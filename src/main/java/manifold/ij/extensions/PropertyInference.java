package manifold.ij.extensions;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.util.PsiUtil;
import manifold.ext.props.rt.api.*;
import manifold.ij.psi.ManLightFieldBuilder;
import manifold.ij.psi.ManLightModifierListImpl;
import manifold.ij.psi.ManPsiElementFactory;
import manifold.rt.api.util.ManStringUtil;
import manifold.rt.api.util.Pair;
import manifold.rt.api.util.ReservedWordMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.*;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.reflect.Modifier.*;
import static java.lang.reflect.Modifier.PRIVATE;

class PropertyInference
{
  static final Key<VarTagInfo> VAR_TAG = Key.create( "VAR_TAG" );

  private final LinkedHashMap<String, PsiMember> _augFeatures;

  static void inferPropertyFields( PsiExtensibleClass psiClass, LinkedHashMap<String, PsiMember> augFeatures )
  {
    PropertyInference pi = new PropertyInference( augFeatures );
    pi.inferPropertyFields( psiClass );
  }

  private PropertyInference( LinkedHashMap<String, PsiMember> augFeatures )
  {
    _augFeatures = augFeatures;
  }

  private void inferPropertyFields( PsiExtensibleClass psiClass )
  {
    Map<String, Set<PropAttrs>> fromGetter = new HashMap<>();
    Map<String, Set<PropAttrs>> fromSetter = new HashMap<>();
    for( PsiMethod psiMethod : psiClass.getOwnMethods() )
    {
      gatherCandidates( psiMethod, fromGetter, fromSetter );
    }

    handleVars( fromGetter, fromSetter );
    handleVals( fromGetter, fromSetter );
    handleWos( fromGetter, fromSetter );
  }

  private void gatherCandidates( PsiMethod m, Map<String, Set<PropAttrs>> fromGetter, Map<String, Set<PropAttrs>> fromSetter )
  {
    PsiAnnotation propgenAnno = m.getAnnotation( propgen.class.getTypeName() );
    if( propgenAnno != null )
    {
      // already a property
      return;
    }

    PropAttrs derivedFromGetter = derivePropertyNameFromGetter( m );
    if( derivedFromGetter != null )
    {
      fromGetter.computeIfAbsent( derivedFromGetter._name, key -> new HashSet<>() )
        .add( derivedFromGetter );
    }
    PropAttrs derivedFromSetter = derivePropertyNameFromSetter( m );
    if( derivedFromSetter != null )
    {
      fromSetter.computeIfAbsent( derivedFromSetter._name, key -> new HashSet<>() )
        .add( derivedFromSetter );
    }
  }

  private boolean isAccessible( PsiField propField, PsiClass origin )
  {
    PsiClass encClass = propField.getContainingClass();
    if( encClass == null )
    {
      return false;
    }
    if( encClass == origin )
    {
      return true;
    }
    PsiModifierList ml = propField.getModifierList();
    if( ml != null && (ml.hasExplicitModifier( PsiModifier.PUBLIC ) ||
      ml.hasExplicitModifier( PsiModifier.PROTECTED )) )
    {
      return true;
    }
    if( ml == null || !ml.hasExplicitModifier( PsiModifier.PRIVATE ) )
    {
      String encPkg = PsiUtil.getPackageName( encClass );
      String originPkg = PsiUtil.getPackageName( origin );
      return encPkg != null && encPkg.equals( originPkg );
    }
    return false;
  }

  private void handleVars( Map<String, Set<PropAttrs>> fromGetter, Map<String, Set<PropAttrs>> fromSetter )
  {
    for( Map.Entry<String, Set<PropAttrs>> entry : fromGetter.entrySet() )
    {
      String name = entry.getKey();
      Set<PropAttrs> getters = entry.getValue();
      Set<PropAttrs> setters = fromSetter.get( name );
      if( getters != null && !getters.isEmpty() && setters != null && !setters.isEmpty() )
      {
        outer:
        for( PropAttrs getAttr : getters )
        {
          PsiType getType = getAttr._type;
          for( PropAttrs setAttr : setters )
          {
            PsiType setType = setAttr._type;
            if( setType.isAssignableFrom( getType ) &&
              getAttr._m.getModifierList().hasModifierProperty( PsiModifier.STATIC ) == setAttr._m.getModifierList().hasModifierProperty( PsiModifier.STATIC ) )
            {
              makeVar( getAttr, setAttr );
              break outer;
            }
          }
        }
      }
    }
  }

  private void handleVals( Map<String, Set<PropAttrs>> fromGetter, Map<String, Set<PropAttrs>> fromSetter )
  {
    for( Map.Entry<String, Set<PropAttrs>> entry : fromGetter.entrySet() )
    {
      String name = entry.getKey();
      Set<PropAttrs> getters = entry.getValue();
      if( getters != null && !getters.isEmpty() )
      {
        Set<PropAttrs> setters = fromSetter.get( name );
        if( setters == null || setters.isEmpty() )
        {
          makeVal( getters.iterator().next() );
        }
      }
    }
  }

  private void handleWos( Map<String, Set<PropAttrs>> fromGetter, Map<String, Set<PropAttrs>> fromSetter )
  {
    for( Map.Entry<String, Set<PropAttrs>> entry : fromSetter.entrySet() )
    {
      String name = entry.getKey();
      Set<PropAttrs> setters = entry.getValue();
      if( setters != null && !setters.isEmpty() )
      {
        Set<PropAttrs> getters = fromGetter.get( name );
        if( getters == null || getters.isEmpty() )
        {
          makeWo( setters.iterator().next() );
        }
      }
    }
  }

  private void makeVar( PropAttrs getAttr, PropAttrs setAttr )
  {
    String fieldName = getAttr._name;
    PsiClass psiClass = getAttr._m.getContainingClass();
    if( !(psiClass instanceof PsiExtensibleClass) )
    {
      return;
    }

    PsiType t = getMoreSpecificType( getAttr._type, setAttr._type );
    int flags = weakest( getAttr.getAccess(), setAttr.getAccess() );
    flags |= (getAttr.isStatic() ? STATIC : 0);

    Pair<Integer, PsiField> res = handleExistingField( fieldName, t, flags, psiClass, var.class );
    if( res == null )
    {
      // existing field found and, if local and compatible, changed access privilege in-place and added @var|val|set
      return;
    }

    // Create and enter the prop field

    flags = res.getFirst() == MAX_VALUE ? flags : weakest( res.getFirst(), flags );

    ManLightFieldBuilder propField = makePropField( fieldName, psiClass, t, flags, getAttr );
    addField( propField, var.class );
  }

  private void makeVal( PropAttrs getAttr )
  {
    String fieldName = getAttr._name;
    PsiClass psiClass = getAttr._m.getContainingClass();
    if( !(psiClass instanceof PsiExtensibleClass) )
    {
      return;
    }

    int flags = getAttr.getAccess();
    flags |= (getAttr.isStatic() ? STATIC : 0);

    Pair<Integer, PsiField> res = handleExistingField( fieldName, getAttr._type, flags, psiClass, val.class );
    if( res == null )
    {
      // existing field found and, if local and compatible, changed access privilege in-place and added @var|val|set
      return;
    }

    // Create and enter the prop field

    flags = res.getFirst() == MAX_VALUE ? getAttr.getAccess() : weakest( res.getFirst(), getAttr.getAccess() );
    flags |= (getAttr.isStatic() ? STATIC : 0);

    ManLightFieldBuilder propField = makePropField( fieldName, psiClass, getAttr._type, flags, getAttr );

    // if super's field is writable, make this one also writable to allow the setter to be used in assignments
    Class<? extends Annotation> varClass =
      res.getSecond() != null && isWritableProperty( res.getSecond() )
        ? var.class
        : val.class;
    addField( propField, varClass );
  }

  private void makeWo( PropAttrs setAttr )
  {
    String fieldName = setAttr._name;
    PsiClass psiClass = setAttr._m.getContainingClass();
    if( !(psiClass instanceof PsiExtensibleClass) )
    {
      return;
    }

    int flags = setAttr.getAccess();
    flags |= (setAttr.isStatic() ? STATIC : 0);

    Pair<Integer, PsiField> res = handleExistingField( fieldName, setAttr._type, flags, psiClass, set.class );
    if( res == null )
    {
      // existing field found and, if local and compatible, changed access privilege in-place and added @var|val|set
      return;
    }

    // Create and enter the prop field

    flags = res.getFirst() == MAX_VALUE ? setAttr.getAccess() : weakest( res.getFirst(), setAttr.getAccess() );
    flags |= (setAttr.isStatic() ? STATIC : 0);

    ManLightFieldBuilder propField = makePropField( fieldName, psiClass, setAttr._type, flags, setAttr );

    // if super's field is writable, make this one also writable to allow the setter to be used in assignments
    Class<? extends Annotation> varClass =
      res.getSecond() != null && isReadableProperty( res.getSecond() )
        ? var.class
        : set.class;
    addField( propField, varClass );
  }

  public boolean isWritableProperty( PsiField exField )
  {
    if( exField == null )
    {
      return false;
    }

    VarTagInfo tag = exField.getCopyableUserData( VAR_TAG );
    if( tag != null )
    {
      return tag.varClass == var.class || tag.varClass == set.class;
    }

    return exField.getAnnotation( var.class.getTypeName() ) != null ||
      exField.getAnnotation( set.class.getTypeName() ) != null;
  }

  public boolean isReadableProperty( PsiField exField )
  {
    if( exField == null )
    {
      return false;
    }

    VarTagInfo tag = exField.getCopyableUserData( VAR_TAG );
    if( tag != null )
    {
      return tag.varClass == var.class || tag.varClass == val.class || tag.varClass == get.class;
    }

    return exField.getAnnotation( var.class.getTypeName() ) != null ||
      exField.getAnnotation( val.class.getTypeName() ) != null ||
      exField.getAnnotation( get.class.getTypeName() ) != null;
  }

  private ManLightFieldBuilder makePropField( String fieldName, PsiClass psiClass, PsiType t, int flags, PropAttrs accessor )
  {
    return ManPsiElementFactory.instance().createLightField( psiClass.getManager(), fieldName, t )
      .withModifierList( new ManLightModifierListImpl( psiClass.getManager(), JavaLanguage.INSTANCE,
        ModifierMap.fromBits( flags ).toArray( new String[0] ) ) )
      .withContainingClass( psiClass )
      .withNavigationElement( accessor._m );
  }

  private void addField( PsiField propField, Class<? extends Annotation> varClass )
  {
    addVarTag( propField, varClass, -1, -1 );
    _augFeatures.put( propField.getName(), propField );
  }

  private void addVarTag( PsiField propField, Class<? extends Annotation> varClass, int weakestAccess, int declaredAccess )
  {
    propField.putCopyableUserData( VAR_TAG, new VarTagInfo( varClass, weakestAccess, declaredAccess ) );
  }

  static class VarTagInfo
  {
    Class<? extends Annotation> varClass;
    int weakestAccess;
    int declaredAccess;

    public VarTagInfo( Class<? extends Annotation> varClass, int weakestAccess, int declaredAccess )
    {
      this.varClass = varClass;
      this.weakestAccess = weakestAccess;
      this.declaredAccess = declaredAccess;
    }
  }

  private PsiType getMoreSpecificType( PsiType t1, PsiType t2 )
  {
    if( t1.equals( t2 ) )
    {
      return t1;
    }
    return t2.isAssignableFrom( t1 ) ? t1 : t2;
  }

  private Pair<Integer, PsiField> handleExistingField( String fieldName, PsiType t, int flags, PsiClass psiClass, Class<? extends Annotation> varClass )
  {
    PsiField[] existing = findExistingFieldInAncestry( fieldName, psiClass, psiClass );
    if( existing != null && existing.length > 0 )
    {
      // a field already exists with this name

      PsiField exField = existing[0];
      if( t.isAssignableFrom( exField.getType() ) &&
        (exField.getModifierList() == null
          ? !Modifier.isStatic( flags )
          : exField.getModifierList().hasExplicitModifier( PsiModifier.STATIC ) == Modifier.isStatic( flags ) &&
          (!exField.getModifierList().hasModifierProperty( PsiModifier.PUBLIC ) || isPropertyField( exField ))) &&
        exField.getContainingClass() != null && !exField.getContainingClass().isInterface() )
      {
        int weakest = weakest( PropertyMaker.getAccess( exField.getModifierList() ), getAccess( flags ) );

        if( exField.getContainingClass() == psiClass )
        {
          // make the existing field accessible according to the weakest of property methods
          int declaredAccess = PropertyMaker.getAccess( exField.getModifierList() );
          addVarTag( exField, varClass, weakest, declaredAccess );
          return null; // don't create another one
        }
        if( isPropertyField( exField ) )
        {
          return new Pair<>( weakest, exField ); // existing field is compatible, create one with `weakest` access (or weaker)
        }
      }
      return null; // existing field is in conflict, don't create another one
    }
    return new Pair<>( MAX_VALUE, null ); // no existing field, create one
  }

  static boolean isPropertyField( PsiField field )
  {
    if( field.getCopyableUserData( VAR_TAG ) != null )
    {
      return true;
    }

    for( Class<?> cls : Arrays.asList( var.class, val.class, get.class, set.class ) )
    {
      PsiAnnotation propAnno = field.getAnnotation( cls.getTypeName() );
      if( propAnno != null )
      {
        return true;
      }
    }
    return false;
  }

  private PsiField[] findExistingFieldInAncestry( String name, PsiClass c, PsiClass origin )
  {
    if( !(c instanceof PsiExtensibleClass) )
    {
      return null;
    }

    List<PsiField> fields = c == origin ? ((PsiExtensibleClass)c).getOwnFields() : Arrays.asList( c.getFields() );
    for( PsiField psiField : fields )
    {
      if( psiField.getName().equals( name ) )
      {
        return isAccessible( psiField, origin ) ? new PsiField[]{psiField} : new PsiField[]{};
      }
    }
    PsiClass st = c.getSuperClass();
    if( st instanceof PsiExtensibleClass )
    {
      PsiField[] psiField = findExistingFieldInAncestry( name, st, origin );
      if( psiField != null )
      {
        return psiField;
      }
    }
    for( PsiClass iface : c.getInterfaces() )
    {
      PsiField[] sym = findExistingFieldInAncestry( name, iface, origin );
      if( sym != null && sym.length > 0 &&
        (sym[0].getModifierList() == null || !sym[0].getModifierList().hasExplicitModifier( PsiModifier.STATIC )) )
      {
        return sym;
      }
    }
    return null;
  }

  private static class PropAttrs
  {
    String _prefix;
    String _name;
    PsiType _type;
    PsiMethod _m;

    PropAttrs( String prefix, String name, PsiType type, PsiMethod m )
    {
      _prefix = prefix;
      _name = name;
      _type = type;
      _m = m;
    }

    private int getAccess()
    {
      return PropertyMaker.getAccess( _m.getModifierList() );
    }

    private boolean isStatic()
    {
      return _m.getModifierList().hasExplicitModifier( PsiModifier.STATIC );
    }
  }

  int getAccess( int flags )
  {
    return flags & (PUBLIC | PROTECTED | PRIVATE);
  }

  int weakest( int acc1, int acc2 )
  {
    return
      acc1 == PUBLIC
        ? PUBLIC
        : acc2 == PUBLIC
        ? PUBLIC
        : acc1 == PROTECTED
        ? PROTECTED
        : acc2 == PROTECTED
        ? PROTECTED
        : acc1 != PRIVATE
        ? 0
        : acc2 != PRIVATE
        ? 0
        : PRIVATE;
  }

  private PropAttrs derivePropertyNameFromGetter( PsiMethod m )
  {
    if( PsiType.VOID.equals( m.getReturnType() ) || m.getParameterList().getParametersCount() > 0 )
    {
      return null;
    }

    PropAttrs derived = deriveName( m, "get", m.getReturnType() );
    return derived == null ? deriveName( m, "is", m.getReturnType() ) : derived;
  }

  private PropAttrs derivePropertyNameFromSetter( PsiMethod m )
  {
    //noinspection ConstantConditions
    return m.getParameterList().getParametersCount() != 1
      ? null
      : deriveName( m, "set", m.getParameterList().getParameter( 0 ).getType() );
  }

  private PropAttrs deriveName( PsiMethod m, String prefix, PsiType type )
  {
    String name = m.getName();
    if( name.startsWith( prefix ) )
    {
      String derived = name.substring( prefix.length() );
      if( !derived.isEmpty() )
      {
        char first = derived.charAt( 0 );
        if( Character.isUpperCase( first ) || first == '$' )
        {
          if( "is".equals( prefix ) && first != '$' )
          {
            // keep "is" in the name to prevent collisions where isBook():true and getBook():Book are both there
            derived = prefix + derived;
          }

          String propName = ManStringUtil.uncapitalize( derived );
          propName = ReservedWordMapping.getIdentifierForName( propName ); // avoid clashing with Java reserved words
          return new PropAttrs( prefix, propName, type, m );
        }
        else if( first == '_' )
        {
          StringBuilder sb = new StringBuilder( derived );
          while( sb.length() > 0 && sb.charAt( 0 ) == '_' )
          {
            sb.deleteCharAt( 0 );
          }
          if( sb.length() > 0 )
          {
            if( "is".equals( prefix ) )
            {
              // keep "is" in the name to prevent collisions where is_book():true and get_book():Book are both there
              sb = new StringBuilder( prefix + ManStringUtil.capitalize( sb.toString() ) );
            }

            String propName = sb.toString();
            propName = ReservedWordMapping.getIdentifierForName( propName ); // avoid clashing with Java reserved words
            return new PropAttrs( prefix, propName, type, m );
          }
        }
      }
    }
    return null;
  }
}
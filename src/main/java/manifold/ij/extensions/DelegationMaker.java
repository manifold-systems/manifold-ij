/*
 *
 *  * Copyright (c) 2022 - Manifold Systems LLC
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package manifold.ij.extensions;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.diagnostic.PluginException;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.jvm.annotation.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiTypesUtil;
import manifold.ext.delegation.DelegationIssueMsg;
import manifold.ext.delegation.rt.api.link;
import manifold.ext.delegation.rt.api.part;
import manifold.ext.rt.api.Structural;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.psi.ManLightMethodBuilder;
import manifold.ij.psi.ManPsiElementFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static manifold.ext.delegation.DelegationIssueMsg.*;
import static manifold.ext.delegation.DelegationIssueMsg.MSG_MODIFIER_REDUNDANT_FOR_LINK;

public class DelegationMaker
{
  private static final ThreadLocal<Set<String>> _reenter = ThreadLocal.withInitial( () -> new HashSet<>() );

  private final AnnotationHolder _holder;
  private final LinkedHashMap<String, PsiMember> _augFeatures;
  private final PsiExtensibleClass _psiClass;
  private final ClassInfo _classInfo;

  static void checkDelegation( PsiExtensibleClass psiClass, AnnotationHolder holder )
  {
    new DelegationMaker( psiClass, holder ).generateOrCheck();
  }
  static void generateMethods( PsiExtensibleClass psiClass, LinkedHashMap<String, PsiMember> augFeatures )
  {
    String qname = psiClass.getQualifiedName();
    if( qname == null )
    {
      return;
    }

    if( _reenter.get().contains( qname ) )
    {
//      throw new IllegalStateException(
//        "Unexpected reentrancy detected. This can cause problems, it is likely due to PsiClass#getMethods() being called " +
//          "somewhere indirectly from DelegationMaker. getOwnMethods() must be called instead of getMethods()." );
      return;
    }
    _reenter.get().add( qname );
    try
    {
      new DelegationMaker( psiClass, augFeatures ).generateOrCheck();
    }
    finally
    {
      _reenter.get().remove( qname );
    }
  }

  private DelegationMaker( PsiExtensibleClass psiClass, AnnotationHolder holder )
  {
    this( psiClass, holder, null );
  }

  private DelegationMaker( PsiExtensibleClass psiClass, LinkedHashMap<String, PsiMember> augFeatures )
  {
    this( psiClass, null, augFeatures );
  }

  private DelegationMaker( PsiExtensibleClass psiClass, AnnotationHolder holder, LinkedHashMap<String, PsiMember> augFeatures )
  {
    _psiClass = psiClass;
    _holder = holder;
    _augFeatures = augFeatures;
    _classInfo = new ClassInfo( psiClass );
  }

  private void generateOrCheck()
  {
    processPartClass();

    if( _classInfo.hasLinks() )
    {
      // find and remove overlapping interfaces to force delegating class to implement them, add warnings
      processInterfaceOverlap( _classInfo );
      // find and remove overlapping methods to force delegating class to implement them, add warnings
      processMethodOverlap( _classInfo );

      for( LinkInfo li : _classInfo.getLinks().values() )
      {
        // build interface method defs
        linkInterfaces( li );
      }

      if( _augFeatures != null )
      {
        for( LinkInfo li : _classInfo.getLinks().values() )
        {
          for( PsiMethod m : li._generatedMethods )
          {
            _augFeatures.put( m.getName(), m );
          }
        }
      }
    }
  }

  private void processPartClass()
  {
    if( !isPartClass( _psiClass ) )
    {
      return;
    }

    checkSuperclass( _psiClass );

    for( PsiField field : _psiClass.getOwnFields() )
    {
      processLinkField( field );
    }
  }

  private void processLinkField( PsiField field )
  {
    @Nullable PsiAnnotation linkAnno = field.getAnnotation( link.class.getTypeName() );
    if( linkAnno == null )
    {
      // not a link field
      return;
    }

    if( isStatic( field ) )
    {
      reportError( field, MSG_LINK_STATIC_FIELD.get() );
      return;
    }

    checkModifiersAndApplyDefaults( field, _psiClass );

    addLinkedInterfaces( linkAnno, _classInfo, field );
  }

  private void addLinkedInterfaces( PsiAnnotation linkAnno, ClassInfo classInfo, PsiField field )
  {
    ArrayList<PsiClassType> interfaces = new ArrayList<>();
    boolean share = getInterfacesFromLinkAnno( linkAnno, interfaces );
    if( interfaces.isEmpty() )
    {
      interfaces.addAll( getCommonInterfaces( classInfo, field.getType() ) );
    }

    if( interfaces.isEmpty() )
    {
      reportError( field, MSG_NO_INTERFACES.get( field.getType().getPresentableText(), classInfo._classDecl.getQualifiedName() ) );
    }

    if( share )
    {
      //todo:
      // shared links must be final
      //field.getModifiers().flags |= FINAL;
    }

    classInfo.getLinks().put( field, new LinkInfo( field, interfaces, share ) );
  }

  private boolean getInterfacesFromLinkAnno( PsiAnnotation linkAnno, ArrayList<PsiClassType> interfaces )
  {
    @NotNull List<JvmAnnotationAttribute> args = linkAnno.getAttributes();
    if( args.isEmpty() )
    {
      return false;
    }

    boolean share = false;
    int i = 0;
    for( JvmAnnotationAttribute entry: args )
    {
      String argSym = entry.getAttributeName();
      @Nullable JvmAnnotationAttributeValue value = entry.getAttributeValue();
      if( argSym.equals( "share" ) )
      {
        //noinspection ConstantConditions
        Boolean val = (Boolean)((JvmAnnotationConstantValue)value).getConstantValue();
        share = val != null && val;
      }
      else if( argSym.equals( "value" ) )
      {
        if( value instanceof JvmAnnotationClassValue )
        {
          processClassType( ((JvmAnnotationClassValue)value).qualifiedName, interfaces, linkAnno.getParameterList().getAttributes()[i] );
        }
        if( value instanceof JvmAnnotationArrayValue )
        {
          for( JvmAnnotationAttributeValue cls : ((JvmAnnotationArrayValue)value).values )
          {
            processClassType( ((JvmAnnotationClassValue)cls).qualifiedName, interfaces, linkAnno.getParameterList().getAttributes()[i] );
          }
        }
      }
      else
      {
        throw new IllegalStateException();
      }
      i++;
    }
    return share;
  }

  private void processClassType( String qname, ArrayList<PsiClassType> interfaces, PsiElement location )
  {
    Project project = location.getProject();
    PsiClass psiClass = JavaPsiFacade.getInstance( project ).findClass( qname, GlobalSearchScope.allScope( project ) );
    if( psiClass != null && psiClass.isInterface() )
    {
      PsiClassType classType = PsiTypesUtil.getClassType( psiClass );
      interfaces.add( classType );
    }
    else
    {
      reportError( location, MSG_ONLY_INTERFACES_HERE.get() );
    }
  }

  private Set<PsiClassType> getCommonInterfaces( ClassInfo ci, @NotNull PsiType fieldType )
  {
    if( !(fieldType instanceof PsiClassType) )
    {
      return Collections.emptySet();
    }

    PsiClass fieldPsiClass = ((PsiClassType)fieldType).resolve();
    if( fieldPsiClass == null )
    {
      return Collections.emptySet();
    }

    ArrayList<PsiClassType> linkFieldInterfaces = new ArrayList<>();
    findAllInterfaces( fieldType, new HashSet<>(), linkFieldInterfaces );

    if( fieldPsiClass.isInterface() && fieldPsiClass.hasAnnotation( Structural.class.getTypeName() ) )
    {
      // A structural interface is assumed to be fully mapped onto the declaring class.
      // Note, structural interfaces work only with forwarding, not with parts
      return new HashSet<>( linkFieldInterfaces );
    }

    return ci.getInterfaces().stream()
      .filter( i1 -> linkFieldInterfaces.stream()
        .anyMatch( i2 -> i1.isAssignableFrom( i2 ) ) )
      .collect( Collectors.toSet() );
  }

  private void linkInterfaces( LinkInfo li )
  {
    for( CandidateInfo m : li.getMethodTypes() )
    {
      generateInterfaceImplMethod( li, m );
    }
  }

  private void generateInterfaceImplMethod( LinkInfo li, CandidateInfo methodGen )
  {
    PsiMethod refMethod = GenerateMembersUtil.substituteGenericMethod( (PsiMethod)methodGen.getElement(), methodGen.getSubstitutor(), _psiClass );

//    PsiMethod refMethod = (PsiMethod)methodGen.getElement();

    ManPsiElementFactory manPsiElemFactory = ManPsiElementFactory.instance();
    String methodName = refMethod.getName();
    ManModule manModule = ManProject.getModule( li.getLinkField() );
    ManLightMethodBuilder method = manPsiElemFactory.createLightMethod( manModule, li.getLinkField().getManager(), methodName )
      .withMethodReturnType( refMethod.getReturnType() )
      .withContainingClass( li.getLinkField().getContainingClass() );

    copyModifiers( refMethod, method );

    for( PsiTypeParameter tv : refMethod.getTypeParameters() )
    {
      method.withTypeParameterDirect( tv );
    }

    PsiParameter[] parameters = refMethod.getParameterList().getParameters();
    for( PsiParameter psiParameter : parameters )
    {
      method.withParameter( psiParameter.getName(), psiParameter.getType() );
    }

    for( PsiClassType psiClassType : refMethod.getThrowsList().getReferencedTypes() )
    {
      method.withException( psiClassType );
    }

    li.addGeneratedMethod( method );
  }

  private void copyModifiers( PsiMethod refMethod, ManLightMethodBuilder method )
  {
    addModifier( refMethod, method, PsiModifier.PUBLIC );
    addModifier( refMethod, method, PsiModifier.STATIC );
    addModifier( refMethod, method, PsiModifier.PACKAGE_LOCAL );
    addModifier( refMethod, method, PsiModifier.PROTECTED );
  }

  private void addModifier( PsiMethod psiMethod, ManLightMethodBuilder method, String modifier )
  {
    if( psiMethod.hasModifierProperty( modifier ) )
    {
      method.withModifier( modifier );
    }
  }

  private void findAllInterfaces( @NotNull PsiType t, Set<PsiType> seen, ArrayList<PsiClassType> result )
  {
    if( !(t instanceof PsiClassType) )
    {
      return;
    }

    PsiClassType type = (PsiClassType)t;

    if( seen.stream().anyMatch( e -> e.equals( type ) ) )
    {
      return;
    }
    seen.add( type );

    PsiClass psiClass = type.resolve();
    if( psiClass == null )
    {
      return;
    }

    if( psiClass.isInterface() )
    {
      if( result.stream()
        .noneMatch( e -> e.equals( type ) ) )
      {
        result.add( type );
      }
    }
    else
    {
      PsiClassType superClass = getSuperClassType( type );
      if( superClass != null )
      {
        findAllInterfaces( superClass, seen, result );
      }
    }

    List<PsiClassType> superInterfaces = getInterfaces( type );
    if( superInterfaces != null )
    {
      superInterfaces.forEach( superInterface -> findAllInterfaces( superInterface, seen, result ) );
    }
  }

  private void processInterfaceOverlap( ClassInfo ci )
  {
    Map<PsiClassType, Set<LinkInfo>> interfaceToLinks = new HashMap<>();

    for( Map.Entry<PsiField, LinkInfo> entry: ci.getLinks().entrySet() )
    {
      LinkInfo li = entry.getValue();
      for( PsiClassType iface : ci.getInterfaces() )
      {
        if( li.getInterfaces().stream().anyMatch( e -> e.equals( iface ) ) )
        {
          Set<LinkInfo> lis = interfaceToLinks.computeIfAbsent( iface, k -> new HashSet<>() );
          lis.add( li );
        }
      }
    }

    for( Map.Entry<PsiClassType, Set<LinkInfo>> entry: interfaceToLinks.entrySet() )
    {
      PsiClassType iface = entry.getKey();
      Set<LinkInfo> lis = entry.getValue();
      if( lis.size() > 1 )
      {
        boolean isInterfaceShared = checkSharedLinks( iface, lis );

        StringBuilder fieldNames = new StringBuilder();
        lis.forEach( li -> fieldNames.append( fieldNames.length() > 0 ? ", " : "" ).append( li._linkField.name ) );
        for( LinkInfo li: lis )
        {
          if( !li.isShare() )
          {
            if( !isInterfaceShared )
            {
              reportWarning( li.getLinkField(),
                DelegationIssueMsg.MSG_INTERFACE_OVERLAP.get( iface.getClassName(), fieldNames ) );
            }

            // remove the overlap interface from the link, only the sharing link provides it
            li.getInterfaces().remove( iface );
          }
        }
      }
    }
  }

  private boolean checkSharedLinks( PsiClassType iface, Set<LinkInfo> lis )
  {
    ArrayList<LinkInfo> sharedLinks = lis.stream()
      .filter( li -> li.isShare() )
      .collect( Collectors.toCollection( () -> new ArrayList<>() ) );
    if( sharedLinks.size() > 1 )
    {
      StringBuilder fieldNames = new StringBuilder();
      sharedLinks.forEach( li -> fieldNames.append( fieldNames.length() > 0 ? ", " : "" ).append( li._linkField.name ) );

      sharedLinks.forEach( li -> reportError( li.getLinkField(),
        DelegationIssueMsg.MSG_MULTIPLE_SHARING.get( iface.getClassName(), fieldNames ) ) );
    }
    return !sharedLinks.isEmpty();
  }

  private void processMethodOverlap( ClassInfo classInfo )
  {
    Map<MethodSignature, CandidateInfo> map = DelegationUtil.getMapToOverrideImplement( _psiClass, true, true );
   // Collection<MethodSignature> methodSignaturesToImplement = OverrideImplementExploreUtil.getMethodSignaturesToImplement( _psiClass );
    for( Map.Entry<PsiField, LinkInfo> entry : classInfo.getLinks().entrySet() )
    {
      LinkInfo li = entry.getValue();
      for( PsiClassType iface : li.getInterfaces() )
      {
        for( Map.Entry<MethodSignature, CandidateInfo> sig_candi: map.entrySet() )
        {
          MethodSignature sig = sig_candi.getKey();
          CandidateInfo candi = sig_candi.getValue();
          HierarchicalMethodSignature hsig = (HierarchicalMethodSignature)sig;
          if( PsiTypesUtil.getClassType( hsig.getMethod().getContainingClass() ).equals( iface ) )
          {
            li.addMethodType( candi );
          }
//          if( sig.)
//          MethodSignature sig = e.getKey();
//          CandidateInfo candi = e.getValue();
//          PsiMethod method = (PsiMethod)candi.getElement();
//          method
        }
//        psiIface.getOwnMethods().stream()
//          .filter( m -> !isStatic( m ) )
//            .forEach( m ->  );
      }
    }

    // Map method types to links, so we can find overlapping methods
    Map<PsiMethod, Set<LinkInfo>> mtToDi = new HashMap<>();
    for( Map.Entry<PsiField, LinkInfo> entry : classInfo.getLinks().entrySet() )
    {
      LinkInfo li = entry.getValue();
      li.getMethodTypes()
          .forEach( m -> {
            boolean found = false;
            for( Map.Entry<PsiMethod, Set<LinkInfo>> e : mtToDi.entrySet() )
            {
              PsiMethod mm = e.getKey();
              Set<LinkInfo> links = e.getValue();
              if( PsiClassImplUtil.isMethodEquivalentTo( (PsiMethod)m.getElement(), mm ) )
              {
                links.add( li );
                found = true;
                break;
              }
            }
            if( !found )
            {
              Set<LinkInfo> set = new HashSet<>();
              set.add( li );
              mtToDi.put( (PsiMethod)m.getElement(), set );
            }
          } );
    }

    for( Map.Entry<PsiMethod, Set<LinkInfo>> entry: mtToDi.entrySet() )
    {
      PsiMethod m = entry.getKey();
      Set<LinkInfo> lis = entry.getValue();
      if( lis.size() > 1 )
      {
        StringBuilder fieldNames = new StringBuilder();
        lis.forEach( li -> fieldNames.append( fieldNames.length() > 0 ? ", " : "" ).append( li._linkField.name ) );
        for( LinkInfo li : lis )
        {
          reportWarning( li.getLinkField(),
            DelegationIssueMsg.MSG_METHOD_OVERLAP.get( m.getName(), fieldNames ) );

          // remove the overlap method type from the link, the delegating class must implement it directly
          li.getMethodTypes().removeIf( im -> PsiClassImplUtil.isMethodEquivalentTo( (PsiMethod)im.getElement(), m ) );
//          for( Iterator<PsiGenerationInfo<PsiMethod>> iter = li.getMethodTypes().iterator(); iter.hasNext(); )
//          {
//            PsiGenerationInfo<PsiMethod> im = iter.next();
//            if( PsiClassImplUtil.isMethodEquivalentTo( im.getPsiMember(), m ) )
//            {
//              iter.remove();
//            }
//          }
        }
      }
    }
  }

//  private void processMethods( PsiExtensibleClass classDecl, LinkInfo li, PsiMethod m )
//  {
//    LinkInfo linkInfo = _classInfo.getLinks().get( li._linkField );
//
//    linkInfo.addMethodType( m );
//  }

  private static PsiClassType getSuperClassType( PsiClassType type )
  {
    PsiClass psiClass = type.resolve();
    if( psiClass == null || psiClass.isInterface() ||
      psiClass.getQualifiedName() == null || psiClass.getQualifiedName().equals( Object.class.getTypeName() ) )
    {
      return null;
    }

    PsiType[] superTypes = type.getSuperTypes();
    if( superTypes.isEmpty() )
    {
      return null;
    }
    return (PsiClassType)superTypes[0];
  }

  private static List<PsiClassType> getInterfaces( PsiClassType type )
  {
    PsiClass psiClass = type.resolve();
    if( psiClass == null )
    {
      return null;
    }

    PsiType[] superTypes = type.getSuperTypes();
    if( superTypes.isEmpty() )
    {
      return null;
    }
    ArrayList<PsiClassType> result = Arrays.stream( superTypes )
      .filter( t -> t instanceof PsiClassType )
      .map( t -> (PsiClassType)t )
      .collect( Collectors.toCollection( () -> new ArrayList<>() ) );
    PsiClass first = result.get(0).resolve();
    if( first == null || !first.isInterface() )
    {
      // remove super class
      result.remove( 0 );
    }
    return result;
  }

  private void checkModifiersAndApplyDefaults( PsiField varDecl, PsiExtensibleClass classDecl )
  {
    if( !isPartClass( classDecl ) )
    {
      // modifier restrictions and defaults apply only to links declared in part classes
      return;
    }

    PsiModifierList modifiers = varDecl.getModifierList();
    if( modifiers.hasModifierProperty( PsiModifier.PUBLIC ) || modifiers.hasModifierProperty( PsiModifier.PROTECTED ) )
    {
      reportError( varDecl.getModifierList(), MSG_MODIFIER_NOT_ALLOWED_HERE.get(
        modifiers.hasModifierProperty( PsiModifier.PUBLIC ) ? PsiModifier.PUBLIC : PsiModifier.PROTECTED) );
    }

    if( modifiers.hasModifierProperty( PsiModifier.PRIVATE ) )
    {
      reportWarning( varDecl.getModifierList(), MSG_MODIFIER_REDUNDANT_FOR_LINK.get( PsiModifier.PRIVATE ) );
    }
    else
    {
      //todo:
      // default @link fields to PRIVATE
      //varDecl.getModifiers().flags |= PRIVATE;
    }

    if( modifiers.hasModifierProperty( PsiModifier.FINAL ) )
    {
      reportWarning( varDecl.getModifierList(), MSG_MODIFIER_REDUNDANT_FOR_LINK.get( PsiModifier.FINAL ) );
    }
    else
    {
      //todo:
      // default @link fields to FINAL
      //varDecl.getModifiers().flags |= FINAL;
    }
  }

  private boolean isStatic( PsiModifierListOwner elem )
  {
    return elem.getModifierList() != null && elem.getModifierList().hasModifierProperty( PsiModifier.STATIC );
  }

  private void checkSuperclass( PsiExtensibleClass psiClass )
  {
    if( !shouldCheck() )
    {
      return;
    }

    @Nullable PsiClass superclass = psiClass.getSuperClass();
    if( superclass != null && !superclass.hasAnnotation( part.class.getTypeName() ) )
    {
      String qname = superclass.getQualifiedName();
      if( qname == null || !qname.equals( Object.class.getTypeName() ) )
      {
        reportError( psiClass.getExtendsList(), MSG_SUPERCLASS_NOT_PART.get() );
      }
    }
  }

  private static boolean isPartClass( PsiExtensibleClass psiClass )
  {
    PsiAnnotation partAnno = psiClass.getAnnotation( part.class.getTypeName() );
    return partAnno != null;
  }

  private boolean shouldCheck()
  {
    return _holder != null;
  }

  private void reportError( PsiElement elem, String msg )
  {
    reportIssue( elem, HighlightSeverity.ERROR, msg );
  }

  private void reportWarning( PsiElement elem, String msg )
  {
    reportIssue( elem, HighlightSeverity.WARNING, msg );
  }

  private void reportIssue( PsiElement elem, HighlightSeverity severity, String msg )
  {
    if( !shouldCheck() )
    {
      return;
    }

    TextRange range = new TextRange( elem.getTextRange().getStartOffset(),
      elem.getTextRange().getEndOffset() );
    AnnotationBuilder builder;
    try
    {
      builder = _holder.newAnnotation( severity, msg )
        .range( range );
    }
    catch( PluginException e )
    {
      // out of range of element being annotated, oh well
      return;
    }
    builder.create();
  }

  private class ClassInfo
  {
    private final PsiExtensibleClass _classDecl;
    private ArrayList<PsiClassType> _interfaces;
    private final Map<PsiField, LinkInfo> _linkInfos;

    ClassInfo( PsiExtensibleClass classDecl )
    {
      _classDecl = classDecl;
      _linkInfos = new HashMap<>();
    }

    public ArrayList<PsiClassType> getInterfaces()
    {
      if( _interfaces == null )
      {
        ArrayList<PsiClassType> result = new ArrayList<>();
        findAllInterfaces( PsiTypesUtil.getClassType( _classDecl ), new HashSet<>(), result );
        _interfaces = result;
      }
      return _interfaces;
    }

    boolean hasLinks()
    {
      return !_linkInfos.isEmpty();
    }

    Map<PsiField, LinkInfo> getLinks()
    {
      return _linkInfos;
    }
  }

  private class LinkInfo
  {
    private final PsiField _linkField;

    private final ArrayList<PsiMethod> _generatedMethods;
    private final Set<CandidateInfo> _methodTypes;
    private ArrayList<PsiClassType> _interfaces;
    private boolean _share;

    LinkInfo( PsiField linkField, ArrayList<PsiClassType> linkedInterfaces, boolean share )
    {
      _linkField = linkField;
      _generatedMethods = new ArrayList<>();
      _methodTypes = new HashSet<>();
      _interfaces = new ArrayList<>( linkedInterfaces );
      _share = share;
    }

    public PsiField getLinkField()
    {
      return _linkField;
    }

    public boolean isShare()
    {
      return _share;
    }

    public Collection<PsiMethod> getGeneratedMethods()
    {
      return _generatedMethods;
    }

    void addGeneratedMethod( PsiMethod methodDecl )
    {
      _generatedMethods.add( methodDecl );
    }

    public ArrayList<PsiClassType> getInterfaces()
    {
      return _interfaces;
    }

    public Set<CandidateInfo> getMethodTypes()
    {
      return _methodTypes;
    }
    void addMethodType( CandidateInfo m )
    {
//      Set<PsiMethodMember> methodMember = Collections.singleton( new PsiMethodMember( m ) );
//      List<PsiGenerationInfo<PsiMethod>> prototypes = OverrideImplementUtil.overrideOrImplementMethods( _psiClass, methodMember, false, false );
//
//      if( prototypes.isEmpty )
//      {
//        return;
//      }
//
//      PsiGenerationInfo<PsiMethod> prototypeMethod = prototypes.get( 0 );
//      if( hasMethodType( prototypeMethod ) )
//      {
//        throw new IllegalStateException();
//      }
//
//      _methodTypes.add( prototypeMethod );
      _methodTypes.add( m );
    }
//    boolean hasMethodType( PsiGenerationInfo<PsiMethod> method )
//    {
//      return _methodTypes.stream().anyMatch( m -> PsiClassImplUtil.isMethodEquivalentTo( m.getPsiMember(), method.getPsiMember() ) );
//    }
  }

}

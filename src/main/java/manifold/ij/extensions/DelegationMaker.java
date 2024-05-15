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
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.jvm.annotation.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
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

public class DelegationMaker
{
  private static final ThreadLocal<Set<String>> _reenter = ThreadLocal.withInitial( () -> new HashSet<>() );

  private final DelegationExternalAnnotator.Info _issueInfo;
  private final LinkedHashSet<PsiMember> _augFeatures;
  private final PsiExtensibleClass _psiClass;
  private final ClassInfo _classInfo;

  static void checkDelegation( PsiExtensibleClass psiClass, DelegationExternalAnnotator.Info issueInfo )
  {
    new DelegationMaker( psiClass, issueInfo ).generateOrCheck();
  }

  static void generateMethods( PsiExtensibleClass psiClass, LinkedHashSet<PsiMember> augFeatures )
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

  private DelegationMaker( PsiExtensibleClass psiClass, DelegationExternalAnnotator.Info issueInfo )
  {
    this( psiClass, issueInfo, null );
  }

  private DelegationMaker( PsiExtensibleClass psiClass, LinkedHashSet<PsiMember> augFeatures )
  {
    this( psiClass, null, augFeatures );
  }

  private DelegationMaker( PsiExtensibleClass psiClass, DelegationExternalAnnotator.Info issueInfo, LinkedHashSet<PsiMember> augFeatures )
  {
    _psiClass = psiClass;
    _issueInfo = issueInfo;
    _augFeatures = augFeatures;
    _classInfo = new ClassInfo( psiClass );
  }

  private void generateOrCheck()
  {
    processLinks();

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
          _augFeatures.addAll( li.getGeneratedMethods() );
        }
      }
    }
  }

  private void processLinks()
  {
    checkSuperclass( _psiClass );

    PsiVariable[] fields = _psiClass.isRecord()
      ? _psiClass.getRecordComponents()
      : _psiClass.getOwnFields().toArray( new PsiVariable[0] );
    for( PsiVariable field : fields )
    {
      processLinkField( field );
    }
  }

  private void processLinkField( PsiVariable field )
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

  private void addLinkedInterfaces( PsiAnnotation linkAnno, ClassInfo classInfo, PsiVariable field )
  {
    ArrayList<PsiClassType> interfaces = new ArrayList<>();
    ArrayList<PsiClassType> shared = new ArrayList<>();
    ArrayList<PsiClassType> fromAnno = new ArrayList<>();
    boolean shareAll = getInterfacesFromLinkAnno( linkAnno, fromAnno, shared );
    if( fromAnno.isEmpty() )
    {
      interfaces.addAll( getCommonInterfaces( classInfo, field.getType() ) );
      if( interfaces.isEmpty() )
      {
        reportError( field, MSG_NO_INTERFACES.get( field.getType().getPresentableText(), classInfo._classDecl.getQualifiedName() ) );
      }
    }
    else
    {
      for( PsiClassType iface : fromAnno )
      {
        Set<PsiClassType> commonInterfaces = getCommonInterfaces( classInfo, iface );
        if( commonInterfaces.isEmpty() )
        {
          reportError( linkAnno, MSG_NO_INTERFACES.get( iface.getPresentableText(), classInfo._classDecl.getQualifiedName() ) );
        }
        interfaces.addAll( commonInterfaces );
      }
      verifyFieldTypeSatisfiesAnnoTypes( field, interfaces );
    }

    if( shareAll || !shared.isEmpty() )
    {
      //todo:
      // shared links must be final
      //field.getModifiers().flags |= FINAL;
    }

    classInfo.getLinks().put( field, new LinkInfo( field, interfaces, shareAll, shared ) );
  }

  private void verifyFieldTypeSatisfiesAnnoTypes( PsiVariable field, ArrayList<PsiClassType> interfaces )
  {
    for( PsiClassType t : interfaces )
    {
      if( !t.isAssignableFrom( field.getType() ) )
      {
        PsiTypeElement typeElement = field.getTypeElement();
        reportError( typeElement == null ? field : typeElement, MSG_FIELD_TYPE_NOT_ASSIGNABLE_TO.get(
          field.getType().getPresentableText(), t.getPresentableText() ) );
      }
    }
  }

  private boolean getInterfacesFromLinkAnno( PsiAnnotation linkAnno, ArrayList<PsiClassType> interfaces, ArrayList<PsiClassType> share )
  {
    @NotNull List<JvmAnnotationAttribute> args = linkAnno.getAttributes();
    if( args.isEmpty() )
    {
      return false;
    }

    boolean shareAll = false;
    for( int i = 0; i < args.size(); i++ )
    {
      JvmAnnotationAttribute entry = args.get( i );

      String argSym = entry.getAttributeName();
      @Nullable JvmAnnotationAttributeValue value = entry.getAttributeValue();
      if( value == null )
      {
        continue;
      }
      if( argSym.equals( "shareAll" ) )
      {
        Boolean val = (Boolean)((JvmAnnotationConstantValue)value).getConstantValue();
        shareAll = val != null && val;
      }
      else if( argSym.equals( "share" ) )
      {
        processClassType( share, value, linkAnno.getParameterList().getAttributes()[i] );
      }
      else if( argSym.equals( "value" ) )
      {
        processClassType( interfaces, value, linkAnno.getParameterList().getAttributes()[i] );
      }
      else
      {
        // todo: add compile error here?
      }
    }
    return shareAll;
  }

  private void processClassType(ArrayList<PsiClassType> interfaces, JvmAnnotationAttributeValue value, PsiElement expr )
  {
    if( value instanceof JvmAnnotationClassValue )
    {
      processClassType( ((JvmAnnotationClassValue)value).qualifiedName, interfaces, expr );
    }
    if( value instanceof JvmAnnotationArrayValue )
    {
      for( JvmAnnotationAttributeValue cls : ((JvmAnnotationArrayValue)value).values )
      {
        if( cls instanceof JvmAnnotationClassValue )
        {
          processClassType( ((JvmAnnotationClassValue)cls).qualifiedName, interfaces, expr );
        }
      }
    }
  }

  private void processClassType( String qname, ArrayList<PsiClassType> interfaces, PsiElement location )
  {
    if( qname == null )
    {
      return;
    }
    
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

  private Set<PsiClassType> getCommonInterfaces( ClassInfo ci, @NotNull PsiType fieldIface )
  {
    if( !(fieldIface instanceof PsiClassType) )
    {
      return Collections.emptySet();
    }

    PsiClass fieldPsiClass = ((PsiClassType)fieldIface).resolve();
    if( fieldPsiClass == null )
    {
      return Collections.emptySet();
    }

    ArrayList<PsiClassType> linkFieldInterfaces = new ArrayList<>();
    findAllInterfaces( fieldIface, new HashSet<>(), linkFieldInterfaces );

    if( fieldPsiClass.isInterface() && fieldPsiClass.hasAnnotation( Structural.class.getTypeName() ) )
    {
      // A structural interface is assumed to be fully mapped onto the declaring class.
      // Note, structural interfaces work only with forwarding, not with parts
      return new HashSet<>( linkFieldInterfaces );
    }

    Set<PsiClassType> set = new HashSet<>();
    for( PsiClassType i1 : ci.getInterfaces() )
    {
      for( PsiClassType i2 : linkFieldInterfaces )
      {
        if( i1.isAssignableFrom( i2 ) )
        {
          set.add( i1 );
          break;
        }
      }
    }
    return set;
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
      .withContainingClass( ((PsiMember)li.getLinkField()).getContainingClass() );

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
      PsiClassType[] superType = psiClass.getExtendsListTypes();
      PsiClassType superClass = superType.length > 0 ? superType[0] : null;
      if( superClass != null )
      {
        findAllInterfaces( superClass, seen, result );
      }
    }

    PsiClassType[] superInterfaces = psiClass.isInterface()
      ? psiClass.getExtendsListTypes()
      : psiClass.getImplementsListTypes();
    for( PsiClassType ifaceType : superInterfaces )
    {
      PsiClass psiIface = ifaceType.resolve();
      if( psiIface != null )
      {
        if( type.hasParameters() )
        {
          PsiClassType.ClassResolveResult classResolveResult = type.resolveGenerics();
          PsiSubstitutor substitutor = classResolveResult.getSubstitutor();
          //substitutor = TypeConversionUtil.getSuperClassSubstitutor( psiIface, psiClass, substitutor );
          ifaceType = (PsiClassType)substitutor.substitute( ifaceType );
        }
        findAllInterfaces( ifaceType, seen, result );
      }
    }
  }

  private void processInterfaceOverlap( ClassInfo ci )
  {
    Map<PsiClassType, Set<LinkInfo>> interfaceToLinks = new HashMap<>();

    for( Map.Entry<PsiVariable, LinkInfo> entry : ci.getLinks().entrySet() )
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

    for( Map.Entry<PsiClassType, Set<LinkInfo>> entry : interfaceToLinks.entrySet() )
    {
      PsiClassType iface = entry.getKey();
      Set<LinkInfo> lis = entry.getValue();
      if( lis.size() > 1 )
      {
        boolean isInterfaceShared = checkSharedLinks( iface, lis );

        StringBuilder fieldNames = new StringBuilder();
        lis.forEach( li -> fieldNames.append( !fieldNames.isEmpty() ? ", " : "" ).append( li._linkField.getName() ) );
        for( LinkInfo li : lis )
        {
          if( !li.shares( iface ) )
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
      .filter( li -> li.shares( iface ) )
      .collect( Collectors.toCollection( () -> new ArrayList<>() ) );
    if( sharedLinks.size() > 1 )
    {
      StringBuilder fieldNames = new StringBuilder();
      sharedLinks.forEach( li -> fieldNames.append( !fieldNames.isEmpty() ? ", " : "" ).append( li._linkField.getName() ) );

      sharedLinks.forEach( li -> reportError( li.getLinkField(),
        DelegationIssueMsg.MSG_MULTIPLE_SHARING.get( iface.getClassName(), fieldNames ) ) );
    }
    return !sharedLinks.isEmpty();
  }

  private void processMethodOverlap( ClassInfo classInfo )
  {
    for( Map.Entry<PsiVariable, LinkInfo> entry : classInfo.getLinks().entrySet() )
    {
      LinkInfo li = entry.getValue();
      for( PsiClassType iface : li.getInterfaces() )
      {
        PsiClassType.ClassResolveResult ifaceResolve = iface.resolveGenerics();
        PsiExtensibleClass psiIface = (PsiExtensibleClass)ifaceResolve.getElement();
        if( psiIface == null )
        {
          continue;
        }
        for( PsiMethod m : psiIface.getOwnMethods() )
        {
          if( !m.getModifierList().hasModifierProperty( PsiModifier.STATIC ) )
          {
            processMethods( classInfo._classDecl, li, m, ifaceResolve.getSubstitutor() );
          }
        }
      }
    }

    // Map method types to links, so we can find overlapping methods
    Map<PsiMethod, Set<LinkInfo>> mtToLi = new HashMap<>();
    for( Map.Entry<PsiVariable, LinkInfo> entry : classInfo.getLinks().entrySet() )
    {
      LinkInfo li = entry.getValue();
      for( CandidateInfo mt : li.getMethodTypes() )
      {
        PsiMethod method = findMethod( mtToLi.keySet(), (PsiMethod)mt.getElement() );
        if( method == null )
        {
          method = (PsiMethod)mt.getElement();
        }
        Set<LinkInfo> linkInfos = mtToLi.computeIfAbsent( method, __ -> new HashSet<>() );
        linkInfos.add( li );
      }
    }

    for( Map.Entry<PsiMethod, Set<LinkInfo>> entry : mtToLi.entrySet() )
    {
      PsiMethod mt = entry.getKey();
      Set<LinkInfo> lis = entry.getValue();
      if( lis.size() > 1 )
      {
        StringBuilder fieldNames = new StringBuilder();
        lis.forEach( li -> fieldNames.append( !fieldNames.isEmpty() ? ", " : "" ).append( li._linkField.getName() ) );
        for( LinkInfo li : lis )
        {
          reportWarning( li.getLinkField(),
            MSG_METHOD_OVERLAP.get( mt.getName(), fieldNames ) );

          // remove the overlap method type from the link, the delegating class must implement it directly
          CandidateInfo candi = li.findMethod( mt );
          li.getMethodTypes().remove( candi );
        }
      }
    }
  }

  private void processMethods( PsiExtensibleClass classDecl, LinkInfo li, PsiMethod m, PsiSubstitutor substitutor )
  {
    if( findMethod( classDecl.getOwnMethods(), m ) != null )
    {
      // class already implements method
      return;
    }

    LinkInfo linkInfo = _classInfo.getLinks().get( li._linkField );
    CandidateInfo candi = new CandidateInfo( m, substitutor );
    if( linkInfo.hasMethod( candi ) )
    {
      // already defined previously in this link
      return;
    }
    linkInfo.addMethodType( candi );
  }

  private PsiMethod findMethod( Iterable<PsiMethod> methods, PsiMethod m )
  {
    for( PsiMethod subMethod : methods )
    {
      if( subMethod.getName().equals( m.getName() ) &&
        MethodSignatureUtil.areOverrideEquivalent( subMethod, m ) )
      {
        return subMethod;
      }
    }
    return null;
  }

  private void checkModifiersAndApplyDefaults( PsiVariable varDecl, PsiExtensibleClass classDecl )
  {
    if( !isPartClass( classDecl ) )
    {
      // modifier restrictions and defaults apply only to links declared in part classes
      return;
    }

    PsiModifierList modifiers = varDecl.getModifierList();
    if( modifiers != null &&
      (modifiers.hasModifierProperty( PsiModifier.PUBLIC ) || modifiers.hasModifierProperty( PsiModifier.PROTECTED )) )
    {
      reportError( varDecl.getModifierList(), MSG_MODIFIER_NOT_ALLOWED_HERE.get(
        modifiers.hasModifierProperty( PsiModifier.PUBLIC ) ? PsiModifier.PUBLIC : PsiModifier.PROTECTED ) );
    }

    if( modifiers != null && modifiers.hasModifierProperty( PsiModifier.PRIVATE ) )
    {
      reportWarning( varDecl.getModifierList(), MSG_MODIFIER_REDUNDANT_FOR_LINK.get( PsiModifier.PRIVATE ) );
    }
    else
    {
      //todo:
      // default @link fields to PRIVATE
      //varDecl.getModifiers().flags |= PRIVATE;
    }

    if( modifiers != null && modifiers.hasModifierProperty( PsiModifier.FINAL ) )
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

    if( !isPartClass( psiClass ) )
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

  public static boolean isPartClass( PsiExtensibleClass psiClass )
  {
    PsiAnnotation partAnno = psiClass.getAnnotation( part.class.getTypeName() );
    return partAnno != null;
  }

  private boolean shouldCheck()
  {
    return _issueInfo != null;
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
    _issueInfo.addIssue( severity, msg, range );
  }

  private class ClassInfo
  {
    private final PsiExtensibleClass _classDecl;
    private ArrayList<PsiClassType> _interfaces;
    private final Map<PsiVariable, LinkInfo> _linkInfos;

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

    Map<PsiVariable, LinkInfo> getLinks()
    {
      return _linkInfos;
    }
  }

  private static class LinkInfo
  {
    private final PsiVariable _linkField;

    private final ArrayList<PsiMethod> _generatedMethods;
    private final Set<CandidateInfo> _methodTypes;
    private final ArrayList<PsiClassType> _interfaces;
    private final ArrayList<PsiClassType> _shared;
    private final boolean _shareAll;

    LinkInfo( PsiVariable linkField, ArrayList<PsiClassType> linkedInterfaces, boolean shareAll, ArrayList<PsiClassType> shared )
    {
      _linkField = linkField;
      _generatedMethods = new ArrayList<>();
      _methodTypes = new HashSet<>();
      _interfaces = new ArrayList<>( linkedInterfaces );
      _shareAll = shareAll;
      _shared = shared;
    }

    public PsiVariable getLinkField()
    {
      return _linkField;
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
      _methodTypes.add( m );
    }

    public boolean hasMethod( CandidateInfo candi )
    {
      return _methodTypes.stream().anyMatch( m -> areOverrideEquivalent( candi, m ) );
    }

    private boolean areOverrideEquivalent( CandidateInfo candi, CandidateInfo m )
    {
      return ((PsiMethod)candi.getElement()).getName().equals( ((PsiMethod)m.getElement()).getName() ) &&
        MethodSignatureUtil.areOverrideEquivalent( (PsiMethod)m.getElement(), (PsiMethod)candi.getElement() );
    }

    public CandidateInfo findMethod( PsiMethod method )
    {
      return _methodTypes.stream()
        .filter( m -> method.getName().equals( ((PsiMethod)m.getElement()).getName() ) &&
          MethodSignatureUtil.areOverrideEquivalent( (PsiMethod)m.getElement(), method ) )
        .findFirst().orElse( null );
    }

    public boolean shares( PsiClassType iface )
    {
      return _shareAll || _shared.stream().anyMatch( t -> t.equals( TypeConversionUtil.erasure( iface ) ) );
    }
  }
}

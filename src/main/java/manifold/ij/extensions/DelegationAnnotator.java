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

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import manifold.ext.parts.PartsIssueMsg;
import manifold.ext.parts.rt.api.internal;
import manifold.ext.parts.rt.api.link;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.codeInsight.AnnotationUtil.findAnnotationInHierarchy;
import static manifold.ext.parts.PartsIssueMsg.*;
import static manifold.ij.extensions.DelegationMaker.isPartClass;

/**
 * Annotator for stuff not covered in DelegationExternalAnnotator. For example, `this` usage in @part classes.
 */
public class DelegationAnnotator implements Annotator
{
  @Override
  public void annotate( @NotNull PsiElement element, @NotNull AnnotationHolder holder )
  {
    if( !ManProject.isManifoldInUse( element ) )
    {
      // Manifold jars are not used in the project
      return;
    }

    if( DumbService.getInstance( element.getProject() ).isDumb() )
    {
      // skip processing during index rebuild
      return;
    }

    ManModule module = ManProject.getModule( element );

    if( module != null && !module.isDelegationEnabled() )
    {
      // project/module not using delegation
      return;
    }

    if( element instanceof PsiMethodCallExpression )
    {
      checkInternalMethodUse( (PsiMethodCallExpression)element, holder );
      checkAmbiguousThisReceiver( (PsiMethodCallExpression)element, holder );
    }

    PsiClass containingClass = ManifoldPsiClassAnnotator.getContainingClass( element );

    if( !(containingClass instanceof PsiExtensibleClass) )
    {
      return;
    }

    PsiExtensibleClass psiClass = (PsiExtensibleClass)containingClass;
    if( !isPartClass( psiClass ) )
    {
      return;
    }

    if( element instanceof PsiThisExpression )
    {
      checkThis( (PsiThisExpression)element, holder );
    }
    else if( element instanceof PsiReferenceExpression )
    {
      checkLinkFieldUse( (PsiReferenceExpression)element, holder );
    }
    else if( element instanceof PsiAssignmentExpression )
    {
      checkLinkFieldAssignment( (PsiAssignmentExpression)element, holder );
    }
    else if( element instanceof PsiField )
    {
      checkLinkFieldAssignment( (PsiField)element, holder );
    }
  }

  private void checkInternalMethodUse( @NotNull PsiMethodCallExpression tree, @NotNull AnnotationHolder holder )
  {
    PsiReferenceExpression methExpr = tree.getMethodExpression();
    PsiExpression qualExpr = methExpr.getQualifierExpression();
    if( qualExpr == null )
    {
      return; // unqualified call ok (implementer)
    }

    PsiElement resolve = methExpr.resolve();
    if( !(resolve instanceof PsiMethod psiMeth) )
    {
      return;
    }

    if( findAnnotationInHierarchy( psiMeth, internal.class ) == null )
    {
      // not an @internal method
      return;
    }

    if( !(qualExpr instanceof PsiReferenceExpression) )
    {
      return;
    }

    String qualText = qualExpr.getText();
    if( qualText.equals( "this" ) ||
        qualText.endsWith( ".this" ) ||
        qualText.equals( "super" ) ||
        qualText.endsWith( ".super" ) )
    {
      // this or super access ok (implementer)
      return;
    }

    PsiElement qual = ((PsiReferenceExpression)qualExpr).resolve();
    if( qual instanceof PsiField )
    {
      boolean isLinkFieldRef = ((PsiField)qual).hasAnnotation( link.class.getTypeName() );
      if( isLinkFieldRef )
      {
        // link field access ok (like super-call in composite)
        return;
      }
    }

    // illegal call to @internal method
    holder.newAnnotation( HighlightSeverity.ERROR, MSG_INTERNAL_ACCESS_NOT_ALLOWED_HERE
        .get( psiMeth.getPresentation() == null ? psiMeth.getName() : psiMeth.getPresentation().getPresentableText(),
              psiMeth.getContainingClass() == null ? "" : psiMeth.getContainingClass().getQualifiedName() ) )
      .range( tree.getTextRange() )
      .create();
  }

  private void checkLinkFieldAssignment( PsiAssignmentExpression element, @NotNull AnnotationHolder holder )
  {
    PsiExpression lhs = element.getLExpression();
    PsiType lhsType = lhs.getType();
    if( !(lhsType instanceof PsiClassType) )
    {
      return;
    }

    PsiField linkField = resolveLinkFieldReference( lhs );
    if( linkField == null )
    {
      // not a link field reference
      return;
    }

    PsiExpression rhs = element.getRExpression();
    if( rhs == null )
    {
      return;
    }

    checkInternalInterface( holder, rhs, lhsType );
  }

  private void checkLinkFieldAssignment( PsiField field, @NotNull AnnotationHolder holder )
  {
    PsiType lhsType = field.getType();
    if( !(lhsType instanceof PsiClassType) )
    {
      return;
    }

    PsiField linkField = field.hasAnnotation( link.class.getTypeName() ) ? field : null;
    if( linkField == null )
    {
      // not a link field reference
      return;
    }

    PsiExpression rhs = linkField.getInitializer();
    if( rhs == null )
    {
      return;
    }

    checkInternalInterface( holder, rhs, lhsType );
  }

  private static void checkInternalInterface( AnnotationHolder holder, PsiExpression rhs, PsiType lhsType )
  {
    PsiType rhsType = rhs.getType();
    if( rhsType instanceof PsiClassType classType )
    {
      PsiClass psiClass = classType.resolve();
      if( psiClass != null )
      {
        PsiClassType @NotNull [] interfaces = psiClass.getImplementsListTypes();
        for( PsiClassType iface : interfaces )
        {
          if( iface.hasAnnotation( internal.class.getTypeName() ) )
          {
            if( ((PsiClassType)lhsType).rawType().equals( iface ) )
            {
              holder.newAnnotation( HighlightSeverity.ERROR, MSG_INTERFACE_IS_INTERNAL_TO_DELEGATE
                  .get( lhsType.getPresentableText(), rhsType.getPresentableText() ) )
                .range( rhs.getTextRange() )
                .create();
              break;
            }
          }
        }
      }
    }
  }

  private void checkThis( PsiThisExpression element, AnnotationHolder holder )
  {
    //noinspection StatementWithEmptyBody
    if( !checkThisArgument( element, holder ) &&
      !checkThisReturn( element, holder ) &&
      !checkThisCast( element, holder ) &&
      !checkThisTernary( element, holder ) &&
      !checkThisAssignment( element, holder ) )
    {
    }
  }

  private boolean checkThisArgument( PsiThisExpression thisExpr, AnnotationHolder holder )
  {
    PsiElement[] actual = {null};
    PsiElement parent = getParentEliminatingParens( thisExpr, actual );
    if( parent instanceof PsiExpressionList )
    {
      parent = parent.getParent();
      if( parent instanceof PsiMethodCallExpression )
      {
        PsiMethodCallExpression m = (PsiMethodCallExpression)parent;
        List<? extends PsiElement> expressions = m.getArgumentList().getExpressions().toList();
        if( expressions.contains( actual[0] ) )
        {
          int index = expressions.indexOf( actual[0] );
          PsiReference reference = ((PsiMethodCallExpression)parent).getMethodExpression();
          PsiMethod sym = (PsiMethod)reference.resolve();
          if( sym == null )
          {
            return true;
          }
          PsiParameter psiParam = sym.getParameterList().getParameters().toList().get( index );
          PsiType paramType = psiParam.getType();
          paramType = TypeConversionUtil.erasure( paramType );
          PsiClass psiParamType = PsiTypesUtil.getPsiClass( paramType );
          if( psiParamType != null && !psiParamType.isInterface() && psiParamType.getQualifiedName() != null )
          {
            addThisNonInterfaceError( thisExpr, holder );
          }
          return true;
        }
      }
    }
    return false;
  }

  private static PsiElement getParentEliminatingParens( PsiThisExpression thisExpr )
  {
    return getParentEliminatingParens( thisExpr, new PsiElement[1] );
  }
  private static PsiElement getParentEliminatingParens( PsiThisExpression thisExpr, PsiElement[] actual )
  {
    PsiElement parent = thisExpr.getParent();
    actual[0] = thisExpr;
    while( parent instanceof PsiParenthesizedExpression )
    {
      actual[0] = parent;
      parent = parent.getParent();
    }
    return parent;
  }

  private boolean checkThisReturn( PsiThisExpression thisExpr, AnnotationHolder holder )
  {
    PsiElement parent = getParentEliminatingParens( thisExpr );
    if( parent instanceof PsiReturnStatement )
    {
      PsiReturnStatement retStmt = (PsiReturnStatement)parent;
      PsiMethod method = RefactoringUtil.getEnclosingMethod( retStmt );
      if( method != null )
      {
        PsiType returnType = method.getReturnType();
        returnType = TypeConversionUtil.erasure( returnType );
        PsiClass psiReturnType = PsiTypesUtil.getPsiClass( returnType );
        if( psiReturnType != null && !psiReturnType.isInterface() && psiReturnType.getQualifiedName() != null )
        {
          addThisNonInterfaceError( thisExpr, holder );
        }
      }
      return true;
    }
    return false;
  }

  private boolean checkThisCast( PsiThisExpression thisExpr, AnnotationHolder holder )
  {
    PsiElement parent = getParentEliminatingParens( thisExpr );
    if( parent instanceof PsiTypeCastExpression )
    {
      PsiTypeCastExpression cast = (PsiTypeCastExpression)parent;
      checkThis( cast.getType(), thisExpr, holder );
      return true;
    }
    return false;
  }

  private boolean checkThisTernary( PsiThisExpression thisExpr, AnnotationHolder holder )
  {
    PsiElement parent = getParentEliminatingParens( thisExpr );
    if( parent instanceof PsiConditionalExpression )
    {
      PsiConditionalExpression ternary = (PsiConditionalExpression)parent;
      checkThis( ternary.getType(), thisExpr, holder );
      return true;
    }
    return false;
  }

  private boolean checkThisAssignment( PsiThisExpression thisExpr, AnnotationHolder holder )
  {
    PsiElement parent = getParentEliminatingParens( thisExpr );
    if( parent instanceof PsiAssignmentExpression )
    {
      PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
      checkThis( assignment.getType(), thisExpr, holder );
      return true;
    }
    else if( parent instanceof PsiVariable )
    {
      PsiVariable varDecl = (PsiVariable)parent;
      checkThis( varDecl.getType(), thisExpr, holder );
      return true;
    }
    return false;
  }

  private static void checkThis( PsiType type, PsiThisExpression thisExpr, AnnotationHolder holder )
  {
    type = TypeConversionUtil.erasure( type );
    PsiClass psiType = PsiTypesUtil.getPsiClass( type );
    if( psiType != null && !psiType.isInterface() )
    {
      addThisNonInterfaceError( thisExpr, holder );
    }
  }

  private static void addThisNonInterfaceError( PsiThisExpression thisExpr, AnnotationHolder holder )
  {
    holder.newAnnotation( HighlightSeverity.ERROR,
        PartsIssueMsg.MSG_PART_THIS_NONINTERFACE_USE.get() )
      .range( thisExpr.getTextRange() )
      .create();
  }

  private void checkLinkFieldUse( PsiReferenceExpression tree, @NotNull AnnotationHolder holder )
  {
    if( !inInstanceMethod( tree ) )
    {
      // probably in a constructor, which is where fields may be assigned
      return;
    }

    PsiField linkField = resolveLinkFieldReference( tree );
    if( linkField == null )
    {
      // not a link field reference
      return;
    }

    PsiMethodCallExpression mcall = PsiTreeUtil.getParentOfType( tree, PsiMethodCallExpression.class );
    if( mcall != null && PsiTreeUtil.isAncestor( mcall.getMethodExpression(), tree, false ) )
    {
      // linkField ref is the receiver of a method call, the only permitted use of a linkField
      return;
    }

    holder.newAnnotation( HighlightSeverity.ERROR,
                          PartsIssueMsg.MSG_PART_LINKFIELD_USE.get( tree.getText() ) )
      .range( tree.getTextRange() )
      .create();
  }

  private PsiField resolveLinkFieldReference( PsiExpression expr )
  {
    if( !(expr instanceof PsiReferenceExpression) )
    {
      return null;
    }

    PsiReferenceExpression reference = (PsiReferenceExpression)expr;

    if( reference.resolve() instanceof PsiField field &&
        field.hasAnnotation( link.class.getTypeName() ) )
    {
      return field;
    }
    return null;
  }

  private boolean inInstanceMethod( PsiElement tree )
  {
    PsiMethod m = PsiTreeUtil.getParentOfType( tree, PsiMethod.class );
    return m != null && !m.isConstructor() && !m.hasModifierProperty( PsiModifier.STATIC );
  }

  static boolean overrides( @NotNull PsiMethod method, @NotNull PsiMethod other, boolean checkResult )
  {
    if( method == other || !method.getName().equals( other.getName() ) )
    {
      return false;
    }
    if( method.isConstructor() || other.isConstructor() )
    {
      return false;
    }
    if( method.hasModifierProperty( PsiModifier.STATIC ) || other.hasModifierProperty( PsiModifier.STATIC ) )
    {
      return false;
    }
    if( other.hasModifierProperty( PsiModifier.PRIVATE ) )
    {
      return false;
    }

    PsiClass methodClass = method.getContainingClass();
    PsiClass otherClass = other.getContainingClass();
    if( methodClass == null || otherClass == null )
    {
      return false;
    }
    if( !methodClass.isInheritor( otherClass, true ) )
    {
      return false;
    }

    // package-private: only overridable from the same package
    if( !other.hasModifierProperty( PsiModifier.PUBLIC ) && !other.hasModifierProperty( PsiModifier.PROTECTED )
        && !JavaPsiFacade.getInstance( method.getProject() ).arePackagesTheSame( methodClass, otherClass ) )
    {
      return false;
    }

    // other as a member of methodClass: superClass = otherClass, derivedClass = methodClass
    PsiSubstitutor otherSub =
      TypeConversionUtil.getSuperClassSubstitutor( otherClass, methodClass, PsiSubstitutor.EMPTY );

    MethodSignature methodSig = method.getSignature( PsiSubstitutor.EMPTY );
    MethodSignature otherSig = other.getSignature( otherSub );
    if( !MethodSignatureUtil.isSubsignature( otherSig, methodSig ) )
    {
      return false;
    }

    if( checkResult )
    {
      PsiType methodRet = method.getReturnType();
      PsiType otherRet = otherSub.substitute( other.getReturnType() );
      if( methodRet == null || otherRet == null )
      {
        return methodRet == otherRet;
      }
      return TypeConversionUtil.isAssignable( otherRet, methodRet )
             || TypeConversionUtil.isAssignable( TypeConversionUtil.erasure( otherRet ), methodRet );
    }
    return true;
  }

  private static boolean isCalledOnThis( @NotNull PsiMethodCallExpression call )
  {
    PsiReferenceExpression methodRef = call.getMethodExpression();

    if( JavaPsiConstructorUtil.isConstructorCall( call ) )
    {
      return false;
    }

    PsiClass innermost = PsiTreeUtil.getParentOfType( call, PsiClass.class );
    if( innermost == null )
    {
      return false;
    }

    PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown( methodRef.getQualifierExpression() );

    if( qualifier != null )
    {
      if( !(qualifier instanceof PsiThisExpression thisExpr) )
      {
        return false;
      }
      PsiJavaCodeReferenceElement thisQualifier = thisExpr.getQualifier();
      if( thisQualifier == null )
      {
        return true;
      }
      PsiElement target = thisQualifier.resolve(); // Outer.this.foo()
      return target == innermost || target instanceof PsiClass;
    }

    JavaResolveResult result = methodRef.advancedResolve( false );
    if( !(result.getElement() instanceof PsiMethod method) )
    {
      return false; // unresolved
    }
    if( method.hasModifierProperty( PsiModifier.STATIC ) )
    {
      return false; // static
    }

    PsiElement resolveScope = result.getCurrentFileResolveScope();
    return resolveScope instanceof PsiClass;
  }

  private static boolean isAnnotatedInternal( @NotNull PsiClassType type )
  {
    for( PsiAnnotation annotation : type.getAnnotations() )
    {
      if( annotation.hasQualifiedName( internal.class.getTypeName() ) )
      {
        return true;
      }
    }
    return false;
  }

  private static boolean isInterfaceExcluded( @NotNull PsiClass psiClass )
  {
    // internal interface $PartClass should never be included as a delegatable interface
    return manifold.ext.parts.rt.internal.$PartClass.class.getTypeName().equals( psiClass.getQualifiedName() );
  }

  private void checkAmbiguousThisReceiver( PsiMethodCallExpression expr, @NotNull AnnotationHolder holder )
  {
    if( !isCalledOnThis( expr ) )
    {
      return;
    }

    PsiMethod psiMethod = expr.resolveMethod();
    if( psiMethod == null )
    {
      return;
    }

    for( PsiClass classDecl = PsiUtil.getContainingClass( expr ); classDecl != null; classDecl = classDecl.getContainingClass() )
    {
      if( classDecl instanceof PsiExtensibleClass extClassDecl && isPartClass( extClassDecl ) )
      {
        List<PsiClass> interfaces = new ArrayList<>();
        findAllInterfaces( classDecl, new HashSet<>(), interfaces );
        interfaces = maximalInterfaces( interfaces );
        Map<PsiClass, List<PsiClass>> matches = new HashMap<>();
        for( PsiClass iface : interfaces )
        {
          for( PsiExtensibleClass t : interfaceClosure( iface ) )
          {
            for( PsiMethod mm : t.getOwnMethods() )
            {
              if( psiMethod.getName().equals( mm.getName() ) )
              {
                if( overrides( psiMethod, mm, false ) )
                {
                  List<PsiClass> types = matches.computeIfAbsent( iface, __ -> new ArrayList<>() );
                  types.add( t );
                  matches.put( iface, maximalInterfaces( types ) );
                }
              }
            }
          }
        }
        if( !matches.isEmpty() )
        {
          if( matches.size() > 1 ||
              matches.values().stream().anyMatch( superInterfaces -> superInterfaces.size() > 1 ) )
          {
            reportAmbiguousReceiverError( psiMethod, matches, expr, holder );
          }
          return;
        }
      }
    }
  }

  private @NotNull List<PsiExtensibleClass> interfaceClosure( @NotNull PsiClass psiClass )
  {
    Set<PsiExtensibleClass> interfaces = new LinkedHashSet<>();
    InheritanceUtil.processSupers( psiClass, true, superClass -> {
      if( superClass instanceof PsiExtensibleClass && superClass.isInterface() )
      {
        interfaces.add( (PsiExtensibleClass)superClass );
      }
      return true; // keep going
    } );
    return new ArrayList<>( interfaces );
  }

  // given {BigInteger, Number, List, Collection, Iterable}, returns {BigInteger, List}
  private @NotNull List<PsiClass> maximalInterfaces( @NotNull List<PsiClass> interfaces )
  {
    // javac: map(erasure) -- not needed, PsiClass has no type arguments
    List<PsiClass> maximal = new ArrayList<>( interfaces );

    for( int i = 0; i < maximal.size(); i++ )
    {
      PsiClass iface = maximal.get( i );
      for( int j = 0; j < maximal.size(); j++ )
      {
        if( i == j )
        {
          continue;
        }

        PsiClass jface = maximal.get( j );
        if( InheritanceUtil.isInheritorOrSelf( iface, jface, true ) )
        {
          if( j < i )
          {
            i--;
          }

          maximal.remove( j );
          j--;
        }
      }
    }
    return maximal;
  }

  private void reportAmbiguousReceiverError( PsiMethod sym, Map<PsiClass, List<PsiClass>> matches, PsiMethodCallExpression expr, @NotNull AnnotationHolder holder )
  {
    // interfaces the caller must choose between. If multiple maximal interfaces reach the method,
    // they are the cast targets, otherwise if the single maximal reaches it through two or more superinterfaces
    LinkedHashSet<PsiClass> candidates = new LinkedHashSet<>();
    if( matches.size() > 1 )
    {
      candidates.addAll( matches.keySet() );
    }
    for( List<PsiClass> declarers : matches.values() )
    {
      if( declarers.size() > 1 )
      {
        candidates.addAll( declarers );
      }
    }

    String ifaceList = candidates.stream()
      .map( t -> t.getName() )
      .collect( Collectors.joining( ", " ) );
    String castTo = candidates.getFirst().getName();
    holder.newAnnotation( HighlightSeverity.ERROR, MSG_AMBIGUOUS_RECEIVER.get(
      PsiFormatUtil.formatMethod( sym,
                                  PsiSubstitutor.EMPTY,
                                  PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                                  PsiFormatUtilBase.SHOW_TYPE ), ifaceList, castTo ) )
      .range( expr.getTextRange() )
      .create();
  }

  private void findAllInterfaces( @NotNull PsiClass psiClass, @NotNull Set<PsiClass> seen, @NotNull List<PsiClass> result )
  {
    findAllInterfaces( psiClass, seen, result, false );
  }

  private void findAllInterfaces( @NotNull PsiClass psiClass, @NotNull Set<PsiClass> seen,
                                  @NotNull List<PsiClass> result, boolean excludeInternal )
  {
    if( !seen.add( psiClass ) )
    {
      return;
    }

    if( psiClass.isInterface() && !isInterfaceExcluded( psiClass ) )
    {
      if( !result.contains( psiClass ) )
      {
        result.add( psiClass );
      }
    }
    else
    {
      // for an interface PsiClass#getSuperClass() returns Object, which is skipped below
      findAllInterfacesInSuperClass( psiClass.getSuperClass(), seen, result, excludeInternal );
    }

    for( PsiClass superInterface : getInterfaces( psiClass, excludeInternal ) )
    {
      findAllInterfaces( superInterface, seen, result, excludeInternal );
    }
  }

  // Replaces the (type, superType, ...) overload
  private void findAllInterfacesInSuperClass( PsiClass superClass, @NotNull Set<PsiClass> seen,
                                              @NotNull List<PsiClass> result, boolean excludeInternal )
  {
    if( superClass == null || CommonClassNames.JAVA_LANG_OBJECT.equals( superClass.getQualifiedName() ) )
    {
      return;
    }
    findAllInterfaces( superClass, seen, result, excludeInternal );
  }

  // Don't include interfaces in a class's implements list annotated with @internal
  private @NotNull List<PsiClass> getInterfaces( @NotNull PsiClass psiClass, boolean excludeInternal )
  {
    if( psiClass.isInterface() || !excludeInternal )
    {
      // an interface can't have internal interfaces (only a class's implements clause may annotate them)
      return List.of( psiClass.getInterfaces() );
    }

    PsiReferenceList implementsList = psiClass.getImplementsList();
    if( implementsList == null )
    {
      return List.of();
    }

    List<PsiClass> interfaces = new ArrayList<>();
    for( PsiClassType type : implementsList.getReferencedTypes() )
    {
      if( isAnnotatedInternal( type ) )
      {
        continue;
      }
      PsiClass resolved = type.resolve();
      if( resolved != null )
      {
        interfaces.add( resolved );
      }
    }
    return interfaces;
  }
}

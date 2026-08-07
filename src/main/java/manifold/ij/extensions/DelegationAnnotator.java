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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import manifold.ext.parts.PartsIssueMsg;
import manifold.ext.parts.rt.api.internal;
import manifold.ext.parts.rt.api.link;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.codeInsight.AnnotationUtil.findAnnotationInHierarchy;
import static manifold.ext.parts.PartsIssueMsg.MSG_INTERFACE_IS_INTERNAL_TO_DELEGATE;
import static manifold.ext.parts.PartsIssueMsg.MSG_INTERNAL_ACCESS_NOT_ALLOWED_HERE;

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
    }

    PsiClass containingClass = ManifoldPsiClassAnnotator.getContainingClass( element );

    if( !(containingClass instanceof PsiExtensibleClass) )
    {
      return;
    }

    PsiExtensibleClass psiClass = (PsiExtensibleClass)containingClass;
    if( !DelegationMaker.isPartClass( psiClass ) )
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
          PsiClass psiParamType = PsiTypesUtil.getPsiClass( paramType );
          if( psiParamType != null && !psiParamType.isInterface() &&
            psiParamType.getQualifiedName() != null &&
            !psiParamType.getQualifiedName().equals( Object.class.getName() ) )
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
        PsiClass psiReturnType = PsiTypesUtil.getPsiClass( returnType );
        if( psiReturnType != null && !psiReturnType.isInterface() &&
          psiReturnType.getQualifiedName() != null &&
          !psiReturnType.getQualifiedName().equals( Object.class.getName() ) )
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
    PsiClass psiType = PsiTypesUtil.getPsiClass( type );
    if( psiType != null && !psiType.isInterface() && !Object.class.getTypeName().equals( psiType.getQualifiedName() ) )
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

  private boolean  inInstanceMethod( PsiElement tree )
  {
    PsiMethod m = PsiTreeUtil.getParentOfType( tree, PsiMethod.class );
    return m != null && !m.isConstructor() && !m.hasModifierProperty( PsiModifier.STATIC );
  }

//  private boolean replaceThisReceiver( PsiThisExpression thisExpr, AnnotationHolder holder )
//  {
//    PsiElement parent = thisExpr.getParent();
//    if( parent instanceof PsiMethodCallExpression )
//    {
//      PsiMethodCallExpression fa = (PsiMethodCallExpression)parent;
//      PsiReference reference = fa.getReference();
//      if( reference == null )
//      {
//        return true;
//      }
//      PsiMethod sym = reference.resolve();
//      Pair<PsiClass, PsiClassType> enclClass_Iface = findInterfaceOfEnclosingTypeThatSymImplements( sym );
//      if( enclClass_Iface == null )
//      {
//        return true;
//      }
//      if( enclClass_Iface.getSecond() != null )
//      {
//        result = replaceThis( tree, enclClass_Iface.fst, enclClass_Iface.snd );
//        return true;
//      }
//    }
//    return false;
//  }
//
//  private Pair<PsiClass, PsiType> findInterfaceOfEnclosingTypeThatSymImplements( PsiMethod sym )
//  {
//    PsiClass classDecl = _classDeclStack.get( i );
//    if( isPartClass( classDecl.sym ) )
//    {
//      ArrayList<PsiClassType> interfaces = new ArrayList<>();
//      findAllInterfaces( classDecl.sym.type, new HashSet<>(), interfaces );
//      for( PsiClassType iface : interfaces )
//      {
//        for( PsiMethod mm : IDynamicJdk.instance().getMembersByName( (PsiClass)iface.tsym, sym.name ) )
//        {
//          if( sym.overrides( mm, iface.tsym, getTypes(), false ) )
//          {
//            return new Pair<>( classDecl, iface );
//          }
//        }
//      }
//    }
//    return null;
//  }

}

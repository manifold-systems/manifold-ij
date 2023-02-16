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
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import manifold.ext.delegation.DelegationIssueMsg;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
    if( psiType != null && !psiType.isInterface() )
    {
      addThisNonInterfaceError( thisExpr, holder );
    }
  }

  private static void addThisNonInterfaceError( PsiThisExpression thisExpr, AnnotationHolder holder )
  {
    holder.newAnnotation( HighlightSeverity.ERROR,
        DelegationIssueMsg.MSG_PART_THIS_NONINTERFACE_USE.get() )
      .range( thisExpr.getTextRange() )
      .create();
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

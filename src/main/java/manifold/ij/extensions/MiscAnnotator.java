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

import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import manifold.ExtIssueMsg;
import manifold.api.util.IssueMsg;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.core.ManPsiTupleExpression;
import manifold.ij.core.ManPsiTupleValueExpression;
import manifold.ij.psi.ManExtensionMethodBuilder;
import manifold.ij.util.ManPsiUtil;
import manifold.internal.javac.ManAttr;
import manifold.rt.api.util.ManClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Annotator for miscellaneous errors & warnings
 */
public class MiscAnnotator implements Annotator
{
  @Override
  public void annotate( @NotNull PsiElement element, @NotNull AnnotationHolder holder )
  {
    if( !ManProject.isManifoldInUse( element ) )
    {
      return;
    }

    highlightAutoAsKeyword( element, holder );
    highlightTupleLabelAsComment( element, holder );
    
    verifyMethodRefNotExtension( element, holder );
    verifyMethodRefNotAuto( element, holder );
    verifyMethodDefNotAbstractAuto( element, holder );
    verifyTuplesEnabled( element, holder );
  }

  private void highlightTupleLabelAsComment( PsiElement element, AnnotationHolder holder )
  {
    if( element instanceof ManPsiTupleValueExpression valueExpr )
    {
      PsiIdentifier label = valueExpr.getLabel();
      if( label != null )
      {
        holder.newAnnotation( HighlightSeverity.TEXT_ATTRIBUTES, "" )
          .range( new TextRange( label.getTextRange().getStartOffset(), valueExpr.getValue().getTextRange().getStartOffset() ) )
          .textAttributes( JavaHighlightingColors.LINE_COMMENT )
          .create();
      }
    }
  }

  private void highlightAutoAsKeyword( PsiElement element, AnnotationHolder holder )
  {
    if( element instanceof PsiJavaCodeReferenceElement )
    {
      String text = element.getText();
      if( !text.equals( "auto" ) && !text.endsWith( ".auto" ) )
      {
        return;
      }
      if( !isExtEnabled( element ) )
      {
        return;
      }

      @Nullable PsiElement psiClass = ((PsiJavaCodeReferenceElement)element).resolve();
      if( psiClass instanceof PsiClass )
      {
        String qname = ((PsiClass)psiClass).getQualifiedName();
        if( qname != null && qname.equals( "manifold.ext.rt.api.auto" ) )
        {
          holder.newAnnotation( HighlightSeverity.TEXT_ATTRIBUTES, "" )
            .range( element.getTextRange() )
            .textAttributes( JavaHighlightingColors.KEYWORD )
            .create();
        }
      }
    }
  }

  private static boolean isExtEnabled( PsiElement element )
  {
    ManModule module = ManProject.getModule( element );
    return module != null && module.isExtEnabled();
  }

  private void verifyTuplesEnabled( PsiElement element, AnnotationHolder holder )
  {
    if( !(element instanceof ManPsiTupleExpression) )
    {
      return;
    }

    ManModule module = ManProject.getModule( element );
    if( module != null && !module.isTuplesEnabled() )
    {
      // the module is not using manifold-tuple

      holder.newAnnotation( HighlightSeverity.ERROR,
          "Add manifold-tuple dependency to enable tuple expressions" )
        .range( element.getTextRange() )
        .create();
    }
  }

  private void verifyMethodDefNotAbstractAuto( PsiElement element, AnnotationHolder holder )
  {
    if( !(element instanceof PsiMethod) )
    {
      return;
    }

    PsiMethod meth = (PsiMethod)element;
    if( isAutoMethod( meth.getReturnTypeElement() ) &&
      meth.getModifierList().hasModifierProperty( PsiModifier.ABSTRACT ) )
    {
      // 'auto' return type inference not allowed on abstract methods

      holder.newAnnotation( HighlightSeverity.ERROR,
          IssueMsg.MSG_AUTO_CANNOT_RETURN_AUTO_FROM_ABSTRACT_METHOD.get() )
        .range( meth.getReturnTypeElement().getTextRange() )
        .create();
    }
  }

  private boolean isAutoMethod( PsiTypeElement typeElement )
  {
    if( typeElement == null )
    {
      return false;
    }
    String fqn = typeElement.getText();
    return fqn.equals( ManClassUtil.getShortClassName( ManAttr.AUTO_TYPE ) ) ||
      fqn.equals( ManAttr.AUTO_TYPE );
  }

  private void verifyMethodRefNotExtension( PsiElement element, AnnotationHolder holder )
  {
    if( element instanceof PsiMethodReferenceExpression )
    {

      PsiElement maybeMethod = ((PsiMethodReferenceExpression)element).resolve();
      if( maybeMethod instanceof PsiMethod )
      {
        PsiMethod psiMethod = (PsiMethod)maybeMethod;
        if( isExtensionMethod( psiMethod ) )
        {
          // Method ref not allowed on an extension method
          holder.newAnnotation( HighlightSeverity.ERROR,
              ExtIssueMsg.MSG_EXTENSION_METHOD_REF_NOT_SUPPORTED.get( psiMethod.getName() ) )
            .range( element.getTextRange() )
            .create();
        }
        else if( isStructuralInterfaceMethod( psiMethod ) )
        {
          // Method ref not allowed on a structural interface method
          holder.newAnnotation( HighlightSeverity.ERROR,
              ExtIssueMsg.MSG_STRUCTURAL_METHOD_REF_NOT_SUPPORTED.get( psiMethod.getName() ) )
            .range( element.getTextRange() )
            .create();
        }
      }
    }
  }

  private void verifyMethodRefNotAuto( PsiElement element, AnnotationHolder holder )
  {
    if( element instanceof PsiMethodReferenceExpression )
    {
      PsiElement maybeMethod = ((PsiMethodReferenceExpression)element).resolve();
      if( maybeMethod instanceof PsiMethod )
      {
        PsiMethod psiMethod = (PsiMethod)maybeMethod;
        if( isAutoMethod( psiMethod.getReturnTypeElement() ) )
        {
          // Method ref not allowed on an extension method
          holder.newAnnotation( HighlightSeverity.ERROR,
              IssueMsg.MSG_ANON_RETURN_METHOD_REF_NOT_SUPPORTED.get( psiMethod.getName() ) )
            .range( element.getTextRange() )
            .create();
        }
      }
    }
  }

  private boolean isStructuralInterfaceMethod( PsiMethod psiMethod )
  {
    return ManPsiUtil.isStructuralInterface( psiMethod.getContainingClass() );
  }

  private boolean isExtensionMethod( PsiMethod method )
  {
    return method instanceof ManExtensionMethodBuilder;
    
//    PsiElement navigationElement = method.getNavigationElement();
//    if( navigationElement instanceof PsiMethod && navigationElement != method )
//    {
//      method = (PsiMethod)navigationElement;
//    }
//
//    PsiModifierList methodMods = method.getModifierList();
//    if( methodMods.findAnnotation( Extension.class.getName() ) != null )
//    {
//      return true;
//    }
//
//    for( PsiParameter param: method.getParameterList().getParameters() )
//    {
//      PsiModifierList modifierList = param.getModifierList();
//      if( modifierList != null &&
//        (modifierList.findAnnotation( This.class.getName() ) != null ||
//          modifierList.findAnnotation( ThisClass.class.getName() ) != null) )
//      {
//        return true;
//      }
//    }
//    return false;
  }
}

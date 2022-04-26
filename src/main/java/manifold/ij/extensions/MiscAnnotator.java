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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import manifold.ExtIssueMsg;
import manifold.ext.rt.api.Extension;
import manifold.ext.rt.api.This;
import manifold.ext.rt.api.ThisClass;
import manifold.ij.core.ManProject;
import manifold.ij.util.ManPsiUtil;
import org.jetbrains.annotations.NotNull;

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

    verifyMethodRefNotExtension( element, holder );
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

  private boolean isStructuralInterfaceMethod( PsiMethod psiMethod )
  {
    return ManPsiUtil.isStructuralInterface( psiMethod.getContainingClass() );
  }

  private boolean isExtensionMethod( PsiMethod method )
  {
    PsiElement navigationElement = method.getNavigationElement();
    if( navigationElement instanceof PsiMethod && navigationElement != method )
    {
      method = (PsiMethod)navigationElement;
    }
    
    PsiModifierList methodMods = method.getModifierList();
    if( methodMods.findAnnotation( Extension.class.getName() ) != null )
    {
      return true;
    }

    for( PsiParameter param: method.getParameterList().getParameters() )
    {
      PsiModifierList modifierList = param.getModifierList();
      if( modifierList != null &&
        (modifierList.findAnnotation( This.class.getName() ) != null ||
          modifierList.findAnnotation( ThisClass.class.getName() ) != null) )
      {
        return true;
      }
    }
    return false;
  }
}

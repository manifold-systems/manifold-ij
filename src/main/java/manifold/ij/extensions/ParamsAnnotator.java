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
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.util.PsiUtil;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.core.ManPsiTupleExpression;
import manifold.ij.core.TupleNamedArgsUtil;
import manifold.ij.util.ManPsiUtil;
import org.jetbrains.annotations.NotNull;

import static manifold.ij.extensions.ManParamsAugmentProvider.hasInitializer;

/**
 *
 */
public class ParamsAnnotator implements Annotator
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

    errorIfOptionalParam( module, element, holder );

    if( module != null && !module.isParamsEnabled() )
    {
      // project/module not using params
      return;
    }

    checkParams( element, holder );
  }

  private void errorIfOptionalParam( ManModule module, @NotNull PsiElement element, @NotNull AnnotationHolder holder )
  {
    if( module != null && !module.isParamsEnabled() && element instanceof PsiParameter )
    {
      PsiParameter param = (PsiParameter)element;
      if( hasInitializer( param ) )
      {
        // manifold-params is not in use from the module at the call-site
        if( param.getNameIdentifier() != null )
        {
          TextRange textRange = param.getNameIdentifier().getTextRange();
          TextRange range = new TextRange( textRange.getStartOffset(), textRange.getEndOffset() );
          holder.newAnnotation( HighlightSeverity.ERROR,
              "<html>Optional parameters are supported with <a href=\"https://github.com/manifold-systems/manifold/blob/master/manifold-deps-parent/manifold-params/README.md\">manifold-params</a></html>" )
            .range( range )
            .create();
        }
      }
    }
  }

  private void checkParams( PsiElement element, AnnotationHolder holder )
  {
    if( element instanceof PsiMethod )
    {
      PsiClass containingClass = ManPsiUtil.getContainingClass( element );
      if( containingClass instanceof PsiExtensibleClass )
      {
        ParamsMaker.checkParamsClass( (PsiMethod)element, (PsiExtensibleClass)containingClass, holder );
        ParamsMaker.checkMethod( (PsiMethod)element, (PsiExtensibleClass)containingClass, holder );
      }
    }
    else if( element instanceof ManPsiTupleExpression )
    {
      PsiElement parent = element.getParent();
      if( parent instanceof PsiExpressionList && ((PsiExpressionList)parent).getExpressionCount() == 1 )
      {
        parent = parent.getParent();
        if( parent instanceof PsiCallExpression ||
            parent instanceof PsiAnonymousClass ||
            parent instanceof PsiEnumConstant )
        {
          // calling only to check/add compile errors
          TupleNamedArgsUtil.getNewParamsClassExprType( parent, (ManPsiTupleExpression)element, holder );
        }
      }
    }
  }
}

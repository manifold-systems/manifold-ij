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
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import org.jetbrains.annotations.NotNull;

/**
 *
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

    checkProperty( element, holder );
  }

  private void checkProperty( PsiElement element, AnnotationHolder holder )
  {
    if( element instanceof PsiExtensibleClass )
    {
      DelegationMaker.checkDelegation( (PsiExtensibleClass)element, holder );
    }
    else if( element instanceof PsiField )
    {
      PsiClass containingClass = ((PsiField)element).getContainingClass();
      if( containingClass instanceof PsiExtensibleClass )
      {
        DelegationMaker.checkDelegation( (PsiExtensibleClass)containingClass, holder );
      }
    }
  }
}

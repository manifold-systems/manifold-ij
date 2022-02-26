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

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.search.GlobalSearchScope;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.psi.ManLightMethodBuilder;
import org.jetbrains.annotations.NotNull;

/**
 *  Filters extension methods where the method is from a module not accessible from the call-site
 */
public class ExtensionMethodCallSiteAnnotator implements Annotator
{
  @Override
  public void annotate( @NotNull PsiElement element, @NotNull AnnotationHolder holder )
  {
    if( !ManProject.isManifoldInUse( element ) )
    {
      // Manifold jars are not used in the project
      return;
    }

    if( element instanceof PsiMethodCallExpression )
    {
      PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)element).getMethodExpression();
      PsiElement member = methodExpression.resolve();
      if( member instanceof ManLightMethodBuilder )
      {
        Module callSiteModule = ModuleUtil.findModuleForPsiElement( element );
        if( callSiteModule != null )
        {
          ManModule manModule = ManProject.getModule( callSiteModule );
          if( manModule != null &&
            (!manModule.isExtEnabled() || ((ManLightMethodBuilder)member).getModules().stream()
              .map( ManModule::getIjModule )
              .noneMatch( extensionModule -> isAccessible( callSiteModule, extensionModule, methodExpression ) )) )
          {
            // The extension method is from a module not accessible from the call-site
            PsiElement methodElem = methodExpression.getReferenceNameElement();
            if( methodElem != null )
            {
              TextRange textRange = methodElem.getTextRange();
              TextRange range = new TextRange( textRange.getStartOffset(), textRange.getEndOffset() );
              holder.newAnnotation( HighlightSeverity.ERROR,
                JavaErrorBundle.message( "cannot.resolve.method", methodExpression.getReferenceName() ) )
                .range( range )
                .create();
            }
          }
        }
      }
    }
  }

  private boolean isAccessible( Module callSiteModule, Module extensionModule, PsiJavaCodeReferenceElement methodExpression )
  {
    // Is the extension method from a module accessible from the call-site?
    GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope( callSiteModule );
    return scope.isSearchInModuleContent( extensionModule ) || methodExpression.getReferenceNameElement() == null;
  }
}

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

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.module.Module;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightRecordField;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Consumer;
import manifold.ext.params.rt.manifold_params;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.psi.ManLightFieldBuilder;
import manifold.ij.psi.ManLightMethodBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Filters out extension methods not accessible from the call-site.
 * Also changes icons for properties.
 */
public class ManJavaCompletionContributor extends CompletionContributor
{
  @Override
  public void fillCompletionVariants( @NotNull CompletionParameters parameters, @NotNull CompletionResultSet result )
  {
    if( !ManProject.isManifoldInUse( parameters.getOriginalFile() ) )
    {
      // Manifold jars are not used in the project
      return;
    }

    // Record fields are treated as public val properties
    addRecordFields( parameters.getPosition(), result );

    result.runRemainingContributors( parameters, new MyConsumer( parameters, result ) );
    result.stopHere();
  }

  private void addRecordFields( @NotNull PsiElement position, CompletionResultSet result )
  {
    PsiElement parent = position.getParent();
    if( parent instanceof PsiReferenceExpression ref )
    {
      PsiElement qualifier = ref.getQualifier();
      PsiType type = qualifier instanceof PsiReferenceExpression refq ? refq.getType() : null;
      if( type instanceof PsiClassReferenceType refType )
      {
        PsiClass psiClass = refType.resolve();
        if( psiClass != null && psiClass.isRecord() )
        {
          for( PsiField c : psiClass.getFields() )
          {
            if( c instanceof LightRecordField )
            {
              result.addElement( LookupElementBuilder.create( c )
                .withIcon( AllIcons.Nodes.PropertyRead ) );
            }
          }
        }
      }
    }
  }

  static class MyConsumer implements Consumer<CompletionResult>
  {
    private final CompletionResultSet _result;
    private final Module _module;

    MyConsumer( CompletionParameters parameters, CompletionResultSet result )
    {
      _result = result;
      _module = findModule( parameters );
    }

    private Module findModule( CompletionParameters parameters )
    {
      PsiElement position = parameters.getPosition();
      Module module = ModuleUtil.findModuleForPsiElement( position.getParent() );
      if( module == null )
      {
        module = ModuleUtil.findModuleForPsiElement( position );
      }
      return module;
    }

    @Override
    public void consume( CompletionResult completionResult )
    {
      LookupElement lookupElement = completionResult.getLookupElement();
      if( !exclude( lookupElement ) )
      {
        _result.passResult( completionResult );
      }
    }

    private boolean exclude( LookupElement lookupElement )
    {
      if( _module == null )
      {
        return false;
      }

      PsiElement psiElem = lookupElement.getPsiElement();
      if( psiElem instanceof ManLightMethodBuilder manMethod )
      {
        ManModule module = ManProject.getModule( _module );
        if( module != null && !module.isExtEnabled() )
        {
          // module not using manifold-ext-rt
          return true;
        }

        if( manMethod.getAnnotation( manifold_params.class.getTypeName() ) != null )
        {
          // don't show generated params method, it navs to the original method
          return true;
        }

        return manMethod.getModules().stream()
          .map( ManModule::getIjModule )
          .noneMatch( methodModule -> GlobalSearchScope.moduleWithDependenciesAndLibrariesScope( _module ).isSearchInModuleContent( methodModule ) );
      }

      //todo: if property is on extension method, filter based on that
      if( psiElem instanceof ManLightFieldBuilder )
      {
        ManModule module = ManProject.getModule( _module );
        if( module != null && !module.isPropertiesEnabled() )
        {
          // module not using manifold-props
          return true;
        }
      }

      return false;
    }
  }
}

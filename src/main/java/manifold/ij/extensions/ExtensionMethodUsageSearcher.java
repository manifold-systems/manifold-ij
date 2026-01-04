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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.search.MethodUsagesSearcher;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import java.util.Set;
import manifold.ext.ExtensionManifold;
import manifold.ext.rt.api.Extension;
import manifold.ext.rt.api.Jailbreak;
import manifold.ext.rt.api.This;
import manifold.ext.rt.api.ThisClass;
import manifold.ij.core.ManProject;
import manifold.ij.psi.ManExtensionMethodBuilder;
import manifold.util.ReflectUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Forward the search to the augmented light method on the extended class
 */
public class ExtensionMethodUsageSearcher extends MethodUsagesSearcher
{
  @Override
  public void processQuery( @NotNull final MethodReferencesSearch.SearchParameters searchParameters,
                            @NotNull final Processor<? super PsiReference> consumer )
  {
    if( !ManProject.isManifoldInUse( searchParameters.getProject() ) )
    {
      // Manifold jars are not used in the project
      return;
    }

    SearchScope searchScope = searchParameters.getScopeDeterminedByUser();
    if( !(searchScope instanceof GlobalSearchScope) )
    {
      return;
    }

    if( searchScope.getClass().getSimpleName().equals( "ModuleWithDependentsScope" ) )
    {
      // include libraries to handle extended classes
      //noinspection unchecked
      Set<Module> modules = (Set)ReflectUtil.field( searchScope, "myModules" ).get();
      if( !modules.isEmpty() )
      {
        searchScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope( modules.iterator().next(),
          ApplicationManager.getApplication().isUnitTestMode() );
      }
    }
    else if( searchScope instanceof ModuleWithDependenciesScope )
    {
      // include libraries to handle extended classes
      @Jailbreak ModuleWithDependenciesScope scope = (ModuleWithDependenciesScope)searchScope;

      searchScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope( scope.getModule(),
        ApplicationManager.getApplication().isUnitTestMode() );
    }
    GlobalSearchScope theSearchScope = (GlobalSearchScope)searchScope;

    PsiMethod method = searchParameters.getMethod();
    PsiClass extensionClass = resolveInReadAction( searchParameters.getProject(), method::getContainingClass );
    if( extensionClass == null )
    {
      return;
    }

    if( !isExtensionClass( searchParameters.getProject(), extensionClass ) )
    {
      return;
    }

    PsiMethod augmentedMethod = resolveInReadAction( searchParameters.getProject(), () ->
    {
      if( method.getModifierList().findAnnotation( Extension.class.getName() ) != null )
      {
        String fqn = getExtendedFqn( extensionClass );
        if( fqn != null )
        {
          PsiClass extendedClass = JavaPsiFacade.getInstance( searchParameters.getProject() )
            .findClass( fqn, getTargetScope( theSearchScope, method ) );
          if( extendedClass != null )
          {
            for( PsiMethod m: extendedClass.findMethodsByName( method.getName(), false ) )
            {
              if( m instanceof ManExtensionMethodBuilder)
              {
                if( PsiUtil.allMethodsHaveSameSignature( new PsiMethod[]{((ManExtensionMethodBuilder)m).getTargetMethod(),
                      (PsiMethod)method.getNavigationElement()} ) )
                {
                  return m;
                }
              }
            }
          }
        }
      }

      for( PsiParameter psiParam : method.getParameterList().getParameters() )
      {
        PsiModifierList modifierList = psiParam.getModifierList();
        if( modifierList != null &&
          (modifierList.findAnnotation( This.class.getName() ) != null ||
            modifierList.findAnnotation( ThisClass.class.getName() ) != null) )
        {
          String fqn = getExtendedFqn( extensionClass );
          PsiClass extendedClass = fqn == null
                                   ? null
                                   : JavaPsiFacade.getInstance( searchParameters.getProject() )
                                     .findClass( fqn, getTargetScope( theSearchScope, method ) );
          if( extendedClass == null )
          {
            continue;
          }
          for( PsiMethod m : extendedClass.findMethodsByName( method.getName(), false ) )
          {
            if( m instanceof ManExtensionMethodBuilder )
            {
              if( PsiUtil.allMethodsHaveSameSignature( new PsiMethod[] {((ManExtensionMethodBuilder)m).getTargetMethod(),
                    (PsiMethod)method.getNavigationElement()} ) )
              {
                return m;
              }
            }
          }
        }
      }
      return null;
    } );
    if( augmentedMethod != null )
    {
      MethodReferencesSearch.SearchParameters searchParams =
        new MethodReferencesSearch.SearchParameters( augmentedMethod, searchScope,
          searchParameters.isStrictSignatureSearch(), searchParameters.getOptimizer() );
      super.processQuery( searchParams, consumer );
    }
  }

  private static boolean isExtensionClass( Project project, PsiClass extensionClass )
  {
    PsiAnnotation extensionAnno = resolveInReadAction( project, () ->
      {
        // only require the toplevel class to have @Extension
        PsiClass topLevelClass = PsiUtil.getTopLevelClass( extensionClass );
        PsiClass psiClass = topLevelClass == null ? extensionClass : topLevelClass;
        PsiModifierList modifierList = psiClass.getModifierList();
        return modifierList == null ? null : modifierList.findAnnotation( Extension.class.getName() );
      } );
    return extensionAnno != null;
  }

  private GlobalSearchScope getTargetScope( GlobalSearchScope searchScope, PsiMethod method )
  {
    if( searchScope instanceof ModuleWithDependenciesScope && searchScope.isSearchInLibraries() )
    {
      return searchScope;
    }

    Module localModule = ModuleUtil.findModuleForPsiElement( method );
    return localModule != null
           ? GlobalSearchScope.moduleWithDependenciesAndLibrariesScope( localModule )
           : GlobalSearchScope.allScope( method.getProject() );
  }

  private String getExtendedFqn( PsiClass extensionClass )
  {
    PsiClass topLevelClass = PsiUtil.getTopLevelClass( extensionClass );
    if( topLevelClass == null )
    {
      return null;
    }

    String topLevelFqn = topLevelClass.getQualifiedName();
    String fqn = topLevelFqn;
    if( fqn != null )
    {
      int iExt = fqn.indexOf( ExtensionManifold.EXTENSIONS_PACKAGE + '.' );
      if( iExt >= 0 )
      {
        fqn = fqn.substring( iExt + ExtensionManifold.EXTENSIONS_PACKAGE.length() + 1 );
        fqn = fqn.substring( 0, fqn.lastIndexOf( '.' ) );

        String extFqn = extensionClass.getQualifiedName(); // could be inner class
        if( extFqn != null && extFqn.length() > topLevelFqn.length() )
        {
          // add inner class
          fqn += extFqn.substring( topLevelFqn.length() );
        }
        return fqn;
      }
    }
    return null;
  }

  private static <T> T resolveInReadAction( Project p, Computable<T> computable )
  {
    return ApplicationManager.getApplication().isReadAccessAllowed()
           ? computable.compute()
           : DumbService.getInstance( p ).runReadActionInSmartMode( computable );
  }
}

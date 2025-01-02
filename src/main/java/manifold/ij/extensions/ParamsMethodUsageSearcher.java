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

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.impl.search.MethodUsagesSearcher;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.refactoring.psi.SearchUtils;
import com.intellij.util.Processor;
import manifold.ext.params.rt.manifold_params;
import manifold.ij.core.ManProject;
import manifold.ij.psi.ManExtensionMethodBuilder;
import manifold.ij.psi.ManLightMethodBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static manifold.ij.extensions.ManParamsAugmentProvider.hasInitializer;

/**
 * Match usages of the generated params method to the corresponding optional params method.
 * Note, we have to implement ImplicitUsageProvider here too to prevent IJ from ignoring file-local usages. Shrug.
 */
public class ParamsMethodUsageSearcher extends MethodUsagesSearcher implements ImplicitUsageProvider
{
  @Override
  public void processQuery( @NotNull final MethodReferencesSearch.SearchParameters searchParameters,
                            @NotNull final Processor<? super PsiReference> consumer )
  {
    if( !shouldProcess( searchParameters.getProject() ) )
    {
      return;
    }

    Set<PsiMethod> allMethods = getAllRelatedMethods( searchParameters.getProject(), searchParameters.getMethod() );

    SearchScope searchScope = searchParameters.getScopeDeterminedByUser();

    for( PsiMethod m : allMethods )
    {
      MethodReferencesSearch.SearchParameters searchParams =
        new MethodReferencesSearch.SearchParameters( m, searchScope, searchParameters.isStrictSignatureSearch(), searchParameters.getOptimizer() );
      super.processQuery( searchParams, consumer );
    }
//
//    ReferencesSearch.searchOptimized( paramsMethod, searchScope, false, searchParameters.getOptimizer(), true, (ref, b) -> consumer.process( ref ) );
  }

  private @NotNull Set<PsiMethod> getAllRelatedMethods( Project p, PsiMethod m )
  {
    Set<PsiMethod> paramsMethods = resolveInReadAction( p, () -> findAdditionalMethodsFor( m ) );
    Set<PsiMethod> allMethods = new LinkedHashSet<>( paramsMethods );
    allMethods.add( m );
    return allMethods;
  }

  private static boolean shouldProcess( Project project )
  {
    if( !ManProject.isManifoldInUse( project ) )
    {
      // Manifold jars are not used in the project
      return false;
    }

    ManProject manProject = ManProject.manProjectFrom( project );
    if( manProject == null || !manProject.isParamsEnabledInAnyModules() )
    {
      // manifold-params not in use
      return false;
    }

    return true;
  }

  @Override
  public boolean isImplicitUsage( @NotNull PsiElement psiElement )
  {
    if( !(psiElement instanceof PsiMethod) )
    {
      return false;
    }

    if( !shouldProcess( psiElement.getProject() ) )
    {
      return false;
    }

    PsiMethod method = (PsiMethod)psiElement;

    if( !(method instanceof ManLightMethodBuilder) &&
      !method.hasAnnotation( manifold_params.class.getTypeName() ) &&
      !ParamsMaker.hasOptionalParams( method ) )
    {
      return false;
    }

    Set<PsiMethod> allRelatedMethods = getAllRelatedMethods( method.getProject(), method );

    for( PsiMethod m : allRelatedMethods )
    {
      for( PsiReference ignored : SearchUtils.findAllReferences( m, GlobalSearchScope.fileScope( method.getContainingFile() ) ) )
      {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isImplicitRead( @NotNull PsiElement psiElement )
  {
    return false;
  }

  @Override
  public boolean isImplicitWrite( @NotNull PsiElement psiElement )
  {
    return false;
  }

//  private PsiMethod findParamsMethodFor( PsiMethod method )
//  {
//    for( PsiParameter param : method.getParameterList().getParameters() )
//    {
//      if( hasInitializer( param ) )
//      {
//        PsiElement navMethod = method.getNavigationElement();
//        if( navMethod != null )
//        {
//          return (PsiMethod)navMethod;
//        }
////        String paramsTypeName = ParamsMaker.getTypeName( method );
////        PsiClass containingClass = method.getContainingClass();
////        if( containingClass != null )
////        {
////          return containingClass.getMethods().stream()
////            .filter( m -> m.getName().equals( method.getName() ) && Arrays.stream( m.getParameterList().getParameters() )
////              .anyMatch( p -> TypeConversionUtil.erasure( p.getType() ).getPresentableText().endsWith( paramsTypeName ) ) )
////            .findFirst().orElse( null );
////        }
//      }
//    }
//    return null;
//  }

  private Set<PsiMethod> findAdditionalMethodsFor( PsiMethod method )
  {
    if( method instanceof ManExtensionMethodBuilder )
    {
      method = ((ManExtensionMethodBuilder)method).getTargetMethod();
    }
    if( method == null )
    {
      return Collections.emptySet();
    }

    for( PsiParameter param : method.getParameterList().getParameters() )
    {
      if( hasInitializer( param ) )
      {
        PsiClass containingClass = method.getContainingClass();
        if( containingClass != null )
        {
          Set<PsiMethod> result = new LinkedHashSet<>();
          for( PsiMethod m : containingClass.getMethods() )
          {
            if( m.getName().equals( method.getName() ) &&
              (m.getNavigationElement() == method ||
                m instanceof ManExtensionMethodBuilder && ((ManExtensionMethodBuilder)m).getTargetMethod() == method) )
            {
              result.add( m );
            }
          }
          return result;
        }
      }
    }
    return Collections.emptySet();
  }

  private static <T> T resolveInReadAction( Project p, Computable<T> computable )
  {
    return ApplicationManager.getApplication().isReadAccessAllowed()
           ? computable.compute()
           : DumbService.getInstance( p ).runReadActionInSmartMode( computable );
  }
}

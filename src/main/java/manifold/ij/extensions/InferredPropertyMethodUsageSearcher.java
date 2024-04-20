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
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.impl.search.MethodUsagesSearcher;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.FilteringProcessor;
import com.intellij.util.Processor;
import manifold.ij.core.ManProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Forward the search to the generated inferred property field corresponding with a getter/setter method
 */
public class InferredPropertyMethodUsageSearcher extends MethodUsagesSearcher
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

    ManProject manProject = ManProject.manProjectFrom( searchParameters.getProject() );
    if( manProject == null || !manProject.isPropertiesEnabledInAnyModules() )
    {
      // manifold-props not in use
      return;
    }

    PsiMethod method = searchParameters.getMethod();
    PsiField propertyField = resolveInReadAction( searchParameters.getProject(), () -> {
      PropertyInference.PropAttrs propAttrs = getPropAttrs( method );
      if( propAttrs == null || propAttrs._name == null || method.getContainingClass() == null )
      {
        return null;
      }
      PsiField propField = method.getContainingClass().findFieldByName( propAttrs._name, false );
      if( propField == null || propField.getCopyableUserData( PropertyInference.VAR_TAG ) == null )
      {
        return null;
      }
      return propField;
    } );

    if( propertyField == null )
    {
      return;
    }

    SearchScope searchScope = searchParameters.getScopeDeterminedByUser();
    ReferencesSearch.searchOptimized( propertyField, searchScope, false, searchParameters.getOptimizer(),
            new FilteringProcessor<>( ref -> proprefMatchesMethod( ref, method ), consumer ) );
  }

  @Nullable
  private static PropertyInference.PropAttrs getPropAttrs(PsiMethod method) {
    PropertyInference.PropAttrs propAttrs = PropertyInference.derivePropertyNameFromGetter(method);
    if( propAttrs == null )
    {
      propAttrs = PropertyInference.derivePropertyNameFromSetter(method);
    }
    return propAttrs;
  }

  private boolean proprefMatchesMethod(PsiReference ref, PsiMethod method )
  {
    PropertyInference.PropAttrs propAttrs = getPropAttrs( method );
    if( propAttrs == null || propAttrs._name == null || method.getContainingClass() == null )
    {
      return false;
    }
    PsiElement parent = ref.getElement().getParent();
    if( propAttrs._prefix.equals( "set" ) )
    {
      // prop references setter only when assigned
      return parent instanceof PsiAssignmentExpression && ((PsiAssignmentExpression)parent).getLExpression() == ref;
    }
    return !(parent instanceof PsiAssignmentExpression) ||
            // prop references both getter and setter if in a compound assign op (+=, -=, etc.)
            ((PsiAssignmentExpression)parent).getLExpression() != ref ||
            ((PsiAssignmentExpression) parent).getOperationTokenType() != JavaTokenType.EQ;
  }

  private static <T> T resolveInReadAction( Project p, Computable<T> computable )
  {
    return ApplicationManager.getApplication().isReadAccessAllowed()
           ? computable.compute()
           : DumbService.getInstance( p ).runReadActionInSmartMode( computable );
  }
}

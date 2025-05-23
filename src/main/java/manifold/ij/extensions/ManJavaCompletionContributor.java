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
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Consumer;
import manifold.ext.params.rt.params;
import manifold.ij.core.*;
import manifold.ij.psi.ManLightClassBuilder;
import manifold.ij.psi.ManLightFieldBuilder;
import manifold.ij.psi.ManLightMethodBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static manifold.ij.core.TupleNamedArgsUtil.getParamNames;

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

    // provide arg name completion for calls to methods having optional params
    addArgumentNames( parameters.getPosition(), result );

    result.runRemainingContributors( parameters, new MyConsumer( parameters, result ) );
    result.stopHere();
  }

  private void addArgumentNames( @NotNull PsiElement position, CompletionResultSet result )
  {
    if( isInTupleValue( position ) )
    {
      // don't include arg name completion when in the value expr part
      return;
    }

    PsiElement callExpr = findCallExpr( position );
    if( callExpr == null )
    {
      return;
    }

    List<PsiElement> candidates =
      callExpr instanceof PsiCallExpression
      ? makeCallExprCadidates( callExpr )
      : callExpr instanceof PsiAnonymousClass
        ? makeAnonymousClassCandidate( (PsiAnonymousClass)callExpr )
        : makeEnumConstantCandidate( (PsiEnumConstant)callExpr );
    Set<String> paramNames = new HashSet<>();
    for( PsiElement elem : candidates )
    {
      if( elem instanceof PsiMethod method )
      {
        PsiAnnotation anno = method.getAnnotation( params.class.getTypeName() );
        if( anno != null )
        {
          PsiParameterList paramList = method.getParameterList();
          if( paramList.getParametersCount() == 1 )
          {
            PsiParameter parameter = paramList.getParameter( 0 );
            PsiType paramType = parameter == null ? null : parameter.getType();
            if( paramType instanceof PsiClassType psiClassType )
            {
              PsiClass psiClass = psiClassType.resolve();
              if( psiClass != null )
              {
                PsiAnnotation annoOnClass = psiClass.getAnnotation( params.class.getTypeName() );
                if( annoOnClass != null )
                {
                  paramNames.addAll( getParamNames( psiClass, true ) );
                }
              }
            }
          }
        }
      }
    }
    for( String paramName : paramNames )
    {
      result.addElement( LookupElementBuilder.create( paramName + ":" )
        .withIcon( AllIcons.Nodes.Parameter ) );
    }
  }

  private static List<PsiElement> makeCallExprCadidates( PsiElement callExpr )
  {
    return Arrays.stream( PsiResolveHelper.getInstance( callExpr.getProject() )
                            .getReferencedMethodCandidates( (PsiCallExpression)callExpr, false ) )
      .map( c -> c.getElement() ).collect( Collectors.toList() );
  }

  private List<PsiElement> makeEnumConstantCandidate( PsiEnumConstant callExpr )
  {
    PsiExpressionList exprList = callExpr.getArgumentList();
    if( exprList != null )
    {
      JavaResolveResult[] results = PsiResolveHelper.getInstance( callExpr.getProject() ).multiResolveConstructor(
        (PsiClassType)callExpr.getType(), exprList, callExpr );
      return Arrays.stream( results ).map( r -> r.getElement() ).collect( Collectors.toList() );
    }
    return null;
  }

  private List<PsiElement> makeAnonymousClassCandidate( PsiAnonymousClass callExpr )
  {
    PsiExpressionList exprList = callExpr.getArgumentList();
    if( exprList != null )
    {
      JavaResolveResult[] results = PsiResolveHelper.getInstance( callExpr.getProject() ).multiResolveConstructor(
        callExpr.getBaseClassType(), exprList, callExpr );
      return Arrays.stream( results ).map( r -> r.getElement() ).collect( Collectors.toList() );
    }
    return null;
  }

  private boolean isInTupleValue( PsiElement position )
  {
    if( position == null )
    {
      return false;
    }
    if( position instanceof ManPsiTupleValueExpression )
    {
      return true;
    }
    PsiElement parent = position.getParent();
    if( isInTupleValue( parent ) )
    {
      if( parent instanceof ManPsiTupleValueExpression )
      {
        ManPsiTupleValueExpression itemExpr = (ManPsiTupleValueExpression)parent;
        return itemExpr.getValue() != null && itemExpr.getValue().getTextRange().contains( position.getTextRange() );
      }
      return true;
    }
    return false;
  }

  private PsiElement findCallExpr( PsiElement position )
  {
    if( position == null )
    {
      return null;
    }
    if( position instanceof PsiCall || position instanceof PsiAnonymousClass )
    {
      return position;
    }
    if( position instanceof PsiClass )
    {
      return null;
    }
    return findCallExpr( position.getParent() );
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

        if( manMethod.getAnnotation( params.class.getTypeName() ) != null )
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

      if( psiElem instanceof ManLightClassBuilder &&
        ((ManLightClassBuilder)psiElem).getName().startsWith( "$" ) )
      {
        // a "params class" inner class, should never see these in completion
        return true;
      }

      return false;
    }
  }
}

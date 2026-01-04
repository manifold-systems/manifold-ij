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

import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.lang.parameterInfo.ParameterInfoUIContext;
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext;
import com.intellij.psi.*;
import manifold.ext.params.rt.params;
import manifold.ij.core.ManPsiTupleExpression;
import manifold.ij.util.ManPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static manifold.ij.extensions.ManParamsAugmentProvider.hasInitializer;

public class ManMethodParameterInfoHandler implements ParameterInfoHandler<PsiExpressionList, PsiExpression>
{
  private final MethodParameterInfoHandler _methodParameterInfoHandler = new MethodParameterInfoHandler();

  @Override
  public @Nullable PsiExpressionList findElementForParameterInfo( @NotNull CreateParameterInfoContext createParameterInfoContext )
  {
    PsiExpressionList exprList = _methodParameterInfoHandler.findElementForParameterInfo( createParameterInfoContext );
    PsiExpression[] args = exprList == null ? new PsiExpression[0] : exprList.getExpressions();
    ManPsiTupleExpression tupleExpr = null;
    if( args.length == 1 && args[0] instanceof ManPsiTupleExpression )
    {
      tupleExpr = (ManPsiTupleExpression)args[0];
    }

    Object[] itemsToShow = createParameterInfoContext.getItemsToShow();
    if( itemsToShow != null )
    {
      List<Object> keep = new ArrayList<>();

      for( Object item : itemsToShow )
      {
        PsiMethod psiMethod = MethodParameterInfoHandler.tryGetMethodFromCandidate( item );
        if( psiMethod != null )
        {
          if( tupleExpr != null )
          {
            // if passing args via tuple expression, filter out methods that don't have optional params
            PsiParameterList paramList = psiMethod.getParameterList();
            if( ManParamsAugmentProvider.hasOptionalParam( paramList ) ||
              Arrays.stream( paramList.getParameters() ).anyMatch( param -> ManPsiUtil.isStructuralInterface( param.getType() ) ) )
            {
              keep.add( item );
            }
          }
          else if( !psiMethod.hasAnnotation( params.class.getTypeName() ) )
          {
            // filter out generated methods marked with @params
            keep.add( item );
          }
        }
        else
        {
          keep.add( item );
        }
      }

      createParameterInfoContext.setItemsToShow( keep.toArray() );
    }
    return exprList;
  }

  @Override
  public void showParameterInfo( @NotNull PsiExpressionList psiExpressionList, @NotNull CreateParameterInfoContext createParameterInfoContext )
  {
    _methodParameterInfoHandler.showParameterInfo( psiExpressionList, createParameterInfoContext );
  }

  @Override
  public @Nullable PsiExpressionList findElementForUpdatingParameterInfo( @NotNull UpdateParameterInfoContext updateParameterInfoContext )
  {
    PsiExpressionList elementForUpdatingParameterInfo = _methodParameterInfoHandler.findElementForUpdatingParameterInfo( updateParameterInfoContext );
    return elementForUpdatingParameterInfo;
  }

  @Override
  public void updateParameterInfo( @NotNull PsiExpressionList psiExpressionList, @NotNull UpdateParameterInfoContext updateParameterInfoContext )
  {
    _methodParameterInfoHandler.updateParameterInfo( psiExpressionList, updateParameterInfoContext );
  }

  @Override
  public void updateUI( PsiExpression psiExpression, @NotNull ParameterInfoUIContext parameterInfoUIContext )
  {
    _methodParameterInfoHandler.updateUI( psiExpression, parameterInfoUIContext );
  }
}

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

package manifold.ij.core;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveCache;
import com.intellij.psi.impl.source.tree.java.PsiAssignmentExpressionImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import manifold.ij.extensions.ManJavaResolveCache;
import org.jetbrains.annotations.NotNull;

// For operator overloading of compound assignment operators +=, *=, etc.
public class ManPsiAssignmentExpressionImpl extends PsiAssignmentExpressionImpl implements IManOperatorOverloadReference
{
  public ManPsiAssignmentExpressionImpl()
  {
    super();
  }

  // Handle compound equality operator overloading +=, *=, etc.
  @Override
  public PsiType getType()
  {
    PsiExpression lExpression = PsiUtil.deparenthesizeExpression( getLExpression() );
    if( lExpression == null || lExpression.getManager() == null || !lExpression.isValid() ||
      !(lExpression instanceof PsiReferenceExpression || lExpression instanceof PsiArrayAccessExpression) )
    {
      return null;
    }
    return JavaResolveCache.getInstance( getProject() ).getType( this, e -> {
      try
      {
        return lExpression.getType();
      }
      catch( PsiInvalidElementAccessException ieae )
      {
        // we make all the proper checks in the if-statement above, how this becomes invalid is a mystery
        return null;
      }
    } );
  }

  @Override
  public boolean isOverloaded()
  {
    if( getRExpression() == null )
    {
      return false;
    }
    PsiMethod method = ManJavaResolveCache.getBinaryOperatorMethod(
      getOperationSign(),
      getLExpression().getType(),
      getRExpression().getType(), this );
    return method != null;
  }

  public PsiReference getReference() {
    return isOverloaded() ? this : super.getReference();
  }

  @NotNull
  @Override
  public PsiElement getElement() {
    return this;
  }

  @NotNull
  @Override
  public TextRange getRangeInElement()
  {
    return TextRange.EMPTY_RANGE;
  }

  @Override
  public PsiElement resolve() {
    if( getRExpression() == null )
    {
      return null;
    }
    PsiMethod method = ManJavaResolveCache.getBinaryOperatorMethod(
      getOperationSign(),
      getLExpression().getType(),
      getRExpression().getType(), this );
    return method;
  }

  @NotNull
  @Override
  public @NlsSafe String getCanonicalText()
  {
    return "";
  }

  @Override
  public PsiElement handleElementRename( @NotNull String newElementName ) throws IncorrectOperationException
  {
    return null;
  }

  @Override
  public PsiElement bindToElement( @NotNull PsiElement element ) throws IncorrectOperationException
  {
    return null;
  }

  @Override
  public boolean isReferenceTo( @NotNull PsiElement element )
  {
    return resolve() == element;
  }

  @Override
  public boolean isSoft() {
    return false;
  }

}

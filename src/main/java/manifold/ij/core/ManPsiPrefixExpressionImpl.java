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
import com.intellij.psi.impl.source.tree.java.PsiPrefixExpressionImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.IncorrectOperationException;
import manifold.ij.extensions.ManJavaResolveCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ManPsiPrefixExpressionImpl extends PsiPrefixExpressionImpl implements IManOperatorOverloadReference
{
  private static final String UNARY_MINUS = "unaryMinus";
  private static final String UNARY_INV = "inv";
  private static final String UNARY_NOT = "not";

  @Override
  public PsiType getType()
  {
    // Handle negation operator overload

    PsiType type = getTypeForUnaryOverload();
    if( type != null )
    {
      return type;
    }

    return super.getType();
  }

  public PsiType getTypeForUnaryOverload()
  {
    IElementType op = getOperationTokenType();
    if( op != JavaTokenType.MINUS && op != JavaTokenType.TILDE && op != JavaTokenType.EXCL )
    {
      return null;
    }

    PsiExpression operand = getOperand();
    if( operand == null )
    {
      return null;
    }

    PsiType operandType = operand.getType();
    if( operandType instanceof PsiPrimitiveType )
    {
      return null;
    }

    return getOverloadedType( methodName( op ), operandType );
  }

  private String methodName( IElementType op )
  {
    if( op == JavaTokenType.MINUS && op != JavaTokenType.TILDE && op != JavaTokenType.EXCL )
    {
      return UNARY_MINUS;
    }
    if( op == JavaTokenType.TILDE )
    {
      return UNARY_INV;
    }
    if( op == JavaTokenType.EXCL )
    {
      return UNARY_NOT;
    }
    return null;
  }

  @Nullable
  private PsiType getOverloadedType( String opName, PsiType operandType )
  {
    PsiClass psiClassOperand = PsiTypesUtil.getPsiClass( operandType );
    if( psiClassOperand == null )
    {
      return null;
    }

    PsiMethod[] members = psiClassOperand.getAllMethods();

    PsiType operationReturnType = getUnaryOperationReturnType( opName, operandType, members );
    if( operationReturnType != null )
    {
      return operationReturnType;
    }

    // also look for default interface methods
    for( PsiType iface: operandType.getSuperTypes() )
    {
      PsiClass psiIface = PsiTypesUtil.getPsiClass( iface );
      if( psiIface != null && psiIface.isInterface() )
      {
        if( iface instanceof PsiClassType )
        {
          operationReturnType = getUnaryOperationReturnType( opName, iface, psiIface.getAllMethods() );
          if( operationReturnType != null )
          {
            return operationReturnType;
          }
        }
      }
    }

    return null;
  }

  @Nullable
  private PsiType getUnaryOperationReturnType( String opName, PsiType operandType, PsiMethod[] members )
  {
    for( PsiMethod m: members )
    {
      if( m.getParameterList().getParametersCount() != 0 )
      {
        continue;
      }

      if( opName.equals( m.getName() ) )
      {
        return ManJavaResolveCache.getMemberSubstitutor( operandType, m ).substitute( m.getReturnType() );
      }
    }
    return null;
  }

  @Override
  public boolean isOverloaded()
  {
    PsiExpression operand = getOperand();
    if( operand == null )
    {
      return false;
    }

    PsiMethod method = ManJavaResolveCache.getBinaryOperatorMethod(
      getOperationSign(),
      operand.getType(),
      null, this );
    return method != null || getTypeForUnaryOverload() != null;
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
    if( isOverloaded() )
    {
      PsiMethod method = ManJavaResolveCache.getBinaryOperatorMethod( getOperationSign(), getOperand().getType(), null, this );
      return method;
    }
    PsiType typeForUnaryMinusOverload = getTypeForUnaryOverload();
    if( typeForUnaryMinusOverload != null )
    {
      // todo
    }
    return null;
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

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

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.java.ExpressionPsiElement;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.JavaTokenType.COLON;
import static com.intellij.psi.JavaTokenType.IDENTIFIER;
import static com.intellij.psi.impl.source.tree.ElementType.EXPRESSION_BIT_SET;

public class ManPsiTupleValueExpressionImpl extends ExpressionPsiElement implements ManPsiTupleValueExpression
{
  private static final Logger LOG = Logger.getInstance( ManPsiTupleValueExpressionImpl.class );

  public ManPsiTupleValueExpressionImpl()
  {
    super( ManElementType.TUPLE_VALUE_EXPRESSION );
  }

  @Override
  public PsiIdentifier getLabel()
  {
    return (PsiIdentifier)findChildByRoleAsPsiElement( ChildRole.LABEL_NAME );
  }

  @Override
  public PsiExpression getValue()
  {
    return (PsiExpression)findChildByRoleAsPsiElement( ChildRole.EXPRESSION );
  }

  @Override
  public @Nullable PsiType getType()
  {
    return getValue() == null ? null : getValue().getType();
  }

  @Override
  public @Nullable PsiElement getNameIdentifier()
  {
    return getLabel();
  }

  @Override
  public PsiElement setName( @NotNull String name ) throws IncorrectOperationException
  {
    PsiElement nameIdentifier = getNameIdentifier();
    if( nameIdentifier != null )
    {
      PsiImplUtil.setName( nameIdentifier, name );
    }
    return this;
  }

  @Override
  public ASTNode findChildByRole( int role )
  {
    LOG.assertTrue( ChildRole.isUnique( role ) );
    switch( role )
    {
      case ChildRole.LABEL_NAME:
        return findChildByType( COLON ) == null ? null : getFirstChildNode();

      case ChildRole.COLON:
        return findChildByType( COLON );

      case ChildRole.EXPRESSION:
        return findChildByType( EXPRESSION_BIT_SET );

      default:
        return null;
    }
  }

  @Override
  public int getChildRole( @NotNull ASTNode child )
  {
    LOG.assertTrue( child.getTreeParent() == this );
    IElementType i = child.getElementType();
    if( i == IDENTIFIER && child.getTreeNext().getElementType() == JavaTokenType.COLON )
    {
      return ChildRole.LABEL_NAME;
    }
    else if( i == COLON )
    {
      return ChildRole.COLON;
    }
    else
    {
      if( EXPRESSION_BIT_SET.contains( child.getElementType() ) )
      {
        return ChildRole.EXPRESSION;
      }
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public void accept( @NotNull PsiElementVisitor visitor )
  {
    if( visitor instanceof JavaElementVisitor )
    {
      ((JavaElementVisitor)visitor).visitExpression( this );
    }
    else
    {
      visitor.visitElement( this );
    }
  }
}

package manifold.ij.core;

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

  @Override
  public PsiType getType()
  {
    // Handle negation operator overload

    PsiType type = getTypeForUnaryMinusOverload();
    if( type != null )
    {
      return type;
    }

    return super.getType();
  }

  public PsiType getTypeForUnaryMinusOverload()
  {
    IElementType op = getOperationTokenType();
    if( op != JavaTokenType.MINUS )
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

    return getUnaryMinusType( UNARY_MINUS, operandType );
  }

  @Nullable
  private PsiType getUnaryMinusType( String opName, PsiType operandType )
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
    PsiMethod method = ManJavaResolveCache.getBinaryOperatorMethod(
      getOperationSign(),
      getOperand().getType(),
      null, this );
    return method != null || getTypeForUnaryMinusOverload() != null;
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
    PsiType typeForUnaryMinusOverload = getTypeForUnaryMinusOverload();
    if( typeForUnaryMinusOverload != null )
    {
      // todo
    }
    return null;
  }

  @NotNull
  @Override
  public String getCanonicalText()
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

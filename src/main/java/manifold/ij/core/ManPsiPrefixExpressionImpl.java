package manifold.ij.core;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.tree.java.PsiPrefixExpressionImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTypesUtil;
import manifold.ij.extensions.ManJavaResolveCache;
import org.jetbrains.annotations.Nullable;

public class ManPsiPrefixExpressionImpl extends PsiPrefixExpressionImpl
{
  private static final String UNARY_MINUS = "unaryMinus";

  @Override
  public PsiType getType()
  {
    // Handle negation operator overload

    PsiType type = getTypeForOverloadedOperator();
    if( type != null )
    {
      return type;
    }

    return super.getType();
  }

  public PsiType getTypeForOverloadedOperator()
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

    return getType( UNARY_MINUS, operandType );
  }

  @Nullable
  private PsiType getType( String opName, PsiType operandType )
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
}

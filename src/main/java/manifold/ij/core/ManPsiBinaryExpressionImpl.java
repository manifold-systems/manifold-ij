package manifold.ij.core;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.java.PsiBinaryExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiJavaTokenImpl;
import com.intellij.psi.tree.IElementType;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

// Supports binding expressions
public class ManPsiBinaryExpressionImpl extends PsiBinaryExpressionImpl
{
  private static Map<IElementType, String> BINARY_OP_TO_NAME = new HashMap<IElementType, String>()
  {{
    put( JavaTokenType.PLUS, "add" );
    put( JavaTokenType.MINUS, "subtract" );
    put( JavaTokenType.ASTERISK, "multiply" );
    put( JavaTokenType.DIV, "divide" );
    put( JavaTokenType.PERC, "modulo" );
    // note ==, !=, >, >=, <, <=  are covered via Comparable **iff implementing manifold-science interfaces**
  }};

  public ManPsiBinaryExpressionImpl()
  {
    this( JavaElementType.BINARY_EXPRESSION );
  }

  protected ManPsiBinaryExpressionImpl( @NotNull IElementType elementType )
  {
    super( elementType );
  }

  @Override
  @NotNull
  public PsiJavaToken getOperationSign()
  {
    PsiJavaToken child = (PsiJavaToken)findChildByRoleAsPsiElement( ChildRole.OPERATION_SIGN );
    if( child == null )
    {
      child = new PsiJavaTokenImpl( JavaTokenType.ASTERISK, "*" );
    }
    return child;
  }
}

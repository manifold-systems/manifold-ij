package manifold.ij.core;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.java.PsiBinaryExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiJavaTokenImpl;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

// Supports binding expressions
public class ManPsiBinaryExpressionImpl extends PsiBinaryExpressionImpl
{
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
      // pose as multiplication to get by
      child = new PsiJavaTokenImpl( JavaTokenType.ASTERISK, "*" );
    }
    return child;
  }
}

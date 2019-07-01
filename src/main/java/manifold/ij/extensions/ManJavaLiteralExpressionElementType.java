package manifold.ij.extensions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.impl.java.stubs.JavaLiteralExpressionElementType;
import com.intellij.psi.impl.java.stubs.impl.PsiLiteralStub;
import org.jetbrains.annotations.NotNull;

public class ManJavaLiteralExpressionElementType extends JavaLiteralExpressionElementType
{
  public PsiLiteralExpression createPsi( @NotNull ASTNode node )
  {
    return new ManPsiLiteralExpressionImpl( node );
  }

  @Override
  public PsiLiteralExpression createPsi( @NotNull PsiLiteralStub stub )
  {
    return new ManPsiLiteralExpressionImpl( stub );
  }
}

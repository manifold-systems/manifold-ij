package manifold.ij.extensions;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Overrides PsiBuilderImpl to handle comments having fragments, cooperates with ManDefaultASTFactoryImpl and
 * ManPsiBuilderFactory.
 */
public class ManPsiBuilderImpl extends PsiBuilderImpl
{
  private ASTNode _building;

  ManPsiBuilderImpl( @NotNull Project project, @NotNull ParserDefinition parserDefinition, @NotNull Lexer lexer, @NotNull ASTNode chameleon, @NotNull CharSequence text )
  {
    super( project, parserDefinition, lexer, chameleon, text );
    _building = chameleon;
  }

  @NotNull
  @Override
  public ASTNode getTreeBuilt()
  {
    try
    {
      return super.getTreeBuilt();
    }
    finally
    {
      ((ManPsiBuilderFactoryImpl)PsiBuilderFactory.getInstance()).popNode( _building );
    }
  }
}

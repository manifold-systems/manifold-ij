package manifold.ij.extensions;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.impl.PsiBuilderFactoryImpl;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import manifold.ij.core.ManProject;
import manifold.util.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ManPsiBuilderFactoryImpl extends PsiBuilderFactoryImpl
{
  private ThreadLocal<Stack<ASTNode>> _buildingNodes;

  public ManPsiBuilderFactoryImpl()
  {
    super();
    _buildingNodes = ThreadLocal.withInitial( Stack::new );
  }

  private void pushNode( @NotNull ASTNode chameleon )
  {
    if( chameleon.getElementType().getLanguage() instanceof JavaLanguage )
    {
      _buildingNodes.get().push( chameleon );
    }
  }

  void popNode( ASTNode building )
  {
    if( building.getElementType().getLanguage() instanceof JavaLanguage )
    {
      assert _buildingNodes.get().peek() == building;
      _buildingNodes.get().pop();
    }
  }

  ASTNode peekNode()
  {
    Stack<ASTNode> nodes = _buildingNodes.get();
    return nodes.isEmpty() ? null : nodes.peek();
  }

  @NotNull
  @Override
  public PsiBuilder createBuilder( @NotNull Project project, @NotNull ASTNode chameleon, @Nullable Lexer lexer, @NotNull Language lang, @NotNull CharSequence seq )
  {
    if( !ManProject.isManifoldInUse( project ) )
    {
      return super.createBuilder( project, chameleon, lexer, lang, seq );
    }

    pushNode( chameleon );
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage( lang );
    return new ManPsiBuilderImpl( project, parserDefinition, lexer != null ? lexer : createLexer( project, lang ), chameleon, seq );
  }

  @NotNull
  private static Lexer createLexer( final Project project, final Language lang )
  {
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage( lang );
    assert parserDefinition != null : "ParserDefinition absent for language: " + lang.getID();
    return parserDefinition.createLexer( project );
  }
}

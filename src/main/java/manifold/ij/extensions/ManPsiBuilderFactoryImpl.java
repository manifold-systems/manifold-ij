package manifold.ij.extensions;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.impl.PsiBuilderFactoryImpl;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.text.BlockSupport;
import com.intellij.testFramework.LightVirtualFile;
import manifold.ij.core.ManProject;
import manifold.ij.util.FileUtil;
import manifold.api.util.Stack;
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

  void pushNode( @NotNull ASTNode chameleon )
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

    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage( lang );

    if( lexer instanceof JavaLexer )
    {
      // Replace lexer to handle Preprocessor
      lexer = new ManJavaLexer( (JavaLexer)lexer );
    }
    else if( lexer == null )
    {
      lexer = createLexer( project, lang );
    }

    if( lexer instanceof ManJavaLexer )
    {
      // For preprocessor
      ((ManJavaLexer)lexer).setChameleon( chameleon );
    }
    
    return new ManPsiBuilderImpl( project, parserDefinition, lexer, chameleon, seq );
  }

  @NotNull
  private static Lexer createLexer( final Project project, final Language lang )
  {
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage( lang );
    assert parserDefinition != null : "ParserDefinition absent for language: " + lang.getID();
    return parserDefinition.createLexer( project );
  }

  static PsiJavaFile getPsiFile( ASTNode chameleon )
  {
    PsiFile containingFile = null;
    PsiElement psi = chameleon.getPsi();
    if( psi != null )
    {
      if( psi instanceof PsiFile )
      {
        containingFile = (PsiFile)psi;
      }
      else if( psi.getContainingFile() != null )
      {
        containingFile = psi.getContainingFile();
        if( containingFile.getVirtualFile() == null || containingFile.getVirtualFile() instanceof LightVirtualFile )
        {
          containingFile = null;
        }
      }
    }

    if( containingFile == null )
    {
      ASTNode originalNode = Pair.getFirst( chameleon.getUserData( BlockSupport.TREE_TO_BE_REPARSED ) );
      if( originalNode == null )
      {
        return null;
      }

      containingFile = originalNode.getPsi().getContainingFile();
    }

    return containingFile instanceof PsiJavaFile && FileUtil.toVirtualFile( containingFile ) != null
           ? (PsiJavaFile)containingFile
           : null;
  }
}

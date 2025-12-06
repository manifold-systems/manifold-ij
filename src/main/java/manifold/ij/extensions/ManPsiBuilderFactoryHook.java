package manifold.ij.extensions;

import com.intellij.java.syntax.lexer.JavaLexer;
import com.intellij.java.syntax.lexer.JavaLexerHook;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.Pair;
import com.intellij.platform.syntax.lexer.Lexer;
import com.intellij.platform.syntax.psi.PsiSyntaxBuilderFactory;
import com.intellij.platform.syntax.psi.PsiSyntaxBuilderFactoryHook;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.text.BlockSupport;
import com.intellij.testFramework.LightVirtualFile;
import manifold.ij.util.FileUtil;
import manifold.rt.api.util.Stack;
import manifold.util.ReflectUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("UnstableApiUsage")
public class ManPsiBuilderFactoryHook implements PsiSyntaxBuilderFactoryHook
{
  private final ThreadLocal<Stack<ASTNode>> _buildingNodes = ThreadLocal.withInitial( Stack::new );

  @Override
  public void accept(@NotNull PsiSyntaxBuilderFactory psiSyntaxBuilderFactory) {
  }

  @Override
  public void createBuilder(@NotNull ASTNode chameleon, @Nullable Lexer lexer, @NotNull Language lang, @NotNull CharSequence text)
  {
    if( lexer instanceof JavaLexer)
    {
      // For preprocessor
      try
      {
        JavaLexerHook hook = (JavaLexerHook) ReflectUtil.field( lexer, "myLexerHook" ).get();
        if( hook != null )
        {
          ((ManPreprocessorJavaLexerHook)hook).setChameleon( chameleon );
        }
      }
      catch( Exception ignore )
      {
        // this can happen in the debugger when it evaluates expressions
      }
    }
  }

  @Override
  public void pushNode( @Nullable ASTNode chameleon )
  {
    if( chameleon != null && chameleon.getElementType().getLanguage() instanceof JavaLanguage)
    {
      _buildingNodes.get().push( chameleon );
    }
  }

  @Override
  public void popNode( @Nullable ASTNode building )
  {
    if( building != null && building.getElementType().getLanguage() instanceof JavaLanguage )
    {
      assert _buildingNodes.get().peek() == building;
      _buildingNodes.get().pop();
    }
  }

  @Override
  public @Nullable ASTNode peekNode()
  {
    Stack<ASTNode> nodes = _buildingNodes.get();
    return nodes.isEmpty() ? null : nodes.peek();
  }

  static PsiJavaFile getPsiFile(ASTNode chameleon )
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
        if( containingFile.getVirtualFile() == null || containingFile.getVirtualFile() instanceof LightVirtualFile)
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

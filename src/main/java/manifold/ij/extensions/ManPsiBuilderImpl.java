package manifold.ij.extensions;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.java.PsiJavaTokenImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import manifold.api.fs.IFileFragment;
import manifold.internal.javac.HostKind;
import manifold.util.ReflectUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Overrides PsiBuilderImpl to handle comments having fragments, cooperates with ManDefaultASTFactoryImpl and
 * ManPsiBuilderFactory.
 */
public class ManPsiBuilderImpl extends PsiBuilderImpl
{
  private ASTNode _building;

  ManPsiBuilderImpl( @NotNull Project project, @NotNull ParserDefinition parserDefinition, @NotNull Lexer lexer,
                     @NotNull ASTNode chameleon, @NotNull CharSequence text )
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

  @NotNull
  @Override
  protected TreeElement createLeaf( @NotNull IElementType type, int start, int end )
  {
    if( type == JavaTokenType.STRING_LITERAL || type == JavaTokenType.RAW_STRING_LITERAL )
    {
      CharSequence text = ((CharTable)ReflectUtil.field( this, "myCharTable" ).get())
        .intern( getOriginalText(), start, end );
      return new ManPsiStringLiteral( type, text );
    }

    return super.createLeaf( type, start, end );
  }

  static class ManPsiStringLiteral extends PsiJavaTokenImpl implements PsiFileFragment
  {
    private IFileFragment _fragment;

    ManPsiStringLiteral( @NotNull IElementType type, @NotNull CharSequence text )
    {
      super( type, text );
      if( type.getLanguage() instanceof JavaLanguage )
      {
        handleFragments();
      }
    }

    @Override
    public IFileFragment getFragment()
    {
      return _fragment;
    }

    @Override
    public void setFragment( IFileFragment fragment )
    {
      _fragment = fragment;
    }

    @Override
    public HostKind getStyle()
    {
      return getTokenType() == JavaTokenType.STRING_LITERAL
             ? HostKind.DOUBLE_QUOTE_LITERAL
             : HostKind.BACKTICK_LITERAL;
    }
  }
}

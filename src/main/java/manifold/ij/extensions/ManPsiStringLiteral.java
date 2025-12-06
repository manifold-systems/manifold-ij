package manifold.ij.extensions;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.java.PsiJavaTokenImpl;
import com.intellij.psi.tree.IElementType;
import manifold.api.fs.IFileFragment;
import manifold.internal.javac.HostKind;
import org.jetbrains.annotations.NotNull;

class ManPsiStringLiteral extends PsiJavaTokenImpl implements PsiFileFragment
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
           : HostKind.TEXT_BLOCK_LITERAL;
  }
}

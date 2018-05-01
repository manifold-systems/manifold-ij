package manifold.ij.template.psi;

import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.annotations.NotNull;

public class ManTemplateTokenImpl extends LeafPsiElement implements ManTemplateToken
{
  public ManTemplateTokenImpl( @NotNull ManTemplateTokenType type, CharSequence text )
  {
    super( type, text );
  }

  @Override
  public ManTemplateTokenType getTokenType()
  {
    return (ManTemplateTokenType)getElementType();
  }

  @Override
  public String toString()
  {
    return "ManTemplateToken:" + getTokenType();
  }
}

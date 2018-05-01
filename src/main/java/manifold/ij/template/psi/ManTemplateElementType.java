package manifold.ij.template.psi;

import com.intellij.psi.tree.IElementType;
import manifold.ij.template.ManTemplateLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ManTemplateElementType extends IElementType
{
  static final ManTemplateElementType ALL = new ManTemplateElementType( "ALL" );

  public ManTemplateElementType( @NotNull @NonNls String debugName )
  {
    super( debugName, ManTemplateLanguage.INSTANCE );
  }


}
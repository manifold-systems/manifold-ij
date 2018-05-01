package manifold.ij.template.psi;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.LanguageFileType;
import javax.swing.Icon;
import manifold.ij.template.ManTemplateJavaLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ManTemplateJavaFileType extends LanguageFileType
{
  public static final ManTemplateJavaFileType INSTANCE = new ManTemplateJavaFileType();

  private ManTemplateJavaFileType()
  {
    super( ManTemplateJavaLanguage.INSTANCE );
  }

  @NotNull
  @Override
  public String getName()
  {
    return "ManTemplateJava";
  }

  @NotNull
  @Override
  public String getDescription()
  {
    return getName();
  }

  @NotNull
  @Override
  public String getDefaultExtension()
  {
    return "*.manjava";
  }

  @Nullable
  @Override
  public Icon getIcon()
  {
    return AllIcons.FileTypes.Java;
  }

  @SuppressWarnings("deprecation")
  @Override
  public boolean isJVMDebuggingSupported()
  {
    return true;
  }
}

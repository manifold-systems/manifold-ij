package manifold.ij.template;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import org.jetbrains.annotations.Nullable;

public class ManTemplateJavaLanguage extends Language
{
  public static final ManTemplateJavaLanguage INSTANCE = new ManTemplateJavaLanguage();

  private ManTemplateJavaLanguage()
  {
    super( "ManTemplateJava" );
  }

  @Nullable
  @Override
  public Language getBaseLanguage()
  {
    return JavaLanguage.INSTANCE;
  }
}
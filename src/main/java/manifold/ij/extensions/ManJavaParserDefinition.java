package manifold.ij.extensions;

import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * For the Preprocessor.  Replaces JavaParserDefinition so we can replace the JavaLexer with ManJavaLexer, so we can
 * handle '#' preprocessor tokens.
 */
public class ManJavaParserDefinition extends JavaParserDefinition
{
  @Override
  @NotNull
  public Lexer createLexer( @Nullable Project project )
  {
    LanguageLevel level = project != null
                          ? LanguageLevelProjectExtension.getInstance( project ).getLanguageLevel()
                          : LanguageLevel.HIGHEST;
    return createLexer( level );
  }

  @NotNull
  public static Lexer createLexer( @NotNull LanguageLevel level )
  {
    return new ManJavaLexer( level );
  }
}

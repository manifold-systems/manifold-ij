package manifold.ij.template.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.lang.java.parser.StatementParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class ManTemplateJavaParser implements PsiParser
{
  @NotNull
  @Override
  public ASTNode parse( @NotNull IElementType root, @NotNull PsiBuilder builder )
  {
    builder.setDebugMode( ApplicationManager.getApplication().isUnitTestMode() );
    JavaParserUtil.setLanguageLevel( builder, LanguageLevelProjectExtension.getInstance( builder.getProject() ).getLanguageLevel() );
    PsiBuilder.Marker rootMarker = builder.mark();
    while( !builder.eof() )
    {
      new JavaParser().getStatementParser().parseStatements( builder );
    }
    rootMarker.done( root );
    return builder.getTreeBuilt();
  }
}

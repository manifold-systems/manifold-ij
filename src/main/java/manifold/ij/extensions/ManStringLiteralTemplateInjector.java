package manifold.ij.extensions;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import java.util.List;
import manifold.internal.javac.StringLiteralTemplateParser;
import org.jetbrains.annotations.NotNull;

public class ManStringLiteralTemplateInjector implements LanguageInjector
{
  private static final String PREFIX =
    "class _Muh_Class_ {\n" +
    "  Object _muhField_ = ";
  private static final String SUFFIX =
    ";\n" +
    "}\n";


  @Override
  public void getLanguagesToInject( @NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces injectionPlacesRegistrar )
  {
    if( !host.getLanguage().isKindOf( JavaLanguage.INSTANCE ) ||
        !(host instanceof PsiLiteralExpressionImpl) ||
        ((PsiLiteralExpressionImpl)host).getLiteralElementType() != JavaTokenType.STRING_LITERAL )
    {
      // Only applies to Java string literal expression
      return;
    }

    String hostText = host.getText();
    List<StringLiteralTemplateParser.Expr> exprs = StringLiteralTemplateParser.parse( hostText );
    if( exprs.isEmpty() )
    {
      // Not a template
      return;
    }

    for( StringLiteralTemplateParser.Expr expr: exprs )
    {
      if( !expr.isVerbatim() )
      {
        injectionPlacesRegistrar.addPlace(
          JavaLanguage.INSTANCE,
          new TextRange( expr.getOffset(), expr.getOffset() + expr.getExpr().length() ),
          PREFIX, SUFFIX );
      }
    }
  }
}

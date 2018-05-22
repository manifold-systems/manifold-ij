package manifold.ij.extensions;

import com.intellij.ide.highlighter.JavaFileHighlighter;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.HtmlHighlightingLexer;
import com.intellij.lexer.LayeredLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.StringLiteralLexer;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

//!!
//!! Copied from IJ's JavaHighlightingLexer so we can handle '$' as a legal escape char (for string literal templates)
//!!
public class ManJavaFileHighlighter extends JavaFileHighlighter
{
  public ManJavaFileHighlighter( LanguageLevel languageLevel )
  {
    super( languageLevel );
  }

  @Override
  @NotNull
  public Lexer getHighlightingLexer()
  {
    return new ManJavaHighlightingLexer( myLanguageLevel );
  }

  private class ManJavaHighlightingLexer extends LayeredLexer
  {
    public ManJavaHighlightingLexer( @NotNull LanguageLevel languageLevel )
    {
      super( JavaParserDefinition.createLexer( languageLevel ) );
      
      //!!
      //!! This is where we add '$' to override IJ's default behavior of highlighting it as an illegal escape char
      //!!
      registerSelfStoppingLayer( new StringLiteralLexer( '\"', JavaTokenType.STRING_LITERAL, false, "$" ),
        new IElementType[]{JavaTokenType.STRING_LITERAL}, IElementType.EMPTY_ARRAY );


      //!! The rest is copied from IJ's JavaHighlightingLexer...

      registerSelfStoppingLayer( new StringLiteralLexer( '\'', JavaTokenType.STRING_LITERAL ),
        new IElementType[]{JavaTokenType.CHARACTER_LITERAL}, IElementType.EMPTY_ARRAY );

      LayeredLexer docLexer = new LayeredLexer( JavaParserDefinition.createDocLexer( languageLevel ) );
      HtmlHighlightingLexer htmlLexer = new HtmlHighlightingLexer( null );
      htmlLexer.setHasNoEmbeddments( true );
      docLexer.registerLayer( htmlLexer, JavaDocTokenType.DOC_COMMENT_DATA );
      registerSelfStoppingLayer( docLexer, new IElementType[]{JavaDocElementType.DOC_COMMENT}, IElementType.EMPTY_ARRAY );
    }
  }
}

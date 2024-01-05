/*
 *
 *  * Copyright (c) 2022 - Manifold Systems LLC
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package manifold.ij.extensions;

import com.intellij.ide.highlighter.JavaFileHighlighter;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lang.java.lexer.JavaDocLexer;
import com.intellij.lexer.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.impl.source.BasicElementTypes;
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
    public ManJavaHighlightingLexer( @NotNull LanguageLevel languageLevel ) {
      super(JavaParserDefinition.createLexer(languageLevel));

      //!!
      //!! This is where we add '$' to override IJ's default behavior of highlighting it as an illegal escape char
      //!!
      registerSelfStoppingLayer(new JavaStringLiteralLexer('\"', JavaTokenType.STRING_LITERAL, false, "s{$"),
              new IElementType[]{JavaTokenType.STRING_LITERAL}, IElementType.EMPTY_ARRAY);

      //!! The rest is copied from IJ's JavaHighlightingLexer...

      registerSelfStoppingLayer(new JavaStringLiteralLexer('\'', JavaTokenType.STRING_LITERAL, false, "s"),
              new IElementType[]{JavaTokenType.CHARACTER_LITERAL}, IElementType.EMPTY_ARRAY);

      registerSelfStoppingLayer(new JavaStringLiteralLexer(JavaStringLiteralLexer.NO_QUOTE_CHAR, JavaTokenType.TEXT_BLOCK_LITERAL, true, "s{$"),
              new IElementType[]{JavaTokenType.TEXT_BLOCK_LITERAL}, IElementType.EMPTY_ARRAY);

      registerSelfStoppingLayer(new JavaStringLiteralLexer(JavaStringLiteralLexer.NO_QUOTE_CHAR, JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN, true, "s"),
              new IElementType[]{JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN}, IElementType.EMPTY_ARRAY);
      registerSelfStoppingLayer(new JavaStringLiteralLexer(JavaStringLiteralLexer.NO_QUOTE_CHAR, JavaTokenType.TEXT_BLOCK_TEMPLATE_MID, true, "s"),
              new IElementType[]{JavaTokenType.TEXT_BLOCK_TEMPLATE_MID}, IElementType.EMPTY_ARRAY);
      registerSelfStoppingLayer(new JavaStringLiteralLexer(JavaStringLiteralLexer.NO_QUOTE_CHAR, JavaTokenType.TEXT_BLOCK_TEMPLATE_END, true, "s"),
              new IElementType[]{JavaTokenType.TEXT_BLOCK_TEMPLATE_END}, IElementType.EMPTY_ARRAY);
      registerSelfStoppingLayer(new JavaStringLiteralLexer(JavaStringLiteralLexer.NO_QUOTE_CHAR, JavaTokenType.STRING_TEMPLATE_BEGIN, true, "s"),
              new IElementType[]{JavaTokenType.STRING_TEMPLATE_BEGIN}, IElementType.EMPTY_ARRAY);
      registerSelfStoppingLayer(new JavaStringLiteralLexer(JavaStringLiteralLexer.NO_QUOTE_CHAR, JavaTokenType.STRING_TEMPLATE_MID, true, "s"),
              new IElementType[]{JavaTokenType.STRING_TEMPLATE_MID}, IElementType.EMPTY_ARRAY);
      registerSelfStoppingLayer(new JavaStringLiteralLexer(JavaStringLiteralLexer.NO_QUOTE_CHAR, JavaTokenType.STRING_TEMPLATE_END, true, "s"),
              new IElementType[]{JavaTokenType.STRING_TEMPLATE_END}, IElementType.EMPTY_ARRAY);

      LayeredLexer docLexer = new LayeredLexer(new JavaDocLexer(languageLevel));

      //noinspection AbstractMethodCallInConstructor
      registerDocLayers(docLexer);
    }

    protected void registerDocLayers(@NotNull LayeredLexer docLexer) {
      HtmlLexer htmlLexer = new HtmlLexer(true);
      htmlLexer.setHasNoEmbeddments(true);
      docLexer.registerLayer(htmlLexer, JavaDocTokenType.DOC_COMMENT_DATA);
      registerSelfStoppingLayer(docLexer, new IElementType[]{JavaDocElementType.DOC_COMMENT}, IElementType.EMPTY_ARRAY);
    }

  }

  static class JavaStringLiteralLexer extends StringLiteralLexer {

    JavaStringLiteralLexer(char quoteChar, IElementType originalLiteralToken) {
      super(quoteChar, originalLiteralToken);
    }

    JavaStringLiteralLexer(char quoteChar,
                           IElementType originalLiteralToken,
                           boolean canEscapeEolOrFramingSpaces,
                           String additionalValidEscapes) {
      super(quoteChar, originalLiteralToken, canEscapeEolOrFramingSpaces, additionalValidEscapes);
    }

    @Override
    public IElementType getTokenType() {
      IElementType tokenType = super.getTokenType();
      if (tokenType == StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN && myBuffer.length() > myStart + 1) {
        char c = myBuffer.charAt(myStart + 1);
        if (c == '{' && BasicElementTypes.BASIC_STRING_TEMPLATE_FRAGMENTS.contains(myOriginalLiteralToken)) {
          // don't highlight \{ in template fragment as bad escape
          return myOriginalLiteralToken;
        }
      }
      return tokenType;
    }

    @Override
    protected @NotNull IElementType getUnicodeEscapeSequenceType() {
      int start = myStart + 2;
      while (start < myEnd && myBuffer.charAt(start) == 'u') start++;
      if (start + 3 >= myEnd) return StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN;
      if (!StringUtil.isHexDigit(myBuffer.charAt(start)) ||
              !StringUtil.isHexDigit(myBuffer.charAt(start + 1)) ||
              !StringUtil.isHexDigit(myBuffer.charAt(start + 2)) ||
              !StringUtil.isHexDigit(myBuffer.charAt(start + 3))) {
        return StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN;
      }
      return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN;
    }

    @Override
    protected int locateUnicodeEscapeSequence(int start, int i) {
      do {
        i++;
      }
      while (i < myBufferEnd && myBuffer.charAt(i) == 'u');
      int end = parseUnicodeDigits(i);
      if (end != i + 4) return end;
      int code = Integer.parseInt(myBuffer.subSequence(i, end).toString(), 16);
      i = end;
      // if escape sequence is not translated to backspace then continue from the next symbol
      if (code != '\\' || i >= myBufferEnd) return i;
      char c = myBuffer.charAt(i);
      if (StringUtil.isOctalDigit(c)) {
        if (i + 2 < myBufferEnd && StringUtil.isOctalDigit(myBuffer.charAt(i + 1)) && StringUtil.isOctalDigit(myBuffer.charAt(i + 1))) {
          return i + 3;
        }
      }
      else if (c == '\\' && i + 1 < myBufferEnd && myBuffer.charAt(i + 1) == 'u') {
        i += 2;
        while (i < myBufferEnd && myBuffer.charAt(i) == 'u') i++;
        return parseUnicodeDigits(i);
      }
      return i + 1;
    }

    private int parseUnicodeDigits(int i) {
      int end = i + 4;
      for (; i < end; i++) {
        if (i == myBufferEnd) return i;
        if (!StringUtil.isHexDigit(myBuffer.charAt(i))) return i;
      }
      return end;
    }
  }

}

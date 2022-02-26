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

package manifold.ij.template;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import manifold.ij.template.psi.ManTemplateLexer;
import manifold.ij.template.psi.ManTemplateTokenType;
import org.jetbrains.annotations.NotNull;


import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

public class ManTemplateHighlighter extends SyntaxHighlighterBase
{
  public static final TextAttributesKey COMMENT = createTextAttributesKey( "MANTL_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT );
  public static final TextAttributesKey DELIMITER = createTextAttributesKey( "MANTL_TEMPLATE_DELIMITER", DefaultLanguageHighlighterColors.MARKUP_TAG );

//  static final TextAttributesKey BAD_CHARACTER = createTextAttributesKey( "SIMPLE_BAD_CHARACTER",
//    new TextAttributes( Color.RED, null, null, null, Font.BOLD ) );

  //  private static final TextAttributesKey[] BAD_CHAR_KEYS = new TextAttributesKey[]{BAD_CHARACTER};
  private static final TextAttributesKey[] COMMENT_KEYS = new TextAttributesKey[]{COMMENT};
  private static final TextAttributesKey[] CODE_DELIM_KEYS = new TextAttributesKey[]{DELIMITER};
  private static final TextAttributesKey[] EMPTY_KEYS = new TextAttributesKey[0];

  @NotNull
  @Override
  public Lexer getHighlightingLexer()
  {
    return new ManTemplateLexer();
  }

  @NotNull
  @Override
  public TextAttributesKey[] getTokenHighlights( IElementType tokenType )
  {
    if( tokenType == ManTemplateTokenType.COMMENT_BEGIN ||
        tokenType == ManTemplateTokenType.COMMENT ||
        tokenType == ManTemplateTokenType.COMMENT_END )
    {
      return COMMENT_KEYS;
    }
    else if( tokenType == ManTemplateTokenType.EXPR_BRACE_BEGIN ||
             tokenType == ManTemplateTokenType.EXPR_BRACE_END ||
             tokenType == ManTemplateTokenType.EXPR_ANGLE_BEGIN ||
             tokenType == ManTemplateTokenType.STMT_ANGLE_BEGIN ||
             tokenType == ManTemplateTokenType.DIR_ANGLE_BEGIN ||
             tokenType == ManTemplateTokenType.ANGLE_END )
    {
      return CODE_DELIM_KEYS;
    }
    return EMPTY_KEYS;
  }
}

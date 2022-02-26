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

package manifold.ij.template.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtilCore;
import manifold.ij.template.ManTemplateLanguage;
import org.jetbrains.annotations.NotNull;


public class ManTemplateParserDefinition implements ParserDefinition
{
  private static final IFileElementType FILE = new IFileElementType( ManTemplateLanguage.INSTANCE );

  @NotNull
  @Override
  public Lexer createLexer( Project project )
  {
    return new ManTemplateLexer();
  }

  @Override
  public PsiParser createParser( Project project )
  {
    return new ManTemplateParser();
  }

  @Override
  public IFileElementType getFileNodeType()
  {
    return FILE;
  }

  @NotNull
  @Override
  public TokenSet getWhitespaceTokens()
  {
    return TokenSet.EMPTY;
  }

  @NotNull
  @Override
  public TokenSet getCommentTokens()
  {
    return TokenSet.EMPTY;
  }

  @NotNull
  @Override
  public TokenSet getStringLiteralElements()
  {
    return TokenSet.EMPTY;
  }

  @NotNull
  @Override
  public PsiElement createElement( ASTNode node )
  {
    IElementType type = node.getElementType();
    if( type instanceof ManTemplateElementType )
    {
      return new ManTemplateElementImpl( node );
    }

    return PsiUtilCore.NULL_PSI_ELEMENT;
  }

  @Override
  public PsiFile createFile( FileViewProvider viewProvider )
  {
    return new ManTemplateFile( viewProvider );
  }

  @Override
  public SpaceRequirements spaceExistenceTypeBetweenTokens( ASTNode left, ASTNode right )
  {
    return SpaceRequirements.MAY;
  }
}

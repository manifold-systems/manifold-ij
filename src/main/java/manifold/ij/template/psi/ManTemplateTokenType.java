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
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILeafElementType;
import java.util.HashMap;
import java.util.Map;
import manifold.ij.template.ManTemplateLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ManTemplateTokenType extends IElementType implements ILeafElementType
{
  private static final Map<String, ManTemplateTokenType> DELIMETER_TOKENS = new HashMap<>();

  public static final ManTemplateTokenType CONTENT = new ManTemplateTokenType( null, "CONTENT_TOKEN" );
  public static final ManTemplateTokenType COMMENT = new ManTemplateTokenType( null, "COMMENT_TOKEN" );
  public static final ManTemplateTokenType EXPR = new ManTemplateTokenType( null, "EXPR_TOKEN" );
  public static final ManTemplateTokenType STMT = new ManTemplateTokenType( null, "STMT_TOKEN" );
  public static final ManTemplateTokenType DIRECTIVE = new ManTemplateTokenType( null, "DIRECTIVE_TOKEN" );
  public static final ManTemplateTokenType EXPR_BRACE_BEGIN = new ManTemplateTokenType( "${", "EXPR_BRACE_BEGIN_TOKEN" );
  public static final ManTemplateTokenType EXPR_BRACE_END = new ManTemplateTokenType( "}", "EXPR_BRACE_END_TOKEN" );
  public static final ManTemplateTokenType EXPR_ANGLE_BEGIN = new ManTemplateTokenType( "<%=", "EXPR_ANGLE_BEGIN_TOKEN" );
  public static final ManTemplateTokenType STMT_ANGLE_BEGIN = new ManTemplateTokenType( "<%", "STMT_ANGLE_BEGIN_TOKEN" );
  public static final ManTemplateTokenType DIR_ANGLE_BEGIN = new ManTemplateTokenType( "<%@", "DIR_ANGLE_BEGIN_TOKEN" );
  public static final ManTemplateTokenType ANGLE_END = new ManTemplateTokenType( "%>", "ANGLE_END_TOKEN" );
  public static final ManTemplateTokenType COMMENT_BEGIN = new ManTemplateTokenType( "<%--", "COMMENT_BEGIN_TOKEN" );
  public static final ManTemplateTokenType COMMENT_END = new ManTemplateTokenType( "--%>", "COMMENT_END_TOKEN" );

  private final String _delimToken;

  private ManTemplateTokenType( String delimToken, @NotNull @NonNls String debugName )
  {
    super( debugName, ManTemplateLanguage.INSTANCE );
    _delimToken = delimToken;
    if( delimToken != null )
    {
      if( DELIMETER_TOKENS.putIfAbsent( delimToken, this ) != null )
      {
        throw new IllegalStateException( "Delimiter token: '" + delimToken + "' already exists" );
      }
    }
  }

  public String getToken()
  {
    return _delimToken;
  }

  @Override
  public String toString()
  {
    return "ManTemplateTokenType." + super.toString();
  }

  @NotNull
  @Override
  public ASTNode createLeafNode( @NotNull CharSequence leafText )
  {
    return new ManTemplateTokenImpl( this, leafText );
  }
}
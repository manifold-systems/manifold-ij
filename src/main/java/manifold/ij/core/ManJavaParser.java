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

package manifold.ij.core;

import com.intellij.lang.java.parser.*;
import org.jetbrains.annotations.NotNull;

/** So we can override the expression and statement parsers */
public class ManJavaParser extends JavaParser
{
  private final StatementParser myStatementParser;
  private final ExpressionParser myExpressionParser;

  public ManJavaParser()
  {
    myStatementParser = new ManStatementParser( this );
    myExpressionParser = new ManExpressionParser( this );
  }

  @NotNull
  public StatementParser getStatementParser()
  {
    return myStatementParser;
  }

  @NotNull
  public ExpressionParser getExpressionParser()
  {
    return myExpressionParser;
  }
}

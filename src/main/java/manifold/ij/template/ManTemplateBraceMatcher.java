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

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


import static manifold.ij.template.psi.ManTemplateTokenType.*;

public class ManTemplateBraceMatcher implements PairedBraceMatcher
{
  private static BracePair[] PAIRS = {
    new BracePair( DIR_ANGLE_BEGIN, ANGLE_END, true ),
    new BracePair( STMT_ANGLE_BEGIN, ANGLE_END, true ),
    new BracePair( EXPR_ANGLE_BEGIN, ANGLE_END, true ),
    new BracePair( EXPR_BRACE_BEGIN, EXPR_BRACE_END, true ),
  };

  @NotNull
  @Override
  public BracePair[] getPairs()
  {
    return PAIRS;
  }

  @Override
  public boolean isPairedBracesAllowedBeforeType( @NotNull IElementType lbraceType, @Nullable IElementType contextType )
  {
    return true;
  }

  @Override
  public int getCodeConstructStart( PsiFile file, int openingBraceOffset )
  {
    return openingBraceOffset;
  }
}
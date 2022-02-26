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

import com.intellij.psi.templateLanguages.OuterLanguageElementImpl;
import org.jetbrains.annotations.NotNull;

public class ManTemplateTokenImpl extends OuterLanguageElementImpl implements ManTemplateToken
{
  public ManTemplateTokenImpl( @NotNull ManTemplateTokenType type, CharSequence text )
  {
    super( type, text );
  }

  @Override
  public ManTemplateTokenType getTokenType()
  {
    return (ManTemplateTokenType)getElementType();
  }

  @Override
  public String toString()
  {
    return "ManTemplateToken:" + getTokenType();
  }
}

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

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.*;
import manifold.rt.api.DisableStringLiteralTemplates;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.Map;

public class ManTemplateColorSettingsPage implements ColorSettingsPage
{
  private static final AttributesDescriptor[] DESCRIPTORS = new AttributesDescriptor[]{
    new AttributesDescriptor( "Comment", ManTemplateHighlighter.COMMENT ),
    new AttributesDescriptor( "Delimiter", ManTemplateHighlighter.DELIMITER ),
  };

  @Nullable
  @Override
  public Icon getIcon()
  {
    return ManTemplateIcons.FILE;
  }

  @NotNull
  @Override
  public SyntaxHighlighter getHighlighter()
  {
    return new ManTemplateHighlighter();
  }

  @NotNull
  @Override
  @DisableStringLiteralTemplates
  public String getDemoText()
  {
    return "<%@ params(String name)%>\n" +
           "The quick brown fox jumps over the lazy dog.\n" +
           "<% java-code %>" +
           "<%=name%> sells seashells by the seashore\n" +
           "<% java-code %>\n" +
           "<%-- Good, better, best. Never rest until good be better and better best --%>\n" +
           "My name is ${name}\n";
  }

  @Nullable
  @Override
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap()
  {
    return null;
  }

  @NotNull
  @Override
  public AttributesDescriptor[] getAttributeDescriptors()
  {
    return DESCRIPTORS;
  }

  @NotNull
  @Override
  public ColorDescriptor[] getColorDescriptors()
  {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public String getDisplayName()
  {
    return "Manifold Template";
  }
}
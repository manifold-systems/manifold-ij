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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.LanguageFileType;
import javax.swing.Icon;
import manifold.ij.template.ManTemplateJavaLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ManTemplateJavaFileType extends LanguageFileType
{
  public static final ManTemplateJavaFileType INSTANCE = new ManTemplateJavaFileType();

  private ManTemplateJavaFileType()
  {
    super( ManTemplateJavaLanguage.INSTANCE );
  }

  @NotNull
  @Override
  public String getName()
  {
    return "ManTemplateJava";
  }

  @NotNull
  @Override
  public String getDescription()
  {
    return getName();
  }

  @NotNull
  @Override
  public String getDefaultExtension()
  {
    return "*.manjava";
  }

  @Nullable
  @Override
  public Icon getIcon()
  {
    return AllIcons.FileTypes.Java;
  }

  @SuppressWarnings("deprecation")
  @Override
  public boolean isJVMDebuggingSupported()
  {
    return true;
  }
}

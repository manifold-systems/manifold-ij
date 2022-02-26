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

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.source.PsiJavaFileBaseImpl;
import manifold.ij.template.ManTemplateJavaLanguage;
import org.jetbrains.annotations.NotNull;

public class ManTemplateJavaFile extends PsiJavaFileBaseImpl
{
  public ManTemplateJavaFile( @NotNull FileViewProvider viewProvider )
  {
    super( ManTemplateJavaParserDefinition.FILE, ManTemplateJavaParserDefinition.FILE, viewProvider );
  }

  @NotNull
  @Override
  public FileType getFileType()
  {
    return ManTemplateJavaFileType.INSTANCE;
  }

  @Override
  @NotNull
  public Language getLanguage()
  {
    return ManTemplateJavaLanguage.INSTANCE;
  }


  @Override
  public String toString()
  {
    return "Manifold Template JAVA File";
  }

  @Override
  public boolean importClass( PsiClass psiClass )
  {
    ManTemplateFile mantlFile = (ManTemplateFile)getViewProvider().getAllFiles().stream()
      .filter( file -> file instanceof ManTemplateFile )
      .findFirst().orElse( null );
    return mantlFile != null && mantlFile.importClass( psiClass );
  }
}
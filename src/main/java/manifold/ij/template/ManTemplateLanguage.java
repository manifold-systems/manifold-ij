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

import com.intellij.lang.InjectableLanguage;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.vfs.VirtualFile;

public class ManTemplateLanguage extends Language implements InjectableLanguage
{
  public static final ManTemplateLanguage INSTANCE = new ManTemplateLanguage();

  private ManTemplateLanguage()
  {
    super( "ManTL" );
  }

  /**
   * The convention for a ManTL file encodes the file extension of the content before the .mtl extension:
   * {@code MyFile.html.mtl}
   */
  public static FileType getContentLanguage( VirtualFile vfile )
  {
    String nameWithoutExtension = vfile.getNameWithoutExtension();
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName( nameWithoutExtension );
    if( fileType == FileTypes.UNKNOWN )
    {
      fileType = PlainTextFileType.INSTANCE;
    }
    return fileType;
  }
}
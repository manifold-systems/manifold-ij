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

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.LayerDescriptor;
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings;
import manifold.ij.template.psi.ManTemplateTokenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ManTemplateLayeredHighlighter extends LayeredLexerEditorHighlighter
{
  ManTemplateLayeredHighlighter( @Nullable Project project, @Nullable VirtualFile virtualFile, @NotNull EditorColorsScheme colors )
  {
    // Create ManTL highlighter
    super( new ManTemplateHighlighter(), colors );

    // Highlighter for outer lang
    FileType type = getOuterFileType( project, virtualFile );
    SyntaxHighlighter outerHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter( type, project, virtualFile );
    registerLayer( ManTemplateTokenType.CONTENT, new LayerDescriptor( outerHighlighter, "" ) );

    // Java is inner lang
    SyntaxHighlighter innerHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter( JavaFileType.INSTANCE, project, virtualFile );
    registerLayer( ManTemplateTokenType.EXPR, new LayerDescriptor( innerHighlighter, "" ) );
    registerLayer( ManTemplateTokenType.STMT, new LayerDescriptor( innerHighlighter, "" ) );
    registerLayer( ManTemplateTokenType.DIRECTIVE, new LayerDescriptor( innerHighlighter, "" ) );
  }

  private FileType getOuterFileType( @Nullable Project project, @Nullable VirtualFile virtualFile )
  {
    FileType type = null;
    if( project == null || virtualFile == null )
    {
      type = FileTypes.PLAIN_TEXT;
    }
    else
    {
      Language language = TemplateDataLanguageMappings.getInstance( project ).getMapping( virtualFile );
      if( language != null )
      {
        type = language.getAssociatedFileType();
      }
      if( type == null )
      {
        type = ManTemplateLanguage.getContentLanguage( virtualFile );
      }
    }
    return type;
  }
}


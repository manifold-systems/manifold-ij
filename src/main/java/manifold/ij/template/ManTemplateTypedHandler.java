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

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import manifold.ij.core.ManProject;
import org.jetbrains.annotations.NotNull;

public class ManTemplateTypedHandler extends TypedHandlerDelegate
{
  @NotNull
  @Override
  public Result charTyped( char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file )
  {
    if( !ManProject.isManifoldInUse( project ) )
    {
      // Manifold jars are not used in the project
      return Result.CONTINUE;
    }

    int offset = editor.getCaretModel().getOffset();
    FileViewProvider provider = file.getViewProvider();

    if( !provider.getBaseLanguage().isKindOf( ManTemplateLanguage.INSTANCE ) )
    {
      return Result.CONTINUE;
    }

    int docLength = editor.getDocument().getTextLength();
    if( offset < 2 || offset > docLength )
    {
      return Result.CONTINUE;
    }

    String previousChar = editor.getDocument().getText( new TextRange( offset - 2, offset - 1 ) );
    String nextChar = docLength > offset+1
                      ? editor.getDocument().getText( new TextRange( offset, offset + 1 ) )
                      : "";

    if( c == '%' && previousChar.equals( "<" ) && !nextChar.equalsIgnoreCase( "%" ) )
    {
      editor.getDocument().insertString( offset, "%>" );
    }
    else if( c == '{' && previousChar.equals( "$" ) && !nextChar.equalsIgnoreCase( "}" ) )
    {
      editor.getDocument().insertString( offset, "}" );
    }

    return Result.CONTINUE;
  }
}
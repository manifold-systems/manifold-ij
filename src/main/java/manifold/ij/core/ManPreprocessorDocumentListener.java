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

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.Alarm;
import com.intellij.util.FileContentUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

/**
 * For Preprocessor.
 * <p/>
 * Listens for changes to Java source files where a {@code #define} or {@code #undef} is involved and reparses the
 * file so the preprocessor can take into account the changes.
 */
public class ManPreprocessorDocumentListener implements DocumentListener
{
  private final Project _project;
  private final Alarm _alarm;
  private boolean _reparse;

  public ManPreprocessorDocumentListener( Project project )
  {
    _project = project;
    _alarm = new Alarm( Alarm.ThreadToUse.SWING_THREAD, project );
  }

  @Override
  public void beforeDocumentChange( @NotNull DocumentEvent event )
  {
    if( shouldReparse( event ) )
    {
      _reparse = true;
    }
  }

  @Override
  public void documentChanged( @NotNull DocumentEvent event )
  {
    if( _reparse || shouldReparse( event ) )
    {
      reparse( event );
    }
    _reparse = false;
  }

  private void reparse( @NonNull DocumentEvent event )
  {
    _alarm.cancelAllRequests();
    _alarm.addRequest( () -> {
      // Avoid interfering with active completion
      if( LookupManager.getInstance( _project ).getActiveLookup() != null )
      {
        reparse( event );
        return;
      }

      PsiDocumentManager.getInstance( _project ).commitDocument( event.getDocument() );
      ApplicationManager.getApplication().runReadAction( () -> {
        VirtualFile vfile = FileDocumentManager.getInstance().getFile( event.getDocument() );
        FileContentUtilCore.reparseFiles( vfile );
      } );
    }, 150 );
  }

  private boolean shouldReparse( DocumentEvent event )
  {
    if( _project.isDisposed() )
    {
      return false;
    }

    Document doc = event.getDocument();
    if( getLanguage( doc ) != JavaLanguage.INSTANCE )
    {
      return false;
    }

    if( FileDocumentManager.getInstance().getFile( doc ) instanceof LightVirtualFile )
    {
      return false;
    }

    return definitionChanged( event );
  }

  private Language getLanguage( Document document )
  {
    VirtualFile vfile = FileDocumentManager.getInstance().getFile( document );
    if( vfile == null || vfile instanceof LightVirtualFile )
    {
      // we check for LightVirtualFile because if that's the case IJ loses its mind if two or more projects are open
      // because a light vfile can only belong to one project, so our next call to PsiDocumentManager.getInstance( _project ).getPsiFile
      // below would otherwise log an ugly error (but not throw), thus we avoid the ugly error here
      return null;
    }

    PsiFile psiFile = PsiDocumentManager.getInstance( _project ).getPsiFile( document );
    if( psiFile == null )
    {
      return null;
    }

    return psiFile.getLanguage();
  }

  private boolean definitionChanged( @NotNull DocumentEvent event )
  {
    // todo: handle multiline changes, also check event.getFragement() for '#define' etc.

    int offset = event.getOffset();
    Document doc = event.getDocument();
    int line = doc.getLineNumber( offset );
    int lineStart = doc.getLineStartOffset( line );
    int lineEnd = doc.getLineEndOffset( line );
    String lineText = doc.getText( new TextRange( lineStart, lineEnd ) );
    return StringUtil.contains( lineText, "#define" ) ||
           StringUtil.contains( lineText, "#undef" );
  }
}

package manifold.ij.core;

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
import com.intellij.util.FileContentUtil;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * For Preprocessor.
 * <p/>
 * Listens for changes to Java source files where a {@code #define} or {@code #undef} is involved and reparses the
 * file so the preprocessor can take into account the changes.
 */
public class ManPreprocessorDocumentListener implements DocumentListener
{
  private final Project _project;
  private boolean _reparse;

  public ManPreprocessorDocumentListener( Project project )
  {
    _project = project;
  }

  @Override
  public void beforeDocumentChange( @NotNull DocumentEvent event )
  {
    Language language = getLanguage( event.getDocument() );
    if( language == JavaLanguage.INSTANCE )
    {
      if( definitionChanged( event ) )
      {
        _reparse = true;
      }
    }
  }

  @Override
  public void documentChanged( @NotNull DocumentEvent event )
  {
    if( _reparse || getLanguage( event.getDocument() ) == JavaLanguage.INSTANCE )
    {
      if( _reparse || definitionChanged( event ) )
      {
        ApplicationManager.getApplication().invokeLater( () -> {
          PsiDocumentManager.getInstance( _project ).commitDocument( event.getDocument() );
          ApplicationManager.getApplication().runReadAction( () -> {
            VirtualFile vfile = FileDocumentManager.getInstance().getFile( event.getDocument() );
            FileContentUtil.reparseFiles( vfile );
          } );
        } );
      }
    }
    _reparse = false;
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

package manifold.ij.actions;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.util.DocumentUtil;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 *
 */
public class JavaSourceViewerComponent extends JPanel implements Disposable
{
  private final Editor _editor;

  public JavaSourceViewerComponent( Project project )
  {
    super( new BorderLayout() );
    final EditorFactory factory = EditorFactory.getInstance();
    final Document doc = ((EditorFactoryImpl)factory).createDocument( "", true, false );
    doc.setReadOnly( true );
    _editor = factory.createEditor( doc, project );
    EditorHighlighterFactory editorHighlighterFactory = EditorHighlighterFactory.getInstance();
    final SyntaxHighlighter syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter( StdFileTypes.JAVA, project, null );
    ((EditorEx)_editor).setHighlighter( editorHighlighterFactory.createEditorHighlighter( syntaxHighlighter, EditorColorsManager.getInstance().getGlobalScheme() ) );
    ((EditorEx)_editor).setCaretVisible( true );

    final EditorSettings settings = _editor.getSettings();
    settings.setLineMarkerAreaShown( false );
    settings.setIndentGuidesShown( false );
    settings.setLineNumbersShown( false );
    settings.setFoldingOutlineShown( false );

    _editor.setBorder( null );
    add( _editor.getComponent(), BorderLayout.CENTER );
  }

  public void setText( final String source )
  {
    setText( source, 0 );
  }

  public void setText( final String source, final int offset )
  {
    DocumentUtil.writeInRunUndoTransparentAction( () -> {
      Document fragmentDoc = _editor.getDocument();
      fragmentDoc.setReadOnly( false );
      fragmentDoc.replaceString( 0, fragmentDoc.getTextLength(), source );
      fragmentDoc.setReadOnly( true );
      _editor.getCaretModel().moveToOffset( offset );
      _editor.getScrollingModel().scrollToCaret( ScrollType.RELATIVE );
    } );
  }

  public String getText()
  {
    return _editor.getDocument().getText();
  }

  public Editor getEditor()
  {
    return _editor;
  }

  @Override
  public void dispose()
  {
    EditorFactory.getInstance().releaseEditor( _editor );
  }
}
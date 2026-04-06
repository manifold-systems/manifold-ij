package manifold.ij.util;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;

public class WhatsActiveUtil
{
  public static Project getActiveProject()
  {
    IdeFrame frame = WindowManager.getInstance().getIdeFrame( null );
    return frame != null ? frame.getProject() : null;
  }

  public static VirtualFile getActiveFile( Project project )
  {
    Document doc = getActiveDocument( project );
    return doc != null ? FileDocumentManager.getInstance().getFile( doc ) : null;
  }

  public static Document getActiveDocument( Project project )
  {
    Editor editor = FileEditorManager.getInstance( project ).getSelectedTextEditor();
    return editor != null ? editor.getDocument() : null;
  }
}

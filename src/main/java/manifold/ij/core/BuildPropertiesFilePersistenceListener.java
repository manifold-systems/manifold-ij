package manifold.ij.core;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import manifold.ij.util.ReparseUtil;
import manifold.preprocessor.definitions.Definitions;
import org.jetbrains.annotations.NotNull;

/**
 * For preprocessor.  When a build.properties files is saved, open Java files reparse.
 */
class BuildPropertiesFilePersistenceListener implements FileDocumentManagerListener
{
  private Project _ijProject;

  BuildPropertiesFilePersistenceListener( Project ijProject )
  {
    _ijProject = ijProject;
  }

  @Override
  public void beforeDocumentSaving( @NotNull Document document )
  {
    reparseFiles( document );
  }

  @Override
  public void fileContentReloaded( @NotNull VirtualFile file, @NotNull Document document )
  {
    reparseFiles( document );
  }

  @Override
  public void fileContentLoaded( @NotNull VirtualFile file, @NotNull Document document )
  {
    reparseFiles( document );
  }

  private void reparseFiles( @NotNull Document document )
  {
    if( isBuildProperties( document ) )
    {
      ReparseUtil.reparseOpenJavaFiles( _ijProject );
    }
  }

  private boolean isBuildProperties( Document document )
  {
    PsiFile psiFile = PsiDocumentManager.getInstance( _ijProject ).getPsiFile( document );
    if( psiFile != null )
    {
      VirtualFile vfile = FileDocumentManager.getInstance().getFile( document );
      if( vfile != null )
      {
        return Definitions.BUILD_PROPERTIES.equalsIgnoreCase( vfile.getName() );
      }
    }
    return false;
  }
}

package manifold.ij.core;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
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
    VirtualFile vfile = FileDocumentManager.getInstance().getFile( document );
    if( vfile == null || vfile instanceof LightVirtualFile )
    {
      // we check for LightVirtualFile because if that's the case IJ loses its mind if two or more projects are open
      // because a light vfile can only belong to one project, so our next call to PsiDocumentManager.getInstance( _project ).getPsiFile
      // below would otherwise log an ugly error (but not throw), thus we avoid the ugly error here
      return false;
    }

    try
    {
      PsiFile psiFile = PsiDocumentManager.getInstance( _ijProject ).getPsiFile( document );
      if( psiFile != null )
      {
        return Definitions.BUILD_PROPERTIES.equalsIgnoreCase( vfile.getName() );
      }
    }
    catch( Throwable ignore )
    {
      // NPE and other exceptions can happen as a result of calling getPsiFile():
      // - for some reason due to "Recursive file view provider creation"
      // - "Light files should have PSI only in one project"
    }
    return false;
  }
}

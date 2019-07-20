package manifold.ij.core;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.FileContentUtil;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;
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
      ApplicationManager.getApplication().invokeLater(
        () -> ApplicationManager.getApplication().runReadAction(
          () -> FileContentUtil.reparseFiles( _ijProject, getOpenJavaFiles(), false ) ) );
    }
  }

  private Collection<? extends VirtualFile> getOpenJavaFiles()
  {
    return Arrays.stream( FileEditorManager.getInstance( _ijProject ).getOpenFiles() )
      .filter( vfile -> "java".equalsIgnoreCase( vfile.getExtension() ) )
      .collect( Collectors.toSet() );
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

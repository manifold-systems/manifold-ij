/*
 * Manifold
 */

package manifold.ij.extensions;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.ModuleRootEventImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiDocumentTransactionListener;
import com.intellij.testFramework.LightVirtualFile;
import java.io.File;
import java.util.List;
import manifold.api.fs.IFile;
import manifold.api.fs.IResource;
import manifold.ij.core.ManProject;
import manifold.ij.fs.IjFile;
import manifold.ij.util.DelayedRunner;
import manifold.ij.util.FileUtil;

public class FileModificationManager implements PsiDocumentTransactionListener, BulkFileListener
{
  public static int TYPE_REFRESH_DELAY_MS = 0;
  private final DelayedRunner _typeRefresher = new DelayedRunner();
  private final Project _project;
  private final ManProject _manProject;
  private ManRefreshListener _manRefresher;

  private class Refresher implements Runnable
  {
    private final Project _project;
    private final VirtualFile _file;

    Refresher( Project project, VirtualFile file )
    {
      _project = project;
      _file = file;
    }

    public void run()
    {
      ApplicationManager.getApplication().runReadAction( () -> fireModifiedEvent( _file ) );
    }
  }

  public FileModificationManager( ManProject project )
  {
    _project = project.getNativeProject();
    _manProject = ManProject.manProjectFrom( _project );
    _manRefresher = new ManRefreshListener( _project );
  }

  public ManRefreshListener getManRefresher()
  {
    return _manRefresher;
  }

  private String getRefreshTaskId( VirtualFile virtualFile )
  {
    return virtualFile.getPath();
  }

  // PsiDocumentTransactionListener
  public void transactionStarted( final Document doc, final PsiFile file )
  {
  }

  // Type creation and refresh
  // Also, this will not fire when a file is created externally (Use VirtualFileManager.VFS_CHANGES)
  public void transactionCompleted( final Document doc, final PsiFile psiFile )
  {
    VirtualFile file = FileUtil.toVirtualFile( psiFile );
    if( file instanceof VirtualFileWindow )
    {
      file = ((VirtualFileWindow)file).getDelegate();
    }

    final Runnable refresher = new Refresher( _project, file );
    if( TYPE_REFRESH_DELAY_MS == 0 )
    {
      refresher.run();
    }
    else
    {
      _typeRefresher.scheduleTask( getRefreshTaskId( file ), TYPE_REFRESH_DELAY_MS, refresher );
    }

    // process inner class changes
    if( psiFile instanceof PsiClassOwner )
    {
      _manRefresher.modified( getIFile( psiFile, file ) );
    }
  }

  private IjFile getIFile( PsiFile psiFile, VirtualFile file )
  {
    return ManProject.manProjectFrom( psiFile.getProject() ).getFileSystem().getIFile( file );
  }

  // BulkRefreshListener
  public void before( final List<? extends VFileEvent> events )
  {
    // Nothing to do
  }

  public void after( final List<? extends VFileEvent> events )
  {
    if( _project.isDisposed() )
    {
      return;
    }

    for( VFileEvent event : events )
    {
      final VirtualFile file = event.getFile();
      if( file != null )
      {
        if( event instanceof VFilePropertyChangeEvent )
        {
          processPropertyChangeEvent( (VFilePropertyChangeEvent)event );
        }
        else if( event instanceof VFileMoveEvent )
        {
          processFileMoveEvent( (VFileMoveEvent)event );
        }
        else if( event instanceof VFileCreateEvent )
        {
          fireCreatedEvent( file );
        }
        else if( event instanceof VFileDeleteEvent )
        {
          fireDeletedEvent( file );
        }
        else if( event instanceof VFileCopyEvent )
        {
          processFileCopyEvent( (VFileCopyEvent)event );
        }
        else
        {
          fireModifiedEvent( file );
        }
      }
    }
  }

  private void processFileCopyEvent( VFileCopyEvent event )
  {
    String newFileName = event.getNewParent().getPath() + "/" + event.getNewChildName();
    IFile newFile = _manProject.getFileSystem().getIFile( new File( newFileName ) );
    fireCreatedEvent( newFile );
  }

  private void processFileMoveEvent( VFileMoveEvent event )
  {
    VirtualFile newFile = event.getFile();
    String oldFileName = event.getOldParent().getPath() + "/" + newFile.getName();
    IFile oldFile = _manProject.getFileSystem().getIFile( new File( oldFileName ) );
    fireDeletedEvent( oldFile );
    fireCreatedEvent( newFile );
  }

  private void processPropertyChangeEvent( VFilePropertyChangeEvent event )
  {
    if( event.getFile().isDirectory() )
    { // a source folder could have been renamed
      ManProject.manProjectFrom( _project ).getModuleClasspathListener().rootsChanged( new ModuleRootEventImpl( _project, false ) );
    }

    if( event.getPropertyName().equals( VirtualFile.PROP_NAME ) )
    { // collect file renames
      final VirtualFile newFile = event.getFile();
      if( newFile instanceof LightVirtualFile )
      {
        return;
      }

      final String oldFileName = (String)event.getOldValue();
      final IFile oldFile = FileUtil.toIDirectory( _project, newFile.getParent() ).file( oldFileName );
      fireDeletedEvent( oldFile );
      fireCreatedEvent( FileUtil.toIResource( _project, newFile ) );
    }
  }

  private void fireModifiedEvent( VirtualFile file )
  {
    fireModifiedEvent( FileUtil.toIResource( _project, file ) );
  }

  private void fireDeletedEvent( VirtualFile file )
  {
    fireDeletedEvent( FileUtil.toIResource( _project, file ) );
  }

  private void fireCreatedEvent( VirtualFile file )
  {
    fireCreatedEvent( FileUtil.toIResource( _project, file ) );
  }

  private void fireModifiedEvent( IResource file )
  {
    _manRefresher.modified( file );
  }

  private void fireDeletedEvent( IResource file )
  {
    _manRefresher.deleted( file );
  }

  private void fireCreatedEvent( IResource file )
  {
    _manRefresher.created( file );
  }
}

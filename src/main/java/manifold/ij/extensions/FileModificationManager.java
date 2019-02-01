/*
 * Manifold
 */

package manifold.ij.extensions;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
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
  @SuppressWarnings("FieldCanBeLocal")
  private static int TYPE_REFRESH_DELAY_MS = 0;

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
    //System.out.println( psiFile.getText() );
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

//    // process inner class changes
//    if( psiFile instanceof PsiClassOwner )
//    {
//      _manRefresher.modified( getIFile( psiFile, file ) );
//    }
  }

  private IjFile getIFile( PsiFile psiFile, VirtualFile file )
  {
    return ManProject.manProjectFrom( psiFile.getProject() ).getFileSystem().getIFile( file );
  }

  // BulkRefreshListener
  public void before( final List<? extends VFileEvent> events )
  {
    if( _project.isDisposed() )
    {
      return;
    }

    DumbService dumb = DumbService.getInstance( _project );
    if( !dumb.isDumb() )
    {
      _before( events );
    }
  }

  private void _before( final List<? extends VFileEvent> events )
  {
    if( _project.isDisposed() )
    {
      return;
    }

    for( VFileEvent event : events )
    {
      final VirtualFile file = event.getFile();
      if( !ignoreFile( file ) )
      {
        if( isMoveOrRename( event ) )
        {
          processRenameBefore( event );
        }
      }
    }
  }

  public void after( final List<? extends VFileEvent> events )
  {
    if( _project.isDisposed() )
    {
      return;
    }

    DumbService dumb = DumbService.getInstance( _project );
    if( dumb.isDumb() )
    {
      dumb.smartInvokeLater( () -> _after( events ) );
    }
    else
    {
      ApplicationManager.getApplication().invokeLater( () ->_after( events ) );
    }
  }

  private void _after( final List<? extends VFileEvent> events )
  {
    if( _project.isDisposed() )
    {
      return;
    }

    for( VFileEvent event : events )
    {
      final VirtualFile file = event.getFile();
      if( !ignoreFile( file ) )
      {
        if( event instanceof VFileCreateEvent )
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
        else if( isMoveOrRename( event ) )
        {
          processRenameAfter( event );
        }
        else // modified
        {
          ApplicationManager.getApplication().runReadAction( () -> fireModifiedEvent( file ) );
        }
      }
    }
  }

  private boolean ignoreFile( VirtualFile file )
  {
    return file == null ||
           file.getPath().contains( "/.idea/" ) ||
           file.getPath().contains( "/.git/" ) ||
           file.getPath().contains( "/idea-sandbox/" );
  }

  private boolean isMoveOrRename( VFileEvent event )
  {
    return event instanceof VFilePropertyChangeEvent && ((VFilePropertyChangeEvent)event).getPropertyName().equals( VirtualFile.PROP_NAME ) ||
           event instanceof VFileMoveEvent;
  }

  private void processRenameBefore( VFileEvent event )
  {
    VirtualFile originalFile = event.getFile();
    if( originalFile instanceof LightVirtualFile )
    {
      return;
    }

    // Handle the Deletion *before* it is renamed
    fireDeletedEvent( originalFile );
  }

  private void processRenameAfter( VFileEvent event )
  {
    VirtualFile renamedFile = event.getFile();
    if( renamedFile instanceof LightVirtualFile )
    {
      return;
    }

    // Handle the Creation *after* it is renamed
    fireCreatedEvent( renamedFile );
  }

  private void processFileCopyEvent( VFileCopyEvent event )
  {
    String newFileName = event.getNewParent().getPath() + "/" + event.getNewChildName();
    IFile newFile = _manProject.getFileSystem().getIFile( new File( newFileName ) );
    fireCreatedEvent( newFile );
  }

  private void fireModifiedEvent( VirtualFile file )
  {
    if( !ignoreFile( file ) )
    {
      fireModifiedEvent( FileUtil.toIResource( _project, file ) );
    }
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

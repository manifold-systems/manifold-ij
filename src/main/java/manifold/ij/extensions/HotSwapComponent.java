package manifold.ij.extensions;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerManagerListener;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.HotSwapFile;
import com.intellij.debugger.impl.HotSwapManager;
import com.intellij.debugger.impl.HotSwapProgress;
import com.intellij.debugger.impl.MultiProcessCommand;
import com.intellij.debugger.ui.HotSwapProgressImpl;
import com.intellij.debugger.ui.HotSwapUIImpl;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.compiler.CompilationStatusListener;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerTopics;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBusConnection;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.tools.JavaFileObject;
import manifold.api.fs.IFile;
import manifold.api.sourceprod.ISourceProducer;
import manifold.ij.core.IjManifoldHost;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.internal.javac.InMemoryClassJavaFileObject;
import manifold.internal.javac.JavaParser;

/**
 */
public class HotSwapComponent implements DebuggerManagerListener
{
  private ManProject _manProject;
  private MessageBusConnection _conn;
  private Map<DebuggerSession, Long> _timeStamps;

  public static void attach( ManProject manProject )
  {
    DebuggerManager debugManager = DebuggerManager.getInstance( manProject.getNativeProject() );
    ((DebuggerManagerEx)debugManager).addDebuggerManagerListener( new HotSwapComponent( manProject ) );
  }

  private HotSwapComponent( ManProject manProject )
  {
    _manProject = manProject;
    _timeStamps = new HashMap<>();
  }

  @Override
  public void sessionAttached( DebuggerSession session )
  {
    if( _conn == null )
    {
      _conn = getIjProject().getMessageBus().connect();
      _conn.subscribe( CompilerTopics.COMPILATION_STATUS, new CompilationStatusHandler() );
      _timeStamps.put( session, System.currentTimeMillis() );
    }
  }

  @Override
  public void sessionDetached( DebuggerSession session )
  {
    if( !getHotSwappableDebugSessions().isEmpty() )
    {
      return;
    }

    final MessageBusConnection conn = _conn;
    if( conn != null )
    {
      Disposer.dispose( conn );
      _conn = null;
    }
  }

  private List<DebuggerSession> getHotSwappableDebugSessions()
  {
    return DebuggerManagerEx.getInstanceEx( getIjProject() ).getSessions().stream()
      .filter( HotSwapUIImpl::canHotSwap )
      .collect( Collectors.toCollection( SmartList::new ) );
  }

  private void reloadModifiedClasses( final Map<DebuggerSession, Map<String, HotSwapFile>> modifiedClasses, final HotSwapProgressImpl progress )
  {
    ProgressManager.getInstance().runProcess(
      () -> {
        HotSwapManager.reloadModifiedClasses( modifiedClasses, progress );
        progress.finished();
      }, progress.getProgressIndicator() );
  }

  private long getTimeStamp( DebuggerSession session )
  {
    Long tStamp = _timeStamps.get( session );
    return tStamp != null ? tStamp.longValue() : 0;
  }

  void setTimeStamp( DebuggerSession session, long tStamp )
  {
    _timeStamps.put( session, Long.valueOf( tStamp ) );
  }

  private void hotSwapSessions( final List<DebuggerSession> sessions )
  {
    HotSwapProgressImpl findClassesProgress = new HotSwapProgressImpl( getIjProject() );

    ApplicationManager.getApplication().executeOnPooledThread( () ->
    {
      final Map<DebuggerSession, Map<String, HotSwapFile>> modifiedClasses = scanForModifiedClassesWithProgress( sessions, findClassesProgress );

      final Application application = ApplicationManager.getApplication();
      if( modifiedClasses.isEmpty() )
      {
        final String message = DebuggerBundle.message( "status.hotswap.uptodate" );
        NotificationGroup.toolWindowGroup( "HotSwap", ToolWindowId.DEBUG ).createNotification( message, NotificationType.INFORMATION ).notify( getIjProject() );
        return;
      }

      application.invokeLater( () ->
      {
        if( !modifiedClasses.isEmpty() )
        {
          final HotSwapProgressImpl progress = new HotSwapProgressImpl( getIjProject() );
          if( modifiedClasses.keySet().size() == 1 )
          {
            //noinspection ConstantConditions
            progress.setSessionForActions( ContainerUtil.getFirstItem( modifiedClasses.keySet() ) );
          }
          application.executeOnPooledThread( () -> reloadModifiedClasses( modifiedClasses, progress ) );
        }
      }, ModalityState.NON_MODAL );
    } );
  }

  private Map<DebuggerSession, Map<String, HotSwapFile>> scanForModifiedClassesWithProgress( List<DebuggerSession> sessions, HotSwapProgressImpl progress )
  {
    Ref<Map<DebuggerSession, Map<String, HotSwapFile>>> result = Ref.create( null );
    ProgressManager.getInstance().runProcess(
      () -> {
        try
        {
          result.set( scanForModifiedClasses( sessions, progress ) );
        }
        finally
        {
          progress.finished();
        }
      }, progress.getProgressIndicator() );
    return result.get();
  }

  public Map<DebuggerSession, Map<String, HotSwapFile>> scanForModifiedClasses( List<DebuggerSession> sessions, HotSwapProgress swapProgress )
  {
    Map<DebuggerSession, Map<String, HotSwapFile>> modifiedClasses = new HashMap<>();

    MultiProcessCommand scanClassesCommand = new MultiProcessCommand();

    swapProgress.setCancelWorker( scanClassesCommand::cancel );

    for( final DebuggerSession debuggerSession : sessions )
    {
      if( debuggerSession.isAttached() )
      {
        scanClassesCommand.addCommand( debuggerSession.getProcess(), new DebuggerCommandImpl()
        {
          protected void action() throws Exception
          {
            swapProgress.setDebuggerSession( debuggerSession );
            Map<String, HotSwapFile> sessionClasses = scanForModifiedClasses( debuggerSession, swapProgress );
            if( !sessionClasses.isEmpty() )
            {
              modifiedClasses.put( debuggerSession, sessionClasses );
            }
          }
        } );
      }
    }

    swapProgress.setTitle( DebuggerBundle.message( "progress.hotswap.scanning.classes" ) );
    scanClassesCommand.run();

    if( swapProgress.isCancelled() )
    {
      for( DebuggerSession session : sessions )
      {
        session.setModifiedClassesScanRequired( true );
      }
      return new HashMap<>();
    }
    return modifiedClasses;
  }

  public Map<String, HotSwapFile> scanForModifiedClasses( DebuggerSession session, HotSwapProgress progress )
  {
    DebuggerManagerThreadImpl.assertIsManagerThread();

    Map<String, HotSwapFile> modifiedClasses = new HashMap<>();

    List<File> outputRoots = new ArrayList<>();
    ApplicationManager.getApplication().runReadAction(
      () -> {
         VirtualFile[] allDirs = OrderEnumerator.orderEntries( getIjProject() ).getAllSourceRoots();
         for( VirtualFile dir : allDirs )
         {
           outputRoots.add( new File( dir.getPath() ) );
         }
       } );
    long timeStamp = getTimeStamp( session );
    for( File root : outputRoots )
    {
      String rootPath = FileUtil.toCanonicalPath( root.getPath() );
      collectModifiedClasses( root, rootPath, modifiedClasses, progress, timeStamp );
    }
    setTimeStamp( session, System.currentTimeMillis() );
    return modifiedClasses;
  }

  private boolean collectModifiedClasses( File file, String filePath, Map<String, HotSwapFile> container, HotSwapProgress progress, long timeStamp )
  {
    if( progress.isCancelled() )
    {
      return false;
    }

    final File[] files = file.listFiles();
    if( files != null )
    {
      for( File child : files )
      {
        if( !collectModifiedClasses( child, filePath + "/" + child.getName(), container, progress, timeStamp ) )
        {
          return false;
        }
      }
    }
    else
    {
      if( !filePath.toLowerCase().endsWith( ".java" ) )
      {
        if( file.lastModified() > timeStamp )
        {
          IFile ifile = _manProject.getFileSystem().getIFile( file );
          Set<ISourceProducer> sps = new HashSet<>();
          for( ManModule module: _manProject.findRootModules() )
          {
            sps.addAll( module.findSourceProducersFor( ifile ) );
          }
          if( !sps.isEmpty() )
          {
            Set<String> fqns = new HashSet<>();
            for( ISourceProducer sp: sps )
            {
              fqns.addAll( Arrays.asList( sp.getTypesForFile( ifile ) ) );
            }
            Set<JavaFileObject> sourceFiles = new HashSet<>();
            for( ManModule module: _manProject.findRootModules() )
            {
              for( String fqn: fqns )
              {
                JavaFileObject sourceFile = module.produceFile( fqn, null );
                if( sourceFile != null )
                {
                  sourceFiles.add( sourceFile );
                }
              }
              if( !sourceFiles.isEmpty() )
              {
                ManModule prevModule = IjManifoldHost.getModule();
                IjManifoldHost.setModule( module );
                try
                {
                  Collection<InMemoryClassJavaFileObject> result = JavaParser.instance().compile( sourceFiles, Arrays.asList( "-g", "-nowarn", "-Xlint:none", "-proc:none", "-parameters" ), null );
                  result = result.stream().filter( e -> fqns.contains( e.getClassName() ) ).collect( Collectors.toList() );
                  Map<String, File> classes = makeTempFiles( result );
                  progress.setText( DebuggerBundle.message( "progress.hotswap.scanning.path", filePath ) );
                  for( Map.Entry<String, File> e : classes.entrySet() )
                  {
                    container.put( e.getKey(), new HotSwapFile( e.getValue() ) );
                  }
                }
                finally
                {
                  IjManifoldHost.setModule( prevModule );
                }
              }
            }
          }
        }
      }
    }
    return true;
  }

  private Map<String, File> makeTempFiles( Collection<InMemoryClassJavaFileObject> result )
  {
    Map<String, File> map = new HashMap<>();
    for( InMemoryClassJavaFileObject obj: result )
    {
      map.put( obj.getClassName(), createTempFile( obj.getBytes() ) );
    }
    return map;
  }

  private File createTempFile( byte[] bytes )
  {
    try
    {
      File file = FileUtil.createTempFile( new File( PathManager.getTempPath() ), "manifoldHotSwap", ".class" );
      FileUtil.writeToFile( file, bytes );
      return file;
    }
    catch( IOException e )
    {
      throw new RuntimeException( e );
    }
  }


  private Project getIjProject()
  {
    return _manProject.getNativeProject();
  }

  private class CompilationStatusHandler implements CompilationStatusListener
  {
    public void compilationFinished( boolean aborted, int errors, int warnings, CompileContext compileContext )
    {
      if( getIjProject().isDisposed() )
      {
        return;
      }

      if( errors > 0 || aborted )
      {
        return;
      }

      List<DebuggerSession> sessions = getHotSwappableDebugSessions();
      if( !sessions.isEmpty() )
      {
        hotSwapSessions( sessions );
      }
    }
  }
}

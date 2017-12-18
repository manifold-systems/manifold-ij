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
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import manifold.api.fs.IFile;
import manifold.api.type.ITypeManifold;
import manifold.ij.core.IjManifoldHost;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.internal.host.ManifoldHost;
import manifold.internal.javac.InMemoryClassJavaFileObject;
import manifold.internal.javac.JavaParser;
import manifold.util.StreamUtil;
import org.jetbrains.annotations.NotNull;

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

  public void sessionCreated( DebuggerSession session )
  {
  }
  public void sessionRemoved( DebuggerSession session )
  {
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
          Set<ITypeManifold> seen = new HashSet<>();
          for( ManModule module: _manProject.findRootModules() )
          {
            Set<ITypeManifold> typeManifolds = module.findTypeManifoldsFor( ifile );
            if( !typeManifolds.isEmpty() )
            {
              Set<String> fqns = new HashSet<>();
              for( ITypeManifold sp : typeManifolds )
              {
                if( seen.contains( sp ) )
                {
                  continue;
                }
                fqns.addAll( Arrays.asList( sp.getTypesForFile( ifile ) ) );
              }
              seen.addAll( typeManifolds );

              Set<JavaFileObject> sourceFiles = new HashSet<>();
              for( String fqn : fqns )
              {
                JavaFileObject sourceFile = module.produceFile( fqn, null );
                if( sourceFile != null )
                {
                  sourceFiles.add( sourceFile );
                }
              }
              if( !sourceFiles.isEmpty() )
              {
                ManModule previousModule = ((IjManifoldHost)ManifoldHost.instance()).setCurrentModule( module );
                try
                {
                  Collection<InMemoryClassJavaFileObject> result = compileManifoldFiles( sourceFiles );
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
                  ((IjManifoldHost)ManifoldHost.instance()).setCurrentModule( previousModule );
                }
              }
            }
          }
        }
      }
    }
    return true;
  }

  private Collection<InMemoryClassJavaFileObject> compileManifoldFiles( Set<JavaFileObject> sourceFiles )
  {
    URLClassLoader cl = makeCompilationClassLoader();
    try
    {
      Class<?> manifoldHostClass;
      try
      {
        manifoldHostClass = Class.forName( ManifoldHost.class.getName(), true, cl );
      }
      catch( ClassNotFoundException cnfe )
      {
        // The project does not have manifold as a dependency
        return Collections.emptyList();
      }

      Method bootstrapMethod = manifoldHostClass.getMethod( "bootstrap", List.class, List.class );
      bootstrapMethod.invoke( null, Collections.emptyList(), makeCompilerClassPath() );

      Class<?> javaParserClass = Class.forName( JavaParser.class.getName(), true, cl );
      Method instanceMethod = javaParserClass.getMethod( "instance" );
      Object javaParser = instanceMethod.invoke( null );
      Method compileMethod = javaParserClass.getMethod( "compile", Collection.class, Iterable.class, DiagnosticCollector.class );
      Collection result = (Collection)compileMethod.invoke( javaParser, sourceFiles, Arrays.asList( "-g", "-nowarn", "-Xlint:none", "-proc:none", "-parameters" ), null );
      //noinspection unchecked
      return (Collection)result.stream().map( f -> makeInMemoryClassJavaFileObject( (SimpleJavaFileObject)f ) ).collect( Collectors.toList() );
    }
    catch( Exception e )
    {
      throw new RuntimeException( e );
    }
  }

  private InMemoryClassJavaFileObject makeInMemoryClassJavaFileObject( SimpleJavaFileObject f )
  {
    String fqn = f.toUri().getPath().substring( 1 ).replace( '/', '.' );
    int iDot = fqn.lastIndexOf( '.' );
    fqn = fqn.substring( 0, iDot );
    InMemoryClassJavaFileObject memF = new InMemoryClassJavaFileObject( fqn, f.getKind() );
    try( OutputStream os = memF.openOutputStream();
         InputStream in = f.openInputStream() )
    {
      os.write( StreamUtil.getContent( in ) );
    }
    catch( IOException e )
    {
      throw new RuntimeException( e );
    }
    return memF;
  }

  @NotNull
  private URLClassLoader makeCompilationClassLoader()
  {
    return new URLClassLoader( makeClassLoaderClassPath() );
  }

  private List<File> makeCompilerClassPath()
  {
    List<File> outputRoots = new ArrayList<>();
    ApplicationManager.getApplication().runReadAction(
      () -> {
         final List<VirtualFile> allDirs = OrderEnumerator.orderEntries( getIjProject() ).withoutSdk().getPathsList().getRootDirs();
         for( VirtualFile dir : allDirs )
         {
           outputRoots.add( new File( getPath( dir ) ) );
         }
       } );
    return outputRoots;
  }

  private URL[] makeClassLoaderClassPath()
  {
    List<URL> outputRoots = new ArrayList<>();
        ApplicationManager.getApplication().runReadAction(
          () -> {
             final List<VirtualFile> allDirs = OrderEnumerator.orderEntries( getIjProject() ).withoutSdk().getPathsList().getRootDirs();
             for( VirtualFile dir : allDirs )
             {
               try
               {
                 outputRoots.add( new File( getPath( dir ) ).toURI().toURL() );
               }
               catch( MalformedURLException e )
               {
                 throw new RuntimeException( e );
               }
             }
           } );
        return outputRoots.toArray( new URL[outputRoots.size()] );
  }

  @NotNull
  private String getPath( VirtualFile dir )
  {
    String url = dir.getUrl();
    String path = dir.getPath();
    if( url.startsWith( "jar:" ) && path.endsWith( "!/" ) )
    {
      path = path.substring( 0, path.length()-2 );
    }
    return path.replace( '/', File.separatorChar );
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

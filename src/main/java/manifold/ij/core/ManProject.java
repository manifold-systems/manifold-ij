package manifold.ij.core;

import com.intellij.ProjectTopics;
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration;
import com.intellij.compiler.server.BuildManagerListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.PsiDocumentTransactionListener;
import com.intellij.util.PathsList;
import com.intellij.util.messages.MessageBusConnection;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import manifold.api.fs.IDirectory;
import manifold.api.fs.IFileUtil;
import manifold.api.fs.jar.JarFileDirectoryImpl;
import manifold.api.host.Dependency;
import manifold.api.host.IModule;
import manifold.ij.extensions.FileModificationManager;
import manifold.ij.extensions.HotSwapComponent;
import manifold.ij.extensions.ManifoldPsiClass;
import manifold.ij.extensions.ModuleClasspathListener;
import manifold.ij.extensions.ModuleRefreshListener;
import manifold.ij.fs.IjFile;
import manifold.ij.fs.IjFileSystem;
import manifold.internal.host.ManifoldHost;
import manifold.util.concurrent.ConcurrentWeakHashMap;
import manifold.util.concurrent.LockingLazyVar;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions;

/**
 */
public class ManProject
{
  private static final Map<Project, ManProject> PROJECTS = new ConcurrentWeakHashMap<>();
  private static final String JAR_INDICATOR = ".jar!";
  private static final String XPLUGIN_MANIFOLD = "-Xplugin:Manifold";

  private final Project _ijProject;
  private IjFileSystem _fs;
  private LockingLazyVar<List<ManModule>> _modules;
  private MessageBusConnection _projectConnection;
  private MessageBusConnection _applicationConnection;
  private MessageBusConnection _permanentProjectConnection;
  private ModuleClasspathListener _moduleClasspathListener;
  private FileModificationManager _fileModificationManager;

  public static Collection<ManProject> getAllProjects()
  {
    return PROJECTS.values().stream().filter( p -> !p.getNativeProject().isDisposed() ).collect( Collectors.toSet() );
  }

  public static Project projectFrom( ManModule module )
  {
    return module.getIjProject();
  }

  public static ManProject manProjectFrom( Module module )
  {
    return manProjectFrom( module.getProject() );
  }

  public static ManProject manProjectFrom( Project project )
  {
    return getProject( project );
  }

  public static ManModule getModule( Module module )
  {
    ManProject manProject = getProject( module.getProject() );
    for( ManModule mm : manProject.getModules() )
    {
      if( mm.getIjModule().equals( module ) )
      {
        return mm;
      }
    }

    // The module may not yet be committed to the project
    // e.g., a new module added in Module Structure dialog, but not yet saved.
    return manProject.defineModule( module );
  }

  public static Module getIjModule( PsiElement element )
  {
    Module module = ModuleUtil.findModuleForPsiElement( element );
    if( module != null )
    {
      return module;
    }

    ManifoldPsiClass javaFacadePsiClass = element.getContainingFile().getUserData( ManifoldPsiClass.KEY_MANIFOLD_PSI_CLASS );
    if( javaFacadePsiClass != null )
    {
      return javaFacadePsiClass.getModule();
    }

    return null;
  }

  public static ManModule getModule( PsiElement element )
  {
    Module ijModule = getIjModule( element );
    if( ijModule != null )
    {
      return getModule( ijModule );
    }
    return null;
  }

  private static ManProject getProject( Project project )
  {
    if( project.isDisposed() )
    {
      return null;
    }

    ManProject manProject = PROJECTS.get( project );
    if( manProject == null )
    {
      manProject = new ManProject( project );
      manProject.init();
      PROJECTS.put( project, manProject );
    }
    return manProject;
  }

  public ManProject( Project project )
  {
    _ijProject = project;
  }

  private void init()
  {
    _fs = new IjFileSystem( this );
    _modules = LockingLazyVar.make( () -> ApplicationManager.getApplication().<List<ManModule>>runReadAction( this::defineModules ) );
    addCompilerArgs(); // in case manifold jar was added we might need to update compiler args
  }

  public void reset()
  {
    ApplicationManager.getApplication().runReadAction(
      () -> {
        init();
        _fileModificationManager.getManRefresher().nukeFromOrbit();
      } );
  }

  public IjFileSystem getFileSystem()
  {
    return _fs;
  }

  public Project getNativeProject()
  {
    return _ijProject;
  }

  public List<ManModule> getModules()
  {
    return _modules.get();
  }

  void projectOpened()
  {
    _applicationConnection = ApplicationManager.getApplication().getMessageBus().connect();
    _projectConnection = _ijProject.getMessageBus().connect();
    _permanentProjectConnection = _ijProject.getMessageBus().connect();

    addTypeRefreshListener();
    addModuleRefreshListener();
    addModuleClasspathListener();
    addHotSwapComponent();
    addStaleClassCleaner();
    addCompilerArgs();
  }

  private void addStaleClassCleaner()
  {
    MessageBusConnection connection = _ijProject.getMessageBus().connect( _ijProject );
    connection.subscribe( BuildManagerListener.TOPIC, new ManStaleClassCleaner() );
   }

  private void addCompilerArgs()
  {
    JpsJavaCompilerOptions javacOptions = JavacConfiguration.getOptions( _ijProject, JavacConfiguration.class );
    String options = javacOptions.ADDITIONAL_OPTIONS_STRING;
    options = options == null ? "" : options;
    if( !options.contains( XPLUGIN_MANIFOLD ) )
    {
      options = XPLUGIN_MANIFOLD + maybeGetProcessorPath() + (options.isEmpty() ? "" : " ") + options;
    }
    else if( findJdkVersion() >= 9 && !options.contains( "-processorpath" ) )
    {
      options = maybeGetProcessorPath() + ((options.isEmpty() || options.startsWith( " " )) ? "" : " ") + options;
    }
    javacOptions.ADDITIONAL_OPTIONS_STRING = options;
  }

  private String maybeGetProcessorPath()
  {
    int jdkVersion = findJdkVersion();
    if( jdkVersion >= 9 )
    {
      PathsList pathsList = ProjectRootManager.getInstance( _ijProject ).orderEntries().withoutSdk().librariesOnly().getPathsList();
      for( VirtualFile path: pathsList.getVirtualFiles() )
      {
        String extension = path.getExtension();
        if( extension != null && extension.equals( "jar" ) && path.getNameWithoutExtension().contains( "manifold-" ) )
        {
          try
          {
            return " -processorpath " + new File( new URL( path.getUrl() ).getFile() ).getAbsolutePath() ;
          }
          catch( MalformedURLException e )
          {
            return "";
          }
        }
      }
    }
    return "";
  }

  private int findJdkVersion()
  {
    Sdk projectSdk = ProjectRootManager.getInstance( _ijProject ).getProjectSdk();
    if( projectSdk == null )
    {
      return -1;
    }

    // expected format:
    // 'java version "1.8.1"'
    // 'java version "9.0.1"'
    // 'java version "10.0.1"'
    // etc.
    String version = projectSdk.getVersionString();
    int iQuote = version.indexOf( '"' );
    if( iQuote < 0 )
    {
      return -1;
    }

    String verNum = version.substring( iQuote+1 );
    int iDot = verNum.indexOf( '.' );
    if( iDot < 0 )
    {
      return -1;
    }

    verNum = verNum.substring( 0, iDot );
    if( verNum.equals( "1" ) )
    {
      return 8;
    }
    else
    {
      return Integer.parseInt( verNum );
    }
  }

  private void addHotSwapComponent()
  {
    HotSwapComponent.attach( this );
  }

  public ModuleClasspathListener getModuleClasspathListener()
  {
    return _moduleClasspathListener;
  }

  private void addModuleClasspathListener()
  {
    _moduleClasspathListener = new ModuleClasspathListener();
    _permanentProjectConnection.subscribe( ProjectTopics.PROJECT_ROOTS, _moduleClasspathListener );
  }

  public void projectClosed()
  {
    _projectConnection.disconnect();
    _projectConnection = null;
    PROJECTS.remove( getNativeProject() );
    _fileModificationManager.getManRefresher().nukeFromOrbit();
  }

  private void addTypeRefreshListener() {
    _fileModificationManager = new FileModificationManager( this );
    _projectConnection.subscribe( PsiDocumentTransactionListener.TOPIC, _fileModificationManager );
    _applicationConnection.subscribe( VirtualFileManager.VFS_CHANGES, _fileModificationManager );
  }

  private void addModuleRefreshListener()
  {
    ModuleRefreshListener moduleRefreshListener = new ModuleRefreshListener();
    _projectConnection.subscribe( ProjectTopics.MODULES, moduleRefreshListener );
  }

  public FileModificationManager getFileModificationManager()
  {
    return _fileModificationManager;
  }

  public List<ManModule> findRootModules()
  {
    List<ManModule> roots = new ArrayList<>( getModules() );
    for( ManModule module : new ArrayList<>( roots ) )
    {
      for( Dependency d : module.getDependencies() )
      {
        //noinspection SuspiciousMethodCalls
        roots.remove( d.getModule() );
      }
    }
    return roots;
  }

  private List<ManModule> defineModules()
  {
    ModuleManager moduleManager = ModuleManager.getInstance( _ijProject );
    Module[] allIjModules = moduleManager.getModules();

    // create modules
    Map<Module, ManModule> modules = new HashMap<>();
    List<ManModule> allModules = new ArrayList<>();
    for( Module ijModule : allIjModules )
    {
      final ManModule module = defineModule( ijModule );
      modules.put( ijModule, module );
      allModules.add( module );
    }

    // add module dependencies
    for( Module ijModule : allIjModules )
    {
      addModuleDependencies( modules, modules.get( ijModule ) );
    }

    // reduce classpaths
    Set<ManModule> visited = new HashSet<>();
    for( ManModule manModule: allModules )
    {
      manModule.reduceClasspath( visited );
    }

    // finally, initialize the type manifolds for each module
    for( ManModule manModule: allModules )
    {
      manModule.initializeTypeManifolds();
    }

    return allModules;
  }

  private void addModuleDependencies( Map<Module, ManModule> modules, ManModule manModule )
  {
    Module ijModule = manModule.getIjModule();
    for( Module child : ModuleRootManager.getInstance( ijModule ).getDependencies() )
    {
      IModule moduleDep = modules.get( child );
      if( moduleDep != null )
      {
        manModule.addDependency( new Dependency( moduleDep, isExported( ijModule, child ) ) );
      }
    }
  }

  public static boolean isExported( Module ijModule, Module child )
  {
    for( OrderEntry entry : ModuleRootManager.getInstance( ijModule ).getOrderEntries() )
    {
      if( entry instanceof ModuleOrderEntry )
      {
        final ModuleOrderEntry moduleEntry = (ModuleOrderEntry)entry;
        final DependencyScope scope = moduleEntry.getScope();
        if( !scope.isForProductionCompile() && !scope.isForProductionRuntime() )
        {
          continue;
        }
        final Module module = moduleEntry.getModule();
        if( module != null && module == child )
        {
          return moduleEntry.isExported();
        }
      }
    }
    return false;
  }

  private ManModule defineModule( Module ijModule )
  {
    List<VirtualFile> sourceFolders = getSourceRoots( ijModule );
    VirtualFile outputPath = CompilerPaths.getModuleOutputDirectory( ijModule, false );
    return createModule( ijModule, getInitialClasspaths( ijModule ),
                         sourceFolders.stream().map( this::toDirectory ).collect( Collectors.toList() ),
                         outputPath == null ? null : getFileSystem().getIDirectory( outputPath ) );
  }

  private ManModule createModule( Module ijModule, List<IDirectory> classpath, List<IDirectory> sourcePaths, IDirectory outputPath )
  {
    // Maybe expand paths to include Class-Path attribute from Manifest...
    classpath = addFromManifestClassPath( classpath );
    sourcePaths = addFromManifestClassPath( sourcePaths );

    // Scan....
    List<IDirectory> sourceRoots = new ArrayList<>( sourcePaths );
    scanPaths( classpath, sourceRoots );

    return new ManModule( this, ijModule, classpath, sourceRoots, Collections.singletonList( outputPath ), getExcludedFolders( ijModule ) );
  }

  private static void scanPaths( List<IDirectory> paths, List<IDirectory> roots )
  {
    //noinspection Convert2streamapi
    for( IDirectory root : paths )
    {
      // roots without manifests are considered source roots
      if( IFileUtil.hasSourceFiles( root ) )
      {
        if( !roots.contains( root ) )
        {
          roots.add( root );
        }
      }
    }
  }

  /**
   * <p>This will add items to the classpath, but only under very specific circumstances.
   * <p>If both of the following conditions are met:
   * <ul>
   * <li>The JAR's manifest contains a Class-Path entry</li>
   * <li>The Class-Path entry contains a space-delimited list of URIs</li>
   * </ul>
   * <p>Then the entries will be parsed and added to the classpath.
   * <p>
   * <p>This logic also handles strange libraries packaged pre-Maven such as xalan:xalan:2.4.1
   * <p>
   * <p>The xalan JAR above has a Class-Path attribute referencing the following:
   * <pre>
   *   Class-Path: xercesImpl.jar xml-apis.jar
   * </pre>
   * <p>
   * These unqualified references should have been resolved by the build tooling, and if we try to interfere and resolve
   * the references, we may cause classpath confusion. Therefore any Class-Path entry not resolvable to an absolute
   * path on disk (and, therefore, can be listed as a URL) will be skipped.
   *
   * @param classpath The module's Java classpath
   *
   * @return The original classpath, possibly with dependencies listed in JAR manifests Class-Path extracted and explicitly listed
   *
   * @see java.util.jar.Attributes.Name#CLASS_PATH
   */
  private List<IDirectory> addFromManifestClassPath( List<IDirectory> classpath )
  {
    if( classpath == null )
    {
      return classpath;
    }

    ArrayList<IDirectory> newClasspath = new ArrayList<>();
    for( IDirectory root : classpath )
    {
      //add the root JAR itself first, preserving ordering
      if( !newClasspath.contains( root ) )
      {
        newClasspath.add( root );
      }
      if( root instanceof JarFileDirectoryImpl )
      {
        JarFile jarFile = ((JarFileDirectoryImpl)root).getJarFile();
        try
        {
          Manifest manifest = jarFile.getManifest();
          if( manifest != null )
          {
            Attributes man = manifest.getMainAttributes();
            String paths = man.getValue( Attributes.Name.CLASS_PATH );
            if( paths != null && !paths.isEmpty() )
            {
              // We found a Jar with a Class-Path listing.
              // Note sometimes happens when running from IntelliJ where the
              // classpath would otherwise make the command line to java.exe
              // too long.
              for( String j : paths.split( " " ) )
              {
                // Add each of the paths to our classpath
                URL url;
                try
                {
                  url = new URL( j );
                }
                catch( MalformedURLException e )
                {
                  //Class-Path contained an invalid URL, skip it
                  continue;
                }
                File dirOrJar = new File( url.toURI() );
                IDirectory idir = getFileSystem().getIDirectory( dirOrJar );
                if( !newClasspath.contains( idir ) )
                {
                  newClasspath.add( idir );
                }
              }
            }
          }
        }
        catch( Exception e )
        {
          throw new RuntimeException( e );
        }
      }
    }

    return newClasspath;
  }

  private List<IDirectory> getExcludedFolders( Module ijModule )
  {
    return getExcludedRoots( ijModule ).stream().map( this::toDirectory ).collect( Collectors.toList() );
  }

  public static List<VirtualFile> getSourceRoots( Module ijModule )
  {
    final ModuleRootManager moduleManager = ModuleRootManager.getInstance( ijModule );
    final List<VirtualFile> sourcePaths = new ArrayList<>();
    List<VirtualFile> excludeRoots = Arrays.asList( moduleManager.getExcludeRoots() );
    for( VirtualFile sourceRoot : moduleManager.getSourceRoots() )
    {
      if( !excludeRoots.contains( sourceRoot ) )
      {
        sourcePaths.add( sourceRoot );
      }
    }

    return sourcePaths;
  }

  public static List<VirtualFile> getExcludedRoots( Module ijModule )
  {
    final ModuleRootManager moduleManager = ModuleRootManager.getInstance( ijModule );
    return Arrays.asList( moduleManager.getExcludeRoots() );
  }

  private IDirectory toDirectory( VirtualFile file )
  {
    String url = file.getUrl();
    if( url.contains( JAR_INDICATOR ) )
    {
      url = url.substring( 0, url.length() - 2 );
      try
      {
        IjFile ijFile = (IjFile)ManifoldHost.getFileSystem().getIFile( new URL( url ) );
        file = ijFile.getVirtualFile();
      }
      catch( MalformedURLException e )
      {
        throw new RuntimeException( e );
      }
    }
    return getFileSystem().getIDirectory( file );
  }

  private static List<IDirectory> getInitialClasspaths( Module ijModule )
  {
    List<String> paths = getDirectClassPaths( ijModule );
    List<IDirectory> dirs = new ArrayList<>();
    for( String path : paths )
    {
      dirs.add( manProjectFrom( ijModule ).getFileSystem().getIDirectory( new File( path ) ) );
    }
    return dirs;
  }

  private static List<String> getDirectClassPaths( Module ijModule )
  {
    final ModuleRootManager rootManager = ModuleRootManager.getInstance( ijModule );
    final List<OrderEntry> orderEntries = Arrays.asList( rootManager.getOrderEntries() );

    List<String> paths = new ArrayList<>();
    for( OrderEntry entry : orderEntries.stream().filter( (LibraryOrderEntry.class)::isInstance ).collect( Collectors.toList() ) )
    {
      final Library lib = ((LibraryOrderEntry)entry).getLibrary();
      if( lib != null )
      {
        for( VirtualFile virtualFile : lib.getFiles( OrderRootType.CLASSES ) )
        {
          final File file = new File( stripExtraCharacters( virtualFile.getPath() ) );
          if( file.exists() )
          {
            paths.add( file.getAbsolutePath() );
          }
        }
      }
    }
    return paths;
  }

  private static String stripExtraCharacters( String fileName )
  {
    if( fileName.endsWith( "!/" ) )
    {
      fileName = fileName.substring( 0, fileName.length() - 2 );
    }
    return fileName;
  }
}

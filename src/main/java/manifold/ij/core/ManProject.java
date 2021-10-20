package manifold.ij.core;

import com.intellij.AppTopics;
import com.intellij.ProjectTopics;
import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.module.ModuleUtilCore;
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
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiDocumentTransactionListener;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.PlatformUtils;
import com.intellij.util.messages.MessageBusConnection;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
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
import manifold.ij.extensions.ManifoldPsiClass;
import manifold.ij.extensions.ManifoldPsiClassCache;
import manifold.ij.extensions.ModuleClasspathListener;
import manifold.ij.extensions.ModuleRefreshListener;
import manifold.ij.fs.IjFile;
import manifold.ij.fs.IjFileSystem;
import manifold.ij.license.CheckLicense;
import manifold.ij.psi.ManLightMethodBuilder;
import manifold.ij.util.MessageUtil;
import manifold.util.concurrent.ConcurrentWeakHashMap;
import manifold.util.concurrent.LockingLazyVar;
import manifold.util.concurrent.LocklessLazyVar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions;

/**
 *
 */
public class ManProject
{
  private static final Map<Project, ManProject> PROJECTS = new ConcurrentWeakHashMap<>();
  private static final String JAR_INDICATOR = ".jar!";
  static final String XPLUGIN_MANIFOLD = "-Xplugin:Manifold";
  private static final String XPLUGIN_MANIFOLD_WITH_QUOTES = "-Xplugin:\"Manifold";

  private IjManifoldHost _host;
  private final Project _ijProject;
  private boolean _manInUse;
  private IjFileSystem _fs;
  private LockingLazyVar<Map<Module, ManModule>> _modules;
  private MessageBusConnection _projectConnection;
  private MessageBusConnection _applicationConnection;
  private MessageBusConnection _permanentProjectConnection;
  private FileModificationManager _fileModificationManager;
  private ManifoldPsiClassCache _psiClassCache;
  private LocklessLazyVar<Set<ManModule>> _rootModules;
  private boolean _hasNamedModule;

  @SuppressWarnings("unused")
  public static Collection<ManProject> getAllProjects()
  {
    return PROJECTS.values().stream().filter( p -> !p.getNativeProject().isDisposed() ).collect( Collectors.toSet() );
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
    if( manProject == null )
    {
      return null;
    }

    Map<Module, ManModule> modules = manProject.getModules();
    ManModule manModule = modules == null ? null : modules.get( module );
    if( manModule != null )
    {
      return manModule;
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

    if( element instanceof ManLightMethodBuilder )
    {
      ManModule manModule = ((ManLightMethodBuilder)element).getModule();
      if( manModule != null )
      {
        return manModule.getIjModule();
      }
    }

    PsiFile psiFile = element.getContainingFile();
    if( psiFile == null )
    {
      return null;
    }

    ManifoldPsiClass javaFacadePsiClass = psiFile.getUserData( ManifoldPsiClass.KEY_MANIFOLD_PSI_CLASS );
    if( javaFacadePsiClass != null )
    {
      return javaFacadePsiClass.getModule();
    }

    VirtualFile virtualFile = psiFile.getViewProvider().getVirtualFile();
    if( virtualFile instanceof LightVirtualFile )
    {
      VirtualFile originalFile = ((LightVirtualFile)virtualFile).getOriginalFile();
      if( originalFile != null )
      {
        return ModuleUtilCore.findModuleForFile( originalFile, psiFile.getProject() );
      }
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

  public static boolean isManifoldInUse( @NotNull PsiElement element )
  {
    return isManifoldInUse( element.getProject() );
  }

  public static boolean isManifoldInUse( @NotNull Project project )
  {
    ManProject manProject = ManProject.manProjectFrom( project );
    return manProject != null && manProject.isManifoldInUse();
  }

  private boolean isManifoldInUse()
  {
    return _manInUse;
  }

  private void init()
  {
    // Remove stock highlighter regardless of _manInUse since we add a replacement in plugin.xml
    removeHighlighter();
    
    _manInUse = ManLibraryChecker.instance().isUsingManifoldJars( _ijProject );

    licenseCheck();

    if( !_manInUse )
    {
      removeCompilerArgs();
      return;
    }

    _host = new IjManifoldHost( this );
    _fs = new IjFileSystem( this );
    _psiClassCache = new ManifoldPsiClassCache( this );
    _hasNamedModule = false;
    _modules = LockingLazyVar.make( () -> ApplicationManager.getApplication().<Map<Module, ManModule>>runReadAction( this::defineModules ) );
    _rootModules = assignRootModuleLazy();
    ManLibraryChecker.instance().warnIfManifoldJarsAreOld( getNativeProject() );
  }

  private void removeHighlighter()
  {
    // ManHighlightVisitor replaces this
    HighlightVisitor.EP_HIGHLIGHT_VISITOR.getPoint( getNativeProject() ).unregisterExtension( HighlightVisitorImpl.class );
  }

  private void licenseCheck()
  {
    if( _manInUse )
    {
      if( PlatformUtils.isIdeaUltimate() && // only apply license to Ultimate
          !ApplicationManager.getApplication().isEAP() && // only apply license to official releases
          !CheckLicense.isLicensed() &&
          !ApplicationManager.getApplication().isUnitTestMode() )
      {
        // let the plugin be free on Ultimate, but nag about it a little bit
        //_manInUse = false;

        ApplicationManager.getApplication().invokeLater( () ->
          MessageUtil.showWarning( _ijProject, MessageUtil.Placement.CENTER,
            "Your copy of the <b>Manifold</b> plugin is not licensed or your license has expired.<br>" +
            "<br>" +
            "Please configure your plugin license or trial period using the menu command:<br><br>" +
            "&nbsp;&nbsp;&nbsp;<b>Help | Register...</b><br><br>" ) );
      }
    }
  }

  private boolean isNamedModule( Module module )
  {
    List<VirtualFile> sourceRoots = getSourceRoots( module );
    for( VirtualFile file: sourceRoots )
    {
      VirtualFile child = file.findChild( "module-info.java" );
      if( child != null && !child.isDirectory() )
      {
        return true;
      }
    }
    return false;
  }

  @NotNull
  private LocklessLazyVar<Set<ManModule>> assignRootModuleLazy()
  {
    return LocklessLazyVar.make(
      () -> {
        HashSet<ManModule> roots = new HashSet<>( getModules().values() );
        for( ManModule module: new ArrayList<>( roots ) )
        {
          for( Dependency d: module.getDependencies() )
          {
            //noinspection SuspiciousMethodCalls
            roots.remove( d.getModule() );
          }
        }
        return roots;
      }
    );
  }

  public void reset()
  {
    ApplicationManager.getApplication().runReadAction(
      () -> {
        if( _modules == null || _modules.isLoaded() ) // prevent double reset()
        {
          init();
          getFileModificationManager().getManRefresher().nukeFromOrbit();
        }
      } );
  }

  public IjManifoldHost getHost()
  {
    return _host;
  }

  public IjFileSystem getFileSystem()
  {
    return _fs;
  }

  public Project getNativeProject()
  {
    return _ijProject;
  }

  public Map<Module, ManModule> getModules()
  {
    return _modules == null ? null : _modules.get();
  }

  void projectOpened()
  {
    _applicationConnection = ApplicationManager.getApplication().getMessageBus().connect();
    _projectConnection = _ijProject.getMessageBus().connect();
    _permanentProjectConnection = _ijProject.getMessageBus().connect();

    addModuleRefreshListener();
    addModuleClasspathListener();
    addBuildPropertiesFilePersistenceListener();

    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(
      new ManPreprocessorDocumentListener( _ijProject ), _ijProject );

    if( isManifoldInUse() )
    {
      // only listen to type changes if this project is using manifold jars
      addTypeRefreshListener();
    }
  }

  private void addCompilerArgs()
  {
    JpsJavaCompilerOptions javacOptions = JavacConfiguration.getOptions( _ijProject, JavacConfiguration.class );
    String options = javacOptions.ADDITIONAL_OPTIONS_STRING;
    options = options == null ? "" : options;
    if( !options.contains( XPLUGIN_MANIFOLD ) && !options.contains( XPLUGIN_MANIFOLD_WITH_QUOTES ) || options.contains( "Manifold static" ) )
    {
      options = XPLUGIN_MANIFOLD + " " + maybeGetProcessorPath();
    }
    else if( findJdkVersion() >= 9 &&
             ((!options.contains( "-processorpath" ) && !options.contains( "--processor-module-path" )) || !hasCorrectManifoldJars( options )) )
    {
      options = XPLUGIN_MANIFOLD + " " + maybeGetProcessorPath();
    }
    if( findJdkVersion() == 8 || !hasNamedModule() )
    {
      options = options.replace( "--processor-module-path", "-processorpath" );
    }
    else
    {
      options = options.replace( "-processorpath", "--processor-module-path" );
    }
    javacOptions.ADDITIONAL_OPTIONS_STRING = options;
  }

  private void removeCompilerArgs()
  {
    JpsJavaCompilerOptions javacOptions = JavacConfiguration.getOptions( _ijProject, JavacConfiguration.class );
    String options = javacOptions.ADDITIONAL_OPTIONS_STRING;
    if( options == null )
    {
      return;
    }
    int index = options.indexOf( XPLUGIN_MANIFOLD );
    if( index >= 0 )
    {
      StringBuilder sb = new StringBuilder( options );
      sb.delete( index, index + XPLUGIN_MANIFOLD.length() );
      options = sb.toString();
    }
    else
    {
      index = options.indexOf( XPLUGIN_MANIFOLD_WITH_QUOTES );
      if( index >= 0 )
      {
        int end = options.indexOf( '"', index + XPLUGIN_MANIFOLD_WITH_QUOTES.length() );
        if( end > index )
        {
          StringBuilder sb = new StringBuilder( options );
          sb.delete( index, end + 1 );
          options = sb.toString();
        }
        else
        {
          return;
        }
      }
      else
      {
        return;
      }
    }
    int jdkVersion = findJdkVersion();
    options = removeManifoldJarsFromProcessorPath( options,
      jdkVersion == 8 || !hasNamedModule() ? "-processorpath" : "-processor-module-path" );
    javacOptions.ADDITIONAL_OPTIONS_STRING = options;
  }

  private boolean hasCorrectManifoldJars( String options )
  {
    for( String manJarPath: ManLibraryChecker.instance().getManifoldJarsInProject( getNativeProject() ) )
    {
      if( !options.contains( manJarPath ) )
      {
        return false;
      }
    }
    return true;
  }

  private String maybeGetProcessorPath()
  {
    int jdkVersion = findJdkVersion();
    StringBuilder processorPath = new StringBuilder();
    if( jdkVersion >= 9 )
    {
      List<String> manifoldJarsInProject = ManLibraryChecker.instance().getManifoldJarsInProject( getNativeProject() );
      for( String path: manifoldJarsInProject )
      {
        if( processorPath.length() == 0 )
        {
          processorPath.append( " --processor-module-path " );
        }
        else
        {
          processorPath.append( File.pathSeparatorChar );
        }
        processorPath.append( path );
      }
    }
    return processorPath.toString();
  }

  private String removeManifoldJarsFromProcessorPath( String options, String processorPathArg )
  {
    int index = options.indexOf( processorPathArg + " " );
    if( index < 0 )
    {
      // no -processorpath
      return options;
    }

    int iNextArg = options.indexOf( " -", index );
    int iEnd = iNextArg >= 0 ? iNextArg : options.length();
    int iStart = index + processorPathArg.length() + 1;
    String processorPaths = options.substring( iStart, iEnd );
    if( !processorPaths.contains( "manifold" ) )
    {
      // no manifold jars to remove from -processorpath
      return options;
    }

    String[] paths = processorPaths.split( File.pathSeparator );
    String[] nonManifoldPaths = Arrays.stream( paths )
      .filter( path -> !path.contains( "manifold" ) )
      .toArray( String[]::new );
    if( nonManifoldPaths.length == 0 )
    {
      // all the paths in -processorpath are manifold jars, remove the entire -processorpath
      StringBuilder sb = new StringBuilder( options );
      sb.delete( index, iEnd );
      return sb.toString().trim();
    }

    // remove manifold jars from -processorpath
    StringBuilder newOptions = new StringBuilder()
      .append( options, 0, iStart );
    Arrays.stream( nonManifoldPaths )
      .map( String::trim )
      .forEach( path -> newOptions.append( path ).append( File.pathSeparatorChar ) );
    newOptions.append( options, iEnd, options.length() );
    return newOptions.toString().trim();
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
    // 'java version "8"'
    // 'java version "9.0.1"'
    // 'java version "10.0.1"'
    // 'java version "11"'
    // etc.
    String version = projectSdk.getVersionString();
    int iQuote = version == null ? -1 : version.indexOf( '"' );
    if( iQuote < 0 )
    {
      return -1;
    }

    String majorVer = version.substring( iQuote + 1 );
    int iStop = majorVer.indexOf( '.' );
    if( iStop < 0 )
    {
      iStop = majorVer.indexOf( '"' );
    }

    majorVer = majorVer.substring( 0, iStop );
    if( majorVer.equals( "1" ) )
    {
      return 8;
    }
    else
    {
      return Integer.parseInt( majorVer );
    }
  }

  private void addBuildPropertiesFilePersistenceListener()
  {
    _permanentProjectConnection.subscribe( AppTopics.FILE_DOCUMENT_SYNC,
      new BuildPropertiesFilePersistenceListener( _ijProject ) );
  }

  private void addModuleClasspathListener()
  {
    _permanentProjectConnection.subscribe( ProjectTopics.PROJECT_ROOTS,
      new ModuleClasspathListener() );
  }

  void projectClosed()
  {
    _projectConnection.disconnect();
    _projectConnection = null;
    PROJECTS.remove( getNativeProject() );
    if( _fileModificationManager != null )
    {
      _fileModificationManager.getManRefresher().nukeFromOrbit();
    }
  }

  private void addTypeRefreshListener()
  {
    _projectConnection.subscribe( PsiDocumentTransactionListener.TOPIC, getFileModificationManager() );
    _applicationConnection.subscribe( VirtualFileManager.VFS_CHANGES, getFileModificationManager() );
  }

  private void addModuleRefreshListener()
  {
    ModuleRefreshListener moduleRefreshListener = new ModuleRefreshListener();
    _projectConnection.subscribe( ProjectTopics.MODULES, moduleRefreshListener );
  }

  public FileModificationManager getFileModificationManager()
  {
    return _fileModificationManager = _fileModificationManager == null
                                      ? new FileModificationManager( this )
                                      : _fileModificationManager;
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  private boolean hasNamedModule()
  {
    return _hasNamedModule;
  }

  public Set<ManModule> getRootModules()
  {
    return _rootModules.get();
  }

  private Map<Module, ManModule> defineModules()
  {
    ModuleManager moduleManager = ModuleManager.getInstance( _ijProject );
    Module[] allIjModules = moduleManager.getModules();

    // create modules
    Map<Module, ManModule> modules = new HashMap<>();
    Map<Module, ManModule> allModules = new LinkedHashMap<>();
    for( Module ijModule: allIjModules )
    {
      final ManModule module = defineModule( ijModule );
      modules.put( ijModule, module );
      allModules.put( ijModule, module );
      _hasNamedModule = _hasNamedModule || isNamedModule( ijModule );
    }

    // add module dependencies
    for( Module ijModule: allIjModules )
    {
      addModuleDependencies( modules, modules.get( ijModule ) );
    }

    // reduce classpaths
    Set<ManModule> visited = new HashSet<>();
    for( ManModule manModule: allModules.values() )
    {
      manModule.reduceClasspath( visited );
    }

    // finally, initialize the type manifolds for each module
    for( ManModule manModule: allModules.values() )
    {
      manModule.initializeTypeManifolds();
    }

    addCompilerArgs();

    return allModules;
  }

  private void addModuleDependencies( Map<Module, ManModule> modules, ManModule manModule )
  {
    Module ijModule = manModule.getIjModule();
    for( Module child: ModuleRootManager.getInstance( ijModule ).getDependencies() )
    {
      IModule moduleDep = modules.get( child );
      if( moduleDep != null )
      {
        manModule.addDependency( new Dependency( moduleDep, isExported( ijModule, child ) ) );
      }
    }
  }

  private static boolean isExported( Module ijModule, Module child )
  {
    for( OrderEntry entry: ModuleRootManager.getInstance( ijModule ).getOrderEntries() )
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
      sourceFolders.stream().map( this::toDirectory ).collect( Collectors.toCollection( () -> new LinkedHashSet<>() ) ),
      outputPath == null ? null : getFileSystem().getIDirectory( outputPath ) );
  }

  private ManModule createModule( Module ijModule, Set<IDirectory> classpath, Set<IDirectory> sourcePaths, IDirectory outputPath )
  {
    // Expand path to include processorPath (type manifolds can be listed there exclusively)
    classpath = addProcessorPath( ijModule, classpath );

    // Maybe expand paths to include Class-Path attribute from Manifest...
    classpath = addFromManifestClassPath( classpath );
    sourcePaths = addFromManifestClassPath( sourcePaths );

    // Scan....
    List<IDirectory> sourceRoots = new ArrayList<>( sourcePaths );
    scanPaths( classpath, sourceRoots );

    return new ManModule( this, ijModule, new ArrayList<>( classpath ), sourceRoots, Collections.singletonList( outputPath ), getExcludedFolders( ijModule ) );
  }

  private Set<IDirectory> addProcessorPath( Module ijModule, Set<IDirectory> classpath )
  {
    String processorPath = CompilerConfiguration.getInstance( _ijProject ).getAnnotationProcessingConfiguration( ijModule ).getProcessorPath();
    if( !processorPath.isEmpty() )
    {
      for( StringTokenizer tokenizer = new StringTokenizer( processorPath, File.pathSeparator ); tokenizer.hasMoreTokens(); )
      {
        String path = tokenizer.nextToken();
        File file = new File( path );
        if( file.exists() )
        {
          IDirectory dir = getFileSystem().getIDirectory( file );
          classpath.add( dir );
        }
      }
    }
    return classpath;
  }

  private static void scanPaths( Set<IDirectory> paths, List<IDirectory> roots )
  {
    //noinspection Convert2streamapi
    for( IDirectory root: paths )
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
  private Set<IDirectory> addFromManifestClassPath( Set<IDirectory> classpath )
  {
    if( classpath == null )
    {
      return null;
    }

    LinkedHashSet<IDirectory> newClasspath = new LinkedHashSet<>();
    for( IDirectory root: classpath )
    {
      //add the root JAR itself first, preserving ordering
      if( !newClasspath.contains( root ) )
      {
        newClasspath.add( root );
      }
      if( root instanceof JarFileDirectoryImpl )
      {
        JarFile jarFile = ((JarFileDirectoryImpl)root).getJarFile();
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
            for( String j: paths.split( " " ) )
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
              newClasspath.add( idir );
            }
          }
        }
      }
    }

    return newClasspath;
  }

  private List<IDirectory> getExcludedFolders( Module ijModule )
  {
    return getExcludedRoots( ijModule ).stream().map( this::toDirectory ).collect( Collectors.toList() );
  }

  private static List<VirtualFile> getSourceRoots( Module ijModule )
  {
    final ModuleRootManager moduleManager = ModuleRootManager.getInstance( ijModule );
    final List<VirtualFile> sourcePaths = new ArrayList<>();
    List<VirtualFile> excludeRoots = Arrays.asList( moduleManager.getExcludeRoots() );
    for( VirtualFile sourceRoot: moduleManager.getSourceRoots() )
    {
      if( !excludeRoots.contains( sourceRoot ) )
      {
        sourcePaths.add( sourceRoot );
      }
    }

    return sourcePaths;
  }

  private static List<VirtualFile> getExcludedRoots( Module ijModule )
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
      IjFile ijFile = (IjFile)getHost().getFileSystem().getIFile( new URL( url ) );
      file = ijFile.getVirtualFile();
    }
    return getFileSystem().getIDirectory( file );
  }

  private static Set<IDirectory> getInitialClasspaths( Module ijModule )
  {
    List<String> paths = getDirectClassPaths( ijModule );
    Set<IDirectory> dirs = new LinkedHashSet<>();
    for( String path: paths )
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
    for( OrderEntry entry: orderEntries.stream().filter( (LibraryOrderEntry.class)::isInstance ).collect( Collectors.toList() ) )
    {
      final Library lib = ((LibraryOrderEntry)entry).getLibrary();
      if( lib != null )
      {
        for( VirtualFile virtualFile: lib.getFiles( OrderRootType.CLASSES ) )
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

  public ManifoldPsiClassCache getPsiClassCache()
  {
    return _psiClassCache;
  }
}

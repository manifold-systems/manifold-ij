package manifold.ij.core;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import manifold.api.fs.IDirectory;
import manifold.api.fs.IFile;
import manifold.api.fs.IFileSystem;
import manifold.api.host.Dependency;
import manifold.api.type.ITypeManifold;
import manifold.api.type.ResourceFileTypeManifold;
import manifold.api.type.TypeName;
import manifold.ext.IExtensionClassProducer;
import manifold.internal.host.SimpleModule;
import manifold.util.JsonUtil;

/**
 */
public class ManModule extends SimpleModule
{
  private ManProject _manProject;
  private Module _ijModule;
  private List<Dependency> _dependencies;
  private List<IDirectory> _excludedDirs;
  private URLClassLoader _typeManifoldClassLoader;

  ManModule( ManProject manProject, Module ijModule, List<IDirectory> classpath, List<IDirectory> sourcePath, List<IDirectory> outputPath, List<IDirectory> excludedDirs )
  {
    super( classpath, sourcePath, outputPath );
    _ijModule = ijModule;
    _manProject = manProject;
    _excludedDirs = excludedDirs;
    _dependencies = new ArrayList<>();
  }

  @Override
  public String getName()
  {
    return _ijModule.getName();
  }

  public ManProject getProject()
  {
    return _manProject;
  }

  @Override
  public ManModule getModule()
  {
    return this;
  }

  public List<Dependency> getDependencies()
  {
    return _dependencies;
  }
  void addDependency( Dependency dependency )
  {
    _dependencies.add( dependency );
  }

  @Override
  public IDirectory[] getExcludedPath()
  {
    return _excludedDirs.toArray( new IDirectory[0] );
  }

  public Module getIjModule()
  {
    return _ijModule;
  }

  public Project getIjProject()
  {
    return _ijModule.getProject();
  }

  @Override
  public IFileSystem getFileSystem()
  {
    return getProject().getFileSystem();
  }

  @SafeVarargs
  @SuppressWarnings("Duplicates")
  @Override
  public final Set<ITypeManifold> findTypeManifoldsFor( String fqn, Predicate<ITypeManifold>... predicates )
  {
    return findTypeManifoldsFor( fqn, predicates, this );
  }
  private Set<ITypeManifold> findTypeManifoldsFor( String fqn, Predicate<ITypeManifold>[] predicates, ManModule root )
  {
    Set<ITypeManifold> sps = super.findTypeManifoldsFor( fqn, predicates );
    if( !sps.isEmpty() )
    {
      return sps;
    }
    sps = new HashSet<>();
    for( Dependency d : getDependencies() )
    {
      if( this == root || d.isExported() )
      {
        sps.addAll( ((ManModule)d.getModule()).findTypeManifoldsFor( fqn, predicates, root ) );
      }
    }
    return sps;
  }

  @SuppressWarnings("Duplicates")
  @Override
  public Set<ITypeManifold> findTypeManifoldsFor( IFile file )
  {
    return findTypeManifoldsFor( file, this );
  }
  private Set<ITypeManifold> findTypeManifoldsFor( IFile file, ManModule root )
  {
    Set<ITypeManifold> sps = super.findTypeManifoldsFor( file );
    if( !sps.isEmpty() )
    {
      return sps;
    }
    sps = new HashSet<>();
    for( Dependency d : getDependencies() )
    {
      if( this == root || d.isExported() )
      {
        sps.addAll( ((ManModule)d.getModule()).findTypeManifoldsFor( file, root ) );
      }
    }
    return sps;
  }

  @Override
  public Set<TypeName> getChildrenOfNamespace( String packageName )
  {
    Set<TypeName> all = new HashSet<>();
    Set<TypeName> children = super.getChildrenOfNamespace( packageName );
    if( children != null )
    {
      all.addAll( children );
    }
    for( Dependency d : getDependencies() )
    {
      children = ((ManModule)d.getModule()).getChildrenOfNamespace( packageName );
      if( children != null )
      {
        all.addAll( children );
      }
    }
    return all;
  }

  /**
   * Override to add the source providers that may be in the Module's classpath.
   * Note we create a classloader per module exclusively to load source providers
   * from the module's classpath.
   *
   * @see #initializeModuleClassLoader()
   */
  @Override
  public void loadRegistered( Set<ITypeManifold> sps )
  {
    initializeModuleClassLoader();
    ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
    if( _typeManifoldClassLoader != null )
    {
      Thread.currentThread().setContextClassLoader( _typeManifoldClassLoader );
    }
    try
    {
      super.loadRegistered( sps );
    }
    finally
    {
      Thread.currentThread().setContextClassLoader( oldLoader );
    }
  }

  private void initializeModuleClassLoader()
  {
    List<IDirectory> classpath = getCollectiveJavaClassPath();
    if( classpath == null || classpath.isEmpty() )
    {
      return;
    }

    URL[] urls = classpath.stream().map(
      dir ->
      {
        try
        {
          return dir.toURI().toURL();
        }
        catch( MalformedURLException e )
        {
          throw new RuntimeException( e );
        }
      } ).toArray( URL[]::new );

    // note this classloader is used exclusively for finding a loading type manifold services
    _typeManifoldClassLoader = new URLClassLoader( urls, getClass().getClassLoader() );
  }

  @Override
  public List<IDirectory> getCollectiveSourcePath()
  {
    List<IDirectory> all = new ArrayList<>( getSourcePath() );

    for( Dependency d : getDependencies() )
    {
      if( d.isExported() )
      {
        all.addAll( d.getModule().getSourcePath() );
      }
    }
    return all;
  }

  @Override
  public List<IDirectory> getCollectiveJavaClassPath()
  {
    return getCollectiveJavaClassPath( this );
  }
  private List<IDirectory> getCollectiveJavaClassPath( ManModule root )
  {
    List<IDirectory> all = new ArrayList<>( getJavaClassPath() );

    for( Dependency d : getDependencies() )
    {
      if( this == root || d.isExported() )
      {
        all.addAll( ((ManModule)d.getModule()).getCollectiveJavaClassPath( root ) );
      }
    }
    return all;
  }

  public String[] getTypesForFile( IFile file )
  {
    Set<String> result = new LinkedHashSet<>();
    addFromPath( file, result );
    for( ITypeManifold sp : getTypeManifolds() )
    {
      result.addAll( Arrays.asList( sp.getTypesForFile( file ) ) );

      if( sp instanceof IExtensionClassProducer )
      {
        result.addAll( ((IExtensionClassProducer)sp).getExtendedTypesForFile( file ) );
      }
    }
    return result.toArray( new String[0] );
  }

  // Add names from path and current file name.  This is essential for cases
  // like rename file/type where the cached name associated with the file is
  // mapped to the old name, hence the raw processing here.
  private void addFromPath( IFile file, Set<String> result )
  {
    List<IDirectory> sourcePath = getSourcePath();
    outer:
    for( IDirectory src : sourcePath )
    {
      if( file.isDescendantOf( src ) )
      {
        String fqn = src.relativePath( file.getParent() );
        String baseName = JsonUtil.makeIdentifier( file.getBaseName() );
        fqn = fqn.length() == 0 ? baseName : fqn.replace( '/', '.' ) + '.' + baseName;
        for( ITypeManifold sp : getTypeManifolds() )
        {
          if( sp instanceof ResourceFileTypeManifold && sp.handlesFile( file ) )
          {
            fqn = ((ResourceFileTypeManifold)sp).getTypeNameForFile( fqn, file );
            if( fqn != null )
            {
              result.add( fqn );
            }
            break outer;
          }
        }
        result.add( fqn );
      }
    }
  }

  /** reduce redundancy, remove paths that exist in dependencies */
  void reduceClasspath( Set<ManModule> visited )
  {
    if( visited.contains( this ) )
    {
      return;
    }
    visited.add( this );

    List<IDirectory> classpath = getJavaClassPath();
    for( Dependency dep: getDependencies() )
    {
      ManModule depMod = (ManModule)dep.getModule();
      depMod.reduceClasspath( visited );
      classpath.removeIf( depMod::hasPath );
    }

    setJavaClassPath( classpath );
  }

  public boolean hasDependency( ManModule m )
  {
    for( Dependency d: getDependencies() )
    {
      if( d.getModule() == m || ((ManModule)d.getModule()).hasDependency( m ) )
      {
        return true;
      }
    }
    return false;
  }

  private boolean hasPath( IDirectory directory )
  {
    List<IDirectory> classpath = getJavaClassPath();
    if( classpath.contains( directory ) )
    {
      return true;
    }

    for( Dependency dep: getDependencies() )
    {
      if( dep.isExported() )
      {
        ManModule depMod = (ManModule)dep.getModule();
        if( depMod.hasPath( directory ) )
        {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public String toString()
  {
    return getName();
  }
}

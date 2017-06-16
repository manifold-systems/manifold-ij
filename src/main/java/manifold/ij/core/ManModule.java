package manifold.ij.core;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import manifold.api.fs.IDirectory;
import manifold.api.fs.IFile;
import manifold.api.fs.IFileSystem;
import manifold.api.host.Dependency;
import manifold.api.sourceprod.ISourceProducer;
import manifold.api.sourceprod.TypeName;
import manifold.internal.host.SimpleModule;

/**
 */
public class ManModule extends SimpleModule
{
  private ManProject _manProject;
  private Module _ijModule;
  private List<Dependency> _dependencies;
  private List<IDirectory> _excludedDirs;
  private URLClassLoader _sourceProducerClassLoader;

  ManModule( ManProject manProject, Module ijModule, List<IDirectory> classpath, List<IDirectory> sourcePath, IDirectory outputPath, List<IDirectory> excludedDirs )
  {
    super( classpath, sourcePath, outputPath );
    _ijModule = ijModule;
    _manProject = manProject;
    _excludedDirs = excludedDirs;
    _dependencies = new ArrayList<>();

    // Indirectly calls our addRegistered() override, which creates a class loader witih this module's classpath
    initializeSourceProducers();
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
    return _excludedDirs.toArray( new IDirectory[_excludedDirs.size()] );
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

  @SuppressWarnings("Duplicates")
  @Override
  public Set<ISourceProducer> findSourceProducersFor( String fqn )
  {
    Set<ISourceProducer> sps = super.findSourceProducersFor( fqn );
    if( !sps.isEmpty() )
    {
      return sps;
    }
    sps = new HashSet<>();
    for( Dependency d : getDependencies() )
    {
      if( d.isExported() )
      {
        sps.addAll( d.getModule().findSourceProducersFor( fqn ) );
      }
    }
    return sps;
  }

  @SuppressWarnings("Duplicates")
  @Override
  public Set<ISourceProducer> findSourceProducersFor( IFile file )
  {
    Set<ISourceProducer> sps = super.findSourceProducersFor( file );
    if( !sps.isEmpty() )
    {
      return sps;
    }
    sps = new HashSet<>();
    for( Dependency d : getDependencies() )
    {
      if( d.isExported() )
      {
        sps.addAll( d.getModule().findSourceProducersFor( file ) );
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
  protected void addRegistered( Set<ISourceProducer> sps )
  {
    initializeModuleClassLoader();
    ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
    if( _sourceProducerClassLoader != null )
    {
      Thread.currentThread().setContextClassLoader( _sourceProducerClassLoader );
    }
    try
    {
      super.addRegistered( sps );
    }
    finally
    {
      Thread.currentThread().setContextClassLoader( oldLoader );
    }
  }

  private void initializeModuleClassLoader()
  {
    List<IDirectory> classpath = getJavaClassPath();
    if( classpath == null || classpath.isEmpty() )
    {
      return;
    }

    URL[] urls = classpath.stream().map(
      dir -> {
        try
        {
          return dir.toURI().toURL();
        }
        catch( MalformedURLException e )
        {
          throw new RuntimeException( e );
        }
      } ).toArray( URL[]::new );
    _sourceProducerClassLoader = new URLClassLoader( urls, getClass().getClassLoader() );
  }

  @Override
  public Set<ISourceProducer> getSourceProducers()
  {
    Set<ISourceProducer> all = new HashSet<>();
    Set<ISourceProducer> sourceProducers = super.getSourceProducers();
    if( sourceProducers != null )
    {
      all.addAll( sourceProducers );
    }

    for( Dependency dep : getDependencies() )
    {
      if( dep.isExported() )
      {
        sourceProducers = dep.getModule().getSourceProducers();
        if( sourceProducers != null )
        {
          all.addAll( sourceProducers );
        }
      }
    }
    return all;
  }

  @Override
  public List<IDirectory> getCollectiveSourcePath()
  {
    List<IDirectory> all = new ArrayList<>();
    all.addAll( getSourcePath() );

    for( Dependency d : getDependencies() )
    {
      if( d.isExported() )
      {
        all.addAll( d.getModule().getSourcePath() );
      }
    }
    return all;
  }

  public String[] getTypesForFile( IFile file )
  {
    Set<String> result = new HashSet<>();
    List<IDirectory> sourcePath = getSourcePath();
    for( IDirectory src : sourcePath )
    {
      if( file.isDescendantOf( src ) )
      {
        String fqn = src.relativePath( file );
        int iDot = fqn.lastIndexOf( '.' );
        fqn = fqn.substring( 0, iDot > 0 ? iDot : fqn.length() ).replace( '/', '.' );
        result.add( fqn );
      }
    }
    for( ISourceProducer sp : getSourceProducers() )
    {
      Arrays.stream( sp.getTypesForFile( file ) ).forEach( result::add );
    }
    return result.toArray( new String[result.size()] );
  }
}

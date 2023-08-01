/*
 *
 *  * Copyright (c) 2022 - Manifold Systems LLC
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package manifold.ij.core;

import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import manifold.api.fs.IDirectory;
import manifold.api.fs.IFile;
import manifold.api.fs.IFileSystem;
import manifold.api.host.Dependency;
import manifold.api.type.ITypeManifold;
import manifold.api.type.ResourceFileTypeManifold;
import manifold.api.type.TypeName;
import manifold.rt.api.util.ManIdentifierUtil;
import manifold.exceptions.CheckedExceptionSuppressor;
import manifold.ext.IExtensionClassProducer;
import manifold.ij.fs.IjFile;
import manifold.internal.host.SimpleModule;
import manifold.strings.StringLiteralTemplateProcessor;
import manifold.util.NecessaryEvilUtil;
import manifold.util.ReflectUtil;
import manifold.util.concurrent.LocklessLazyVar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions;

/**
 */
public class ManModule extends SimpleModule
{
  private final ManProject _manProject;
  private final Module _ijModule;
  private final List<Dependency> _dependencies;
  private final List<IDirectory> _excludedDirs;
  private URLClassLoader _typeManifoldClassLoader;
  private final LocklessLazyVar<List<ManModule>> _modulesDependingOnMe;
  private final LocklessLazyVar<Boolean> _isExtEnabled;
  private final LocklessLazyVar<Boolean> _isStringsEnabled;
  private final LocklessLazyVar<Boolean> _isExceptionsEnabled;
  private final LocklessLazyVar<Boolean> _isPreprocessorEnabled;
  private final LocklessLazyVar<Boolean> _isPropertiesEnabled;
  private final LocklessLazyVar<Boolean> _isDelegationEnabled;
  private final LocklessLazyVar<Boolean> _isTuplesEnabled;

  ManModule( ManProject manProject, Module ijModule, List<IDirectory> classpath, List<IDirectory> sourcePath, List<IDirectory> outputPath, List<IDirectory> excludedDirs )
  {
    super( manProject.getHost(), classpath, sourcePath, outputPath );
    _ijModule = ijModule;
    _manProject = manProject;
    _excludedDirs = excludedDirs;
    _dependencies = new ArrayList<>();
    _modulesDependingOnMe = LocklessLazyVar.make(
      () -> {
        LinkedHashSet<Module> result = new LinkedHashSet<>();
        ModuleUtilCore.collectModulesDependsOn( getIjModule(), result );
        return result.stream().map( ManProject::getModule ).collect( Collectors.toList() );
      } );
    _isExtEnabled = LocklessLazyVar.make( () -> hasJar( "manifold-ext-rt" ) || hasJar( "manifold-all" ) );
    _isStringsEnabled = LocklessLazyVar.make( () -> hasJar( "manifold-strings" ) || hasJar( "manifold-all" ) );
    _isExceptionsEnabled = LocklessLazyVar.make( () -> hasJar( "manifold-exceptions" ) || hasJar( "manifold-all" ) );
    _isPreprocessorEnabled = LocklessLazyVar.make( () -> hasJar( "manifold-preprocessor" ) || hasJar( "manifold-all" ) );
    _isPropertiesEnabled = LocklessLazyVar.make( () -> hasJar( "manifold-props" ) || hasJar( "manifold-all" ) );
    _isDelegationEnabled = LocklessLazyVar.make( () -> hasJar( "manifold-delegation" ) || hasJar( "manifold-all" ) );
    _isTuplesEnabled = LocklessLazyVar.make( () -> hasJar( "manifold-tuple" ) || hasJar( "manifold-all" ) );
  }

  private boolean hasJar( String jarName )
  {
    return _typeManifoldClassLoader != null &&
      Arrays.stream( _typeManifoldClassLoader.getURLs() ).anyMatch( url -> url.toString().contains( jarName ) );
  }

  public void runWithLoader( Runnable code )
  {
    runWithLoader( () -> {code.run(); return null;});
  }
  public <R> R runWithLoader( Callable<R> code )
  {
    ClassLoader prior = Thread.currentThread().getContextClassLoader();
    if( _typeManifoldClassLoader != null )
    {
      Thread.currentThread().setContextClassLoader( _typeManifoldClassLoader );
    }
    try
    {
      return code.call();
    }
    finally
    {
      Thread.currentThread().setContextClassLoader( prior );
    }
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

  public final Set<ITypeManifold> super_findTypeManifoldsFor( String fqn, Predicate<ITypeManifold> predicate )
  {
    return super.findTypeManifoldsFor( fqn, predicate );
  }

  //## hack: exclude the Gosu type manifold to prevent interference with Studio's Gosu support, and also because Gosu
  //## would need to mirror the modules etc. which would be a PITA and not useful since the Gosu plugin for IJ already
  //## does everything we need.
  @Override
  public List<String> getExcludedTypeManifolds()
  {
    List<String> excludedTypeManifolds = new ArrayList<>( super.getExcludedTypeManifolds() );
    excludedTypeManifolds.add( "gw.lang.init.GosuTypeManifold" );
    return excludedTypeManifolds;
  }

  /**
   * Find type manifolds for an FQN starting with this module and then searching its dependencies (search *down*).
   * Note searching for type manifolds for an FQN amounts to *resolving* the FQN from the context of THIS module.
   * Say we are in an editor trying to resolve "abc.Foo".  If the resource[s] comprising "abc.Foo" live in this
   * module, the type manifolds for this module resolve it.  Otherwise, we search dependencies because this module
   * has access to resources in its dependencies.
   *
   * @param fqn The FQN to resolve in terms of the type manifolds that build the corresponding type
   * @param predicate A predicate that filters the type manifolds returned
   * @return The set of type manifolds responsible for producing the type corresponding with FQN
   */
  @Override
  public final Set<ITypeManifold> findTypeManifoldsFor( String fqn, Predicate<ITypeManifold> predicate )
  {
    return findTypeManifoldsFor( fqn, predicate, this, new HashSet<>() );
  }
  private Set<ITypeManifold> findTypeManifoldsFor( String fqn, Predicate<ITypeManifold> predicate, ManModule root, HashSet<ManModule> visited )
  {
    if( visited.contains( this ) )
    {
      // prevent cycles (prohibited in IJ?)
      return Collections.emptySet();
    }
    visited.add( this );

    Set<ITypeManifold> sps = super.findTypeManifoldsFor( fqn, predicate );
    if( !sps.isEmpty() )
    {
      return sps;
    }
    sps = new HashSet<>();
    for( Dependency d: getDependencies() )
    {
      if( this == root || d.isExported() )
      {
        sps.addAll( ((ManModule)d.getModule()).findTypeManifoldsFor( fqn, predicate, root, visited ) );
      }
    }
    return sps;
  }

  /**
   * Search for the type manifolds that use {@code file} as the basis for type[s].  Note searching for type manifolds
   * for a file amounts to first finding the module that contains {@code file} and the transitive set of modules that
   * depend *on* the containing module, in other words search *up*.  This is because only modules that have access to
   * {@code file} can have type manifolds for it.  This is the inverse of searching for an FQN.
   *
   * @param project The project to which the search is limited
   * @param file The file to find type manifolds for
   * @param include An optional predicate controlling the type manifolds returned. Note a type manifold is included in
   *                the search results only if it passes {@code include}'s test and then also passes {@link ITypeManifold#handlesFile(IFile)}
   * @param terminate An optional predicate to determine whether or not the search should terminate
   * @return The set of type manifolds responsible for resolving the type corresponding with {@code file}
   */
  public static @NotNull Set<ITypeManifold> findTypeManifoldsForFile( Project project, IFile file,
                                                                      Predicate<ITypeManifold> include,
                                                                      Predicate<ITypeManifold> terminate)
  {
    Set<ITypeManifold> result = Collections.emptySet();
    Module moduleForFile = ModuleUtilCore.findModuleForFile( ((IjFile)file.getPhysicalFile()).getVirtualFile(), project );
    Collection<ManModule> modules;
    if( moduleForFile == null )
    {
      modules = ManProject.manProjectFrom( project ).getModules().values();
    }
    else
    {
      ManModule module = ManProject.getModule( moduleForFile );
      if( module != null )
      {
        modules = module._modulesDependingOnMe.get();
      }
      else
      {
        return result;
      }
    }

    for( ManModule m: modules )
    {
      Set<ITypeManifold> res = m.findTypeManifoldsFor( file, include );
      if( !res.isEmpty() )
      {
        if( result.isEmpty() )
        {
          result = new HashSet<>();
        }
        result.addAll( res );
        if( res.stream().anyMatch( tm -> terminate != null && terminate.test( tm ) ) )
        {
          break;
        }
      }
    }
    return result;
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
   * Override to add the type manifolds that may be in the Module's classpath.
   * Note we create a classloader per module exclusively to load type manifolds
   * from the module's classpath.
   *
   * @see #initializeModuleClassLoader()
   */
  @Override
  public void loadRegistered( Set<ITypeManifold> sps )
  {
    initializeModuleClassLoader();
    NecessaryEvilUtil.bypassJava9Security( true );
    runWithLoader( () -> super.loadRegistered( sps ) );
  }

  private void initializeModuleClassLoader()
  {
    List<IDirectory> classpath = getCollectiveJavaClassPath();
    if( classpath == null || classpath.isEmpty() )
    {
      return;
    }

    URL[] urls = classpath.stream().map( dir -> dir.toURI().toURL() ).toArray( URL[]::new );

    // note this classloader is used exclusively for finding and loading type manifold services
    _typeManifoldClassLoader =
      new URLClassLoader( urls, getClass().getClassLoader() )
      {
        /**
         * Total hack to avoid Jar-hell with IJ's PathClassLoader, a parent loader in the chain. For example, if a project
         * uses manifold-sql with H2, those jars must load in this URLClassLoader so manifold-sql can do its thing. However,
         * since IJ apparently uses part of H2 internally, its PathClassLoader, being a parent loader of this loader, will
         * load H2 classes, which are probably not from the same version of H2, etc. Therefore, overloading loadClass()
         * here for non-manifold classes ensures that ij's loader won't interfere with dependencies of manifold classes.
         */
        @Override
        protected Class<?> loadClass( String name, boolean resolve ) throws ClassNotFoundException
        {
          ClassLoader parent = null;
          try
          {
            if( !name.startsWith( "manifold." ) ) // if( name.startsWith( "org.h2." ) )
            {
              // jump over PathClassLoader to the great-grandfather since none of these classes should have a dependency on IJ classes
              parent = (ClassLoader)ReflectUtil.field( this, "parent" ).get();
              ReflectUtil.field( this, "parent" ).set( ClassLoader.getPlatformClassLoader() );
            }
            return super.loadClass( name, resolve );
          }
          finally
          {
            if( !name.startsWith( "manifold." ) )
            {
              ReflectUtil.field( this, "parent" ).set( parent );
            }
          }
        }
      };
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
    return getCollectiveJavaClassPath( this, new HashSet<>() );
  }
  private List<IDirectory> getCollectiveJavaClassPath( ManModule root, Set<ManModule> visited )
  {
    if( visited.contains( this ) )
    {
      return Collections.emptyList();
    }
    visited.add( this );

    List<IDirectory> all = new ArrayList<>( getJavaClassPath() );

    for( Dependency d: getDependencies() )
    {
      if( (this == root || d.isExported()) && d.getModule() != this )
      {
        all.addAll( ((ManModule)d.getModule()).getCollectiveJavaClassPath( root, visited ) );
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
  public void addFromPath( IFile file, Set<String> result )
  {
    List<IDirectory> sourcePath = getSourcePath();
    outer:
    for( IDirectory src : sourcePath )
    {
      if( file.isDescendantOf( src ) )
      {
        String fqn = src.relativePath( file.getParent() );
        String baseName = ManIdentifierUtil.makeIdentifier( file.getBaseName() );
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

  public boolean isExtEnabled()
  {
    return _isExtEnabled.get();
  }

  public boolean isStringsEnabled()
  {
    return _isStringsEnabled.get();
  }

  public boolean isExceptionsEnabled()
  {
    return _isExceptionsEnabled.get();
  }

  public boolean isPreprocessorEnabled()
  {
    return _isPreprocessorEnabled.get();
  }

  public boolean isPropertiesEnabled()
  {
    return _isPropertiesEnabled.get();
  }

  public boolean isDelegationEnabled()
  {
    return _isDelegationEnabled.get();
  }

  public boolean isTuplesEnabled()
  {
    return _isTuplesEnabled.get();
  }

  public boolean isPluginArgEnabled( String pluginArg )
  {
    // Module-level args override project-level args
    
    JpsJavaCompilerOptions options = JavacConfiguration.getOptions( getIjProject(), JavacConfiguration.class );
    Map<String, String> optionsOverride = options.ADDITIONAL_OPTIONS_OVERRIDE;
    if( optionsOverride != null )
    {
      for( String moduleName: optionsOverride.keySet() )
      {
        if( getName().equals( moduleName ) )
        {
          return hasPluginArg( optionsOverride.get( moduleName ), pluginArg );
        }
      }
    }

    // Project-level args

    return hasPluginArg( options.ADDITIONAL_OPTIONS_STRING, pluginArg );
  }

  private boolean hasPluginArg( String optionsString, String pluginArg )
  {
    if( optionsString == null )
    {
      return false;
    }

    String pluginArgPrefix;
    int index = optionsString.indexOf( pluginArgPrefix = ManProject.XPLUGIN_MANIFOLD );
    if( index < 0 )
    {
      index = optionsString.indexOf( pluginArgPrefix = "-Xplugin:\"Manifold" );
    }
    if( index >= 0 )
    {
      StringTokenizer tokenizer = new StringTokenizer( optionsString.substring( pluginArgPrefix.length() ), " \"" );
      while( tokenizer.hasMoreTokens() )
      {
        String token = tokenizer.nextToken();
        if( token.equals( pluginArg ) )
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

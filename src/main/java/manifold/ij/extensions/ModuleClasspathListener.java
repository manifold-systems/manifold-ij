/*
 * Manifold
 */

package manifold.ij.extensions;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ModuleRootManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import manifold.api.fs.IDirectory;
import manifold.api.host.Dependency;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.util.FP64;

public class ModuleClasspathListener implements ModuleRootListener
{
  private static final Logger LOG = Logger.getInstance( ModuleClasspathListener.class );

  public static boolean ENABLED = true;
  public static final String ARROW = " <- ";
  public static final String EXPORT = "^, ";
  public static final String NOT_EXPORT = ", ";

  @Override
  public void beforeRootsChange( ModuleRootEvent event )
  {
  }

  @Override
  public void rootsChanged( ModuleRootEvent event )
  {
    Project project = (Project)event.getSource();
    if( !shouldProcessRootChanges( project ) )
    {
      return;
    }
    boolean processDependencies = true;

    if( processDependencies )
    {
      processModuleDependenciesChange( project );
    }

    final Module[] modules = ModuleManager.getInstance( project ).getModules();
    for( Module ijModule : modules )
    {
      ManModule manModule = ManProject.getModule( ijModule );
      processClasspathChange( manModule, ijModule );
      changeSourceRoots( manModule );
    }
  }

  private boolean shouldProcessRootChanges( Project project )
  {
    return ENABLED;
  }

  private void processClasspathChange( ManModule manModule, Module ijModule )
  {
    List<IDirectory> ijClasspath = manModule.getJavaClassPath();
    List<IDirectory> ijSources = manModule.getSourcePath();
    List<IDirectory> gosuClasspath = manModule.getJavaClassPath();
    if( areDifferentIgnoringOrder( ijClasspath, gosuClasspath ) )
    {
      changeClasspath( manModule, ijClasspath, ijSources );
    }
  }


  private boolean areDifferentIgnoringOrder( List<IDirectory> list1, List<IDirectory> list2 )
  {
    if( list1.size() != list2.size() )
    {
      return true;
    }
    List<IDirectory> list1copy = new ArrayList<>( list1 );
    list1copy.removeAll( list2 );
    return list1copy.size() != 0;
  }

  private void processModuleDependenciesChange( Project project )
  {
    FP64 ijDependencyFingerprint = computeIJDependencyFingerprint( project );
    FP64 gosuDependencyFingerprint = computeGosuDependencyFingerprint( project );
    if( !ijDependencyFingerprint.equals( gosuDependencyFingerprint ) )
    {
      changeDependencies( project );
    }
  }

  private FP64 computeGosuDependencyFingerprint( Project project )
  {
    List<String> strings = new ArrayList<>();
    for( ManModule module: ManProject.manProjectFrom( project ).getModules() )
    {
      String s = module.getName() + ARROW;
      for( Dependency dependency : module.getDependencies() )
      {
        ManModule child = (ManModule)dependency.getModule();
        s += child.getName() + (dependency.isExported() ? EXPORT : NOT_EXPORT);
      }
      strings.add( s );
    }
    return computeOrderIndependentFingerprint( strings );
  }

  private FP64 computeIJDependencyFingerprint( Project project )
  {
    List<String> strings = new ArrayList<>();
    Module[] ijModules = ModuleManager.getInstance( project ).getModules();
    for( Module ijModule : ijModules )
    {
      String s = ijModule.getName() + ARROW;
      for( Module child : ModuleRootManager.getInstance( ijModule ).getDependencies() )
      {
        s += child.getName() + (ManProject.isExported( ijModule, child ) ? EXPORT : NOT_EXPORT);
      }
      strings.add( s );
    }
    return computeOrderIndependentFingerprint( strings );
  }

  private FP64 computeOrderIndependentFingerprint( List<String> strings )
  {
    Collections.sort( strings );

    final FP64 fp = new FP64();
    for( String s : strings )
    {
      fp.extend( s );
    }
    return fp;
  }

  // ================================ private part

  private void changeDependencies( Project project )
  {
    ManProject.manProjectFrom( project ).reset();
  }

  private void changeSourceRoots( ManModule manModule )
  {
    if( !manModule.getIjModule().getProject().isInitialized() )
    {
      return;
    }

    manModule.getProject().reset();
  }

  private void changeClasspath( ManModule manModule, List<IDirectory> classpath, List<IDirectory> sources )
  {
    manModule.getProject().reset();
  }
}

package manifold.ij.core;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.Set;
import manifold.api.host.IModule;
import manifold.api.host.ITypeLoaderListener;
import manifold.api.sourceprod.TypeName;
import manifold.internal.host.DefaultManifoldHost;

/**
 * Override the default behavior to support listening to IJ's file system and type system
 */
public class IjManifoldHost extends DefaultManifoldHost
{
  @Override
  public void addTypeLoaderListenerAsWeakRef( Object ctx, ITypeLoaderListener listener )
  {
    if( ctx == null )
    {
      addToAllProjectsWithWarning( listener );
    }
    else
    {
      ManProject manProject = getManProject( ctx );
      manProject.getFileModificationManager().getManRefresher().addTypeLoaderListenerAsWeakRef( listener );
    }
  }

    /**
     * Called indirectly when we compile a manifold resource file during a debug session for HotSwap.
     * Kind of hacky with the ThreadLocal...
     */
    private static ThreadLocal<ManModule> Module = new ThreadLocal<>();
    @Override
    public Set<TypeName> getChildrenOfNamespace( String packageName )
    {
      return getModule().getChildrenOfNamespace( packageName );
    }
    @Override
    public IModule getCurrentModule()
    {
      return getModule();
    }
    @Override
    public IModule getGlobalModule()
    {
      return getModule().getProject().findRootModules().get( 0 );
    }
    public static void setModule( ManModule module )
    {
      Module.set( module );
    }
    public static ManModule getModule()
    {
      return Module.get();
    }

  private void addToAllProjectsWithWarning( ITypeLoaderListener listener )
  {
    System.out.println( "Warning: Null ctx, adding listener to ALL projects." );
    for( ManProject p: ManProject.getAllProjects() )
    {
      p.getFileModificationManager().getManRefresher().addTypeLoaderListenerAsWeakRef( listener );
    }
  }

  private ManProject getManProject( Object ctx )
  {
    ManProject manProject;
    if( ctx instanceof Project )
    {
      manProject = ManProject.manProjectFrom( (Project)ctx );
    }
    else if( ctx instanceof Module )
    {
      manProject = ManProject.manProjectFrom( (Module)ctx );
    }
    else if( ctx instanceof ManModule )
    {
      manProject = ((ManModule)ctx).getProject();
    }
    else if( ctx instanceof ManProject )
    {
      manProject = (ManProject)ctx;
    }
    else
    {
      throw new IllegalArgumentException( "Context is invalid: " + ctx );
    }
    return manProject;
  }
}

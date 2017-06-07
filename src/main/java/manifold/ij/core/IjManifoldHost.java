package manifold.ij.core;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import manifold.api.host.ITypeLoaderListener;
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

/*
 * Manifold
 */

package manifold.ij.extensions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import manifold.ij.core.ManProject;

public class ModuleClasspathListener implements ModuleRootListener
{
  @Override
  public void beforeRootsChange( ModuleRootEvent event )
  {
  }

  @Override
  public void rootsChanged( ModuleRootEvent event )
  {
    Project project = (Project)event.getSource();
    resetProject( project );
  }

  private void resetProject( Project project )
  {
    if( !project.isInitialized() )
    {
      return;
    }

    ManProject.manProjectFrom( project ).reset();
  }
}

/*
 * Manifold
 */

package manifold.ij.extensions;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;

import java.util.List;
import manifold.ij.core.ManProject;
import org.jetbrains.annotations.NotNull;

public class ModuleRefreshListener implements ModuleListener
{
  public void moduleAdded( Project project, Module ijModule )
  {
    ManProject.manProjectFrom( project ).reset();
  }

  public void beforeModuleRemoved( Project project, Module ijModule )
  {
    ManProject.manProjectFrom( project ).reset();
  }

  public void moduleRemoved( Project project, Module module )
  {
    ManProject.manProjectFrom( project ).reset();
  }

  @Override
  public void modulesRenamed( Project project, List<Module> modules, Function<Module, String> moduleStringFunction )
  {
    ManProject.manProjectFrom( project ).reset();
  }
}

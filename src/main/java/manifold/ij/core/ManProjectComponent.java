/*
 * Manifold
 */

package manifold.ij.core;

import com.intellij.codeInsight.daemon.impl.EditorTracker;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;

public class ManProjectComponent implements ProjectComponent
{
  public static final PluginId MANIFOLD_PLUGIN_ID = PluginId.getId( "manifold-systems.manifold" );
  private final Project _project;

  protected ManProjectComponent( Project project, EditorTracker editorTracker, EditorColorsManager colorsManager )
  {
    _project = project;
  }


  @Override
  public void projectOpened()
  {
//    StartupManagerImpl.getInstance( _project ).registerStartupActivity(
//      () -> ApplicationManager.getApplication().invokeLater(
//        () -> ApplicationManager.getApplication().runWriteAction(
//          () -> ManProject.manProjectFrom( _project ).projectOpened() ) ) );

    StartupManagerImpl.getInstance( _project ).registerStartupActivity( () ->
      ApplicationManager.getApplication().runReadAction( () -> ManProject.manProjectFrom( _project ).projectOpened() ) );
  }

  @Override
  public void projectClosed()
  {
    ManProject.manProjectFrom( _project ).projectClosed();
  }

  @Override
  public void initComponent()
  {
  }

  @Override
  public void disposeComponent()
  {
  }

  @Override
  public String getComponentName()
  {
    return "Manifold Project Component";
  }
}

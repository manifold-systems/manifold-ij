/*
 * Manifold
 */

package manifold.ij.core;

import com.intellij.codeInsight.daemon.impl.EditorTracker;
import com.intellij.codeInsight.highlighting.PairedBraceMatcherAdapter;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.LanguageBraceMatching;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import manifold.ij.extensions.ManifoldPsiClassAnnotator;
import manifold.ij.template.ManTemplateBraceMatcher;
import manifold.ij.template.ManTemplateLanguage;

public class ManProjectComponent implements ProjectComponent
{
  private static volatile boolean Initialized = false;

  public static final PluginId MANIFOLD_PLUGIN_ID = PluginId.getId( "manifold-systems.manifold" );
  private final Project _project;

  protected ManProjectComponent( Project project, EditorTracker editorTracker, EditorColorsManager colorsManager )
  {
    _project = project;
  }


  @Override
  public void projectOpened()
  {
    StartupManagerImpl.getInstance( _project ).registerStartupActivity( () ->
      ApplicationManager.getApplication().runReadAction( () -> ManProject.manProjectFrom( _project ).projectOpened() ) );
  }

  @Override
  public void projectClosed()
  {
    ManProject manProject = ManProject.manProjectFrom( _project );
    if( manProject != null )
    {
      manProject.projectClosed();
    }
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

  @Override
  public void initComponent()
  {
    if( !Initialized )
    {
      synchronized( this )
      {
        if( !Initialized )
        {
          Initialized = true;

          // All this stuff is at the "Application" scope, but we have to initialize the first time a Project loads
          StartupManagerImpl.getInstance( _project ).registerStartupActivity( () -> {
            registerAnnotatorWithAllLanguages();
          } );
        }
      }
    }
  }

  private void registerAnnotatorWithAllLanguages()
  {
    // effectively adds annotator to ALL languages
    LanguageAnnotators.INSTANCE.addExplicitExtension( Language.ANY, new ManifoldPsiClassAnnotator() );

    // add brace matcher to templates
    LanguageBraceMatching.INSTANCE.addExplicitExtension( ManTemplateLanguage.INSTANCE,
      new PairedBraceMatcherAdapter( new ManTemplateBraceMatcher(), ManTemplateLanguage.INSTANCE ) );
  }
}

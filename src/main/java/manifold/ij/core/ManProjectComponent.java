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
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.psi.impl.java.stubs.JavaLiteralExpressionElementType;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import java.util.function.Supplier;
import manifold.ij.extensions.ManJavaLiteralExpressionElementType;
import manifold.ij.extensions.ManifoldPsiClassAnnotator;
import manifold.ij.template.ManTemplateBraceMatcher;
import manifold.ij.template.ManTemplateLanguage;
import manifold.util.ReflectUtil;

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
    ManProject.manProjectFrom( _project ).projectClosed();
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
            replaceJavaExpressionParser();
            overrideJavaStringLiterals();
          } );
        }
      }
    }
  }

  private void replaceJavaExpressionParser()
  {
    ReflectUtil.field( JavaParser.INSTANCE, "myExpressionParser" ).set( new ManExpressionParser( JavaParser.INSTANCE ) );
    ReflectUtil.field( JavaElementType.BINARY_EXPRESSION, "myConstructor" ).set( (Supplier)ManPsiBinaryExpressionImpl::new );
    ReflectUtil.field( JavaElementType.PREFIX_EXPRESSION, "myConstructor" ).set( (Supplier)ManPsiPrefixExpressionImpl::new );

    ReflectUtil.field( JavaParser.INSTANCE, "myStatementParser" ).set( new ManStatementParser( JavaParser.INSTANCE ) );
  }

  /**
   * Override Java String literals to handle fragments
   */
  private void overrideJavaStringLiterals()
  {
    ManJavaLiteralExpressionElementType override = new ManJavaLiteralExpressionElementType();
    ReflectUtil.field( JavaStubElementTypes.class, "LITERAL_EXPRESSION" ).setStatic( override );

    ApplicationManager.getApplication().runReadAction( () -> {
      IElementType[] registry = (IElementType[])ReflectUtil.field( IElementType.class, "ourRegistry" ).getStatic();
      for( int i = 0; i < registry.length; i++ )
      {
        if( registry[i] instanceof JavaLiteralExpressionElementType )
        {
          // ensure the original JavaLiteralExpressionElementType is replaced with ours
          registry[i] = override;
        }
      }
    } );
  }

  private void registerAnnotatorWithAllLanguages()
  {
    for( Language lang: Language.getRegisteredLanguages() )
    {
      LanguageAnnotators.INSTANCE.addExplicitExtension( lang, new ManifoldPsiClassAnnotator() );
    }
    LanguageBraceMatching.INSTANCE.addExplicitExtension( ManTemplateLanguage.INSTANCE, new PairedBraceMatcherAdapter( new ManTemplateBraceMatcher(), ManTemplateLanguage.INSTANCE ) );
  }
}

package manifold.ij.core;

import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathsList;
import java.awt.EventQueue;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import manifold.ij.template.psi.ManTemplateFile;
import manifold.ij.util.MessageUtil;

public class ManLibraryChecker
{
  private static final ManLibraryChecker INSTANCE = new ManLibraryChecker();
  public static ManLibraryChecker instance()
  {
    return INSTANCE;
  }

  private ManLibraryChecker()
  {
  }

  public void warnFeatureRequiresManifold( Project project )
  {
    EventQueue.invokeLater(
      () ->
        MessageUtil.showWarning( project, MessageUtil.Placement.CENTER,
          "This feature requires <b>Manifold</b> dependencies, which appear to be missing from your project's build configuration.\n" +
          "\n" +
          "Please add or update Manifold libraries in your project to at least version: <b>" + getVersionFromPlugin() + "</b>.\n" +
          "\n" +
          "Visit <a href=\"http://manifold.systems/docs.html#setup\">Setup</a> to learn more about configuring Manifold libraries in your project." )
      );
  }

  public void warnIfManifoldJarsAreOld( Project project )
  {
    EventQueue.invokeLater(
      () -> {
        if( projectJarOlderThanPluginJar( project ) )
        {
          MessageUtil.showWarning( project,
            "Some of your project's <b>Manifold</b> dependencies are missing or out of date and\n" +
            "may not be compatible with the newer Manifold IntelliJ IDEA plugin you are using.\n" +
            "\n" +
            "Please add or update Manifold libraries in your project to at least version: <b>" + getVersionFromPlugin() + "</b>.\n" +
            "\n" +
            "Visit <a href=\"http://manifold.systems/docs.html#setup\">Setup</a> to learn more about configuring Manifold libraries in your project." );
        }
      } );
  }

  public void warnIfManifoldTemplatesNotConfiguredForProject( ManTemplateFile psiFile )
  {
    EventQueue.invokeLater(
      () -> {
        ManModule module = ManProject.getModule( psiFile );
        if( module != null &&
            module.getTypeManifolds().stream()
              .noneMatch( tm -> tm.getClass().getSimpleName().equals( "TemplateManifold" ) ) )
        {
          MessageUtil.showWarning( psiFile.getProject(), "The use of templates in Module '${module.getName()}' requires a dependency on <b>manifold-templates</b> or <b>manifold-all</b>" );
        }
      } );
  }

  public boolean isUsingManifoldJars( Project project )
  {
    if( project.isDisposed() )
    {
      // project is closed/disposed
      return false;
    }

    String pluginVer = getVersionFromPlugin();
    if( pluginVer == null )
    {
      // can't find plugin jars
      return false;
    }
    return null != getVersionFromProject( project );
  }

  private boolean projectJarOlderThanPluginJar( Project project )
  {
    if( project.isDisposed() )
    {
      // project is closed/disposed
      return false;
    }

    String pluginVer = getVersionFromPlugin();
    if( pluginVer == null )
    {
      // can't find plugin jars
      return false;
    }
    String projectVer = getVersionFromProject( project );
    if( projectVer == null )
    {
      // missing Manifold jars
      return false; // was `true`, but see https://github.com/manifold-systems/manifold/issues/55
    }
    if( pluginVer.equals( projectVer ) )
    {
      // versions are identical
      return false;
    }

    // compare versions, >0 indicates project jars are older than plugin jars
    return compareComps(
      getVersionComps( pluginVer ),
      getVersionComps( projectVer ) ) > 0;
  }

  private int compareComps( List<Integer> pluginComps, List<Integer> projectComps )
  {
    for( int i = 0; i < pluginComps.size(); i++ )
    {
      if( projectComps.size() == i )
      {
        return 1;
      }
      int result = pluginComps.get( i ) - projectComps.get( i );
      if( result != 0 )
      {
        return result;
      }
    }
    return 0;
  }

  private List<Integer> getVersionComps( String ver )
  {
    List<Integer> comps = new ArrayList<>();
    for( StringTokenizer tokenizer = new StringTokenizer( ver, "." ); tokenizer.hasMoreTokens(); )
    {
      String token = tokenizer.nextToken();
      comps.add( Integer.parseInt( token ) );
    }
    return comps;
  }

  private String getVersionFromProject( Project project )
  {
    List<String> manifoldJarsInProject = getManifoldJarsInProject( project );
    if( manifoldJarsInProject.isEmpty() )
    {
      return null;
    }
    File file = new File( manifoldJarsInProject.get( 0 ) );
    return getVersionFromJarName( file.getName() );
  }

  private String getVersionFromPlugin()
  {
    try
    {
      List<URL> urls = getUrls();
      for( URL url: urls )
      {
        String name = new File( url.toURI() ).getName();
        if( name.contains( "manifold" ) )
        {
          return getVersionFromJarName( name );
        }
      }
    }
    catch( URISyntaxException ignore )
    {
    }
    return null;
  }

  private List<URL> getUrls()
  {
    ClassLoader cl = getClass().getClassLoader();
    if( cl instanceof PluginClassLoader )
    {
      return ((PluginClassLoader)cl).getUrls();
    }
    if( cl instanceof URLClassLoader )
    {
      // no plugin classloader for tests
      return Arrays.asList( ((URLClassLoader)cl).getURLs() );
    }
    throw new IllegalStateException();
  }

  private String getVersionFromJarName( String fileName )
  {
    StringBuilder version = new StringBuilder();
    for( int i = 0; i < fileName.length(); i++ )
    {
      char c = fileName.charAt( i );
      if( Character.isDigit( c ) )
      {
        version.append( c );
      }
      else if( version.length() > 0 )
      {
        if( c == '.' )
        {
          version.append( c );
        }
        else
        {
          break;
        }
      }
    }
    return version.toString();
  }

  public List<String> getManifoldJarsInProject( Project project )
  {
    PathsList pathsList = ProjectRootManager.getInstance( project )
      .orderEntries().withoutSdk().librariesOnly().getPathsList();
    return getManifoldJars( pathsList );
  }
  public List<String> getManifoldJarsInModule( Module module )
  {
    PathsList pathsList = ModuleRootManager.getInstance( module )
      .orderEntries().withoutSdk().librariesOnly().getPathsList();
    return getManifoldJars( pathsList );
  }
  private List<String> getManifoldJars( PathsList pathsList )
  {
    List<String> result = new ArrayList<>();
    for( VirtualFile path: pathsList.getVirtualFiles() )
    {
      String extension = path.getExtension();
      if( extension != null &&
          extension.equalsIgnoreCase( "jar" ) &&
          path.getNameWithoutExtension().contains( "manifold" ) )
      {
        try
        {
          String canonicalPath = path.getCanonicalPath();
          if( canonicalPath == null )
          {
            continue;
          }
          File file = new File( new URL( path.getUrl() ).getFile() );
          Manifest manifest = new JarFile( file.getAbsoluteFile() ).getManifest();
          Attributes attributes = manifest.getMainAttributes();
          String vendor = attributes.getValue( "Implementation-Vendor-Id" );
          if( vendor != null && vendor.trim().equals( "systems.manifold" ) )
          {
            result.add( file.getAbsolutePath() );
          }
        }
        catch( MalformedURLException e )
        {
          //## todo: log
          e.printStackTrace();
        }
      }
    }
    return result;
  }
}

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

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathsList;
import java.awt.EventQueue;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import manifold.ij.template.psi.ManTemplateFile;
import manifold.ij.util.MessageUtil;
import manifold.ij.util.SlowOperationsUtil;
import manifold.util.ReflectUtil;

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
            "Some of your project's <b>Manifold</b> dependencies are out of date and may\n" +
            "not be compatible with the newer Manifold IntelliJ IDEA plugin you are using.\n" +
            "\n" +
            "Please add or update Manifold libraries in your project to at least version: <b>" + getVersionFromPlugin() + "</b>.\n" +
            "\n" +
            "Visit <a href=\"https://github.com/manifold-systems/manifold/tree/master/manifold-core-parent/manifold#projects\">Projects</a> to learn more about configuring Manifold libraries in your project." );
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
          MessageUtil.showWarning( psiFile.getProject(), "The use of templates in Module '${module.getName()}' requires a dependency on <b>manifold-templates</b>" );
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
      // can't find plugin jars, should be here though because this method s/b called only when the plugin is detected
      return false;
    }
    String projectVer = getVersionFromProject( project );
    if( projectVer == null )
    {
      // no Manifold jars, they should be there because this method s/b called only when a project uses manifold jars
      return false;
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
    if( pluginComps.size() != projectComps.size() )
    {
      // don't try to compare versions with different formats
      return 0;
    }

    for( int i = 0; i < pluginComps.size(); i++ )
    {
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
    for( String jarPath : manifoldJarsInProject )
    {
      File file = new File( jarPath );
      String version = getVersionFromJarName( file.getName() );
      if( !version.isEmpty() )
      {
        return version;
      }
    }
    return null;
  }

  private String getVersionFromPlugin()
  {
    List<URL> urls = getUrls();
    for( URL url: urls )
    {
      String name = null;
      try
      {
        File file = new File( url.toURI() );
        name = file.getName();
      }
      catch( Throwable t )
      {
        String filepath = url.getFile();
        if( filepath != null )
        {
          int iSlash = filepath.lastIndexOf( "/" );
          if( iSlash > 0 && iSlash + 1 < filepath.length() )
          {
            name = filepath.substring( iSlash + 1 );
          }
        }
      }
      if( name != null && name.contains( "manifold-rt-" ) )
      {
        String version = getVersionFromJarName( name );
        if( !version.isEmpty() && Character.isDigit( version.charAt( 0 ) ) )
        {
          return version;
        }
      }
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

    ReflectUtil.LiveMethodRef getURLs = getURLsMethod( cl );
    if( getURLs != null )
    {
      Object urls = getURLs.invoke();
      //noinspection unchecked
      return urls.getClass().isArray()
        ? Arrays.asList( (URL[])urls )
        : (List<URL>)urls;
    }
    throw new IllegalStateException();
  }

  private static ReflectUtil.LiveMethodRef getURLsMethod( Object receiver )
  {
    ReflectUtil.LiveMethodRef getURLs = ReflectUtil.WithNull.methodWithReturn( receiver, "getURLs|getUrls", URL[].class );
    if( getURLs == null )
    {
      getURLs = ReflectUtil.WithNull.methodWithReturn( receiver, "getURLs|getUrls", List.class );
      if( getURLs == null && receiver instanceof ClassLoader )
      {
        ReflectUtil.LiveFieldRef ucpField = ReflectUtil.WithNull.field( receiver, "ucp" );
        if( ucpField != null )
        {
          Object ucp = ucpField.get();
          if( ucp != null )
          {
            getURLs = getURLsMethod( ucp );
          }
        }
      }
    }
    return getURLs;
  }

  private String getVersionFromJarName( String fileName )
  {
    StringBuilder version = new StringBuilder();
    boolean dotSeen = false;
    for( int i = 0; i < fileName.length(); i++ )
    {
      char c = fileName.charAt( i );
      if( Character.isDigit( c ) )
      {
        if( dotSeen )
        {
          version.append( '.' );
          dotSeen = false;
        }
        version.append( c );
      }
      else if( version.length() > 0 )
      {
        if( !dotSeen && c == '.' )
        {
          dotSeen = true;
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
    // note see ide.slow.operations.assertion.manifold.fragments registrykey defined in plugin.xml
    return SlowOperationsUtil.allowSlowOperation( "manifold.generic",
      () -> {
        ModuleManager moduleManager = ModuleManager.getInstance( project );
        Module[] allModules = moduleManager.getModules();
        PathsList preprocessorPath = new PathsList();
        for( Module m : allModules )
        {
          String processorPath = CompilerConfiguration.getInstance( project )
            .getAnnotationProcessingConfiguration( m ).getProcessorPath();
          Arrays.stream( processorPath.split( File.pathSeparator ) ).forEach( path -> preprocessorPath.add( path ) );
        }
        PathsList pathsList = ProjectRootManager.getInstance( project )
          .orderEntries().withoutSdk().librariesOnly().getPathsList();
        pathsList.addAll( preprocessorPath.getPathList() );
        return getManifoldJars( pathsList );
      } );
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
          path.getNameWithoutExtension().contains( "manifold-" ) )
      {
        try
        {
          String canonicalPath = path.getCanonicalPath();
          if( canonicalPath == null )
          {
            continue;
          }

          canonicalPath = canonicalPath.replace( '/', File.separatorChar );
          File file = new File( canonicalPath );
          if( !file.isFile() )
          {
            file = new File( new URL( path.getUrl() ).getFile() );
          }

          try( JarFile jarFile = new JarFile( file.getAbsoluteFile() ) )
          {
            Manifest manifest = jarFile.getManifest();
            Attributes attributes = manifest.getMainAttributes();
            String vendor = attributes.getValue( "Implementation-Vendor-Id" );
            if( vendor != null && vendor.trim().equals( "systems.manifold" ) )
            {
              result.add( file.getAbsolutePath() );
            }
          }
          catch( Exception e )
          {
            // todo: some customers get NoSuchFileException for the Jar even though it's there, must add in this case
            e.printStackTrace();
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

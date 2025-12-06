package manifold.ij.core;

import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.lang.UrlClassLoader;
import manifold.ext.rt.api.Jailbreak;
import manifold.rt.api.util.ManClassUtil;
import manifold.rt.api.util.StreamUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Frontloader
{
  public static void frontloadClasses( String fqn, Class<?> from )
  {
    frontloadClasses( fqn, fqn, from );
  }
  public static void frontloadClasses( String fqn, String clFqn, Class<?> from )
  {
    @Jailbreak ClassLoader classLoader = findClassLoader( clFqn, from );
    if( classLoader == null )
    {
      throw new RuntimeException( "Could not find class loader for: " + fqn );
    }

    synchronized( classLoader.getClassLoadingLock( fqn ) )
    {
      if( null == classLoader.findLoadedClass( fqn ) )
      {
        List<URL> urls = getFrontloads( from.getClassLoader(), ClassUtil.extractClassName( fqn ) );
        for( URL url : urls )
        {
          String path = url.getPath().substring( 0, url.getPath().lastIndexOf( '.' ) );
          String fileName = path.substring( path.lastIndexOf( '/' ) + 1 );
          fqn = ManClassUtil.getPackage( fqn ) + "." + fileName;
          try( InputStream bytes = url.openStream() )
          {
            byte[] content = StreamUtil.getContent( bytes );
            classLoader.defineClass( fqn, content, 0, content.length );
          }
          catch( IOException e )
          {
            throw new RuntimeException( e );
          }
        }
      }
      else
      {
        throw new RuntimeException( "Class: '" + fqn + "' already loaded!" );
      }
    }

  }

  public static List<URL> getFrontloads( ClassLoader cl, String className )
  {
    List<URL> result = new ArrayList<>();

    String path = "frontloads";
    Enumeration<URL> urls = null;
    try
    {
      urls = cl.getResources( path );


      while( urls.hasMoreElements() )
      {
        URL url = urls.nextElement();
        String protocol = url.getProtocol();

        if( "file".equals( protocol ) )
        {
          File dir = new File( url.getFile() );
          if( dir.isDirectory() )
          {
            File[] files = dir.listFiles( (d, name) -> name.endsWith( ".frontload" ) );
            if( files != null )
            {
              for( File f : files )
              {
                String slashPath = f.getPath().replace( '\\', '/' );
                if( simpleNameStartsWith( slashPath, className ) )
                {
                  result.add( f.toURI().toURL() );
                }
              }
            }
          }
        }
        else if( "jar".equals( protocol ) )
        {
          String jarPath = url.getPath();
          int bangIndex = jarPath.indexOf( "!" );
          if( bangIndex > 0 )
          {
            String jarFilePath = jarPath.substring( 5, bangIndex ); // strip "file:"
            try (JarFile jar = new JarFile( URLDecoder.decode( jarFilePath, StandardCharsets.UTF_8.name() ) ))
            {
              for( Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements(); )
              {
                JarEntry entry = e.nextElement();
                String entryName = entry.getName();
                if( entryName.startsWith( path ) && entryName.endsWith( ".frontload" ) && !entry.isDirectory() )
                {
                  URL resourceUrl = cl.getResource( entryName );
                  if( resourceUrl != null )
                  {
                    String slashPath = resourceUrl.getPath().replace( '\\', '/' );
                    if( simpleNameStartsWith( slashPath, className ) )
                    {
                      result.add( resourceUrl );
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    catch( Exception e )
    {
      throw new RuntimeException( e );
    }
    return result;
  }

  private static boolean simpleNameStartsWith( String slashPath, String className )
  {
    int lastSlash = slashPath.lastIndexOf( '/' );
    String fileName = slashPath.substring( lastSlash + 1 );
    return fileName.startsWith( className );
  }

  public static ClassLoader findClassLoader( String fqn, Class<?> from )
  {
    ClassLoader fromLoader = from.getClassLoader();
    String jarName = getJarName( fqn, fromLoader );
    return findClassLoader( fqn, jarName, fromLoader, new HashSet<>() );
  }

  private static String getJarName( String fqn, ClassLoader fromLoader )
  {
    String resourceName = fqn.replace( '.', '/' ) + ".class";
    URL url = fromLoader.getResource( resourceName );
    if( url == null )
    {
      throw new IllegalArgumentException( fqn + " not found" );
    }
    String path = url.getPath();
    int iDotJar = path.indexOf( ".jar!" );
    if( iDotJar < 0 )
    {
      throw new IllegalArgumentException( "Expecting " + fqn + " to be in a jar file" );
    }
    return path.substring( path.substring( 0, iDotJar ).lastIndexOf( '/' ), iDotJar + 4 );
  }

  private static ClassLoader findClassLoader( String fqn, String jarName, ClassLoader csr, Set<ClassLoader> visited )
  {
    if( visited.contains( csr ) )
    {
      return null;
    }
    visited.add( csr );

    if( !(csr instanceof UrlClassLoader) )
    {
      return null;
    }

    if( csr instanceof PluginClassLoader )
    {
      ClassLoader[] allParents = ((PluginClassLoader)csr).getAllParentsClassLoaders();
      if( allParents != null )
      {
        for( ClassLoader cl : allParents )
        {
          ClassLoader found = findClassLoader( fqn, jarName, cl, visited );
          if( found != null )
          {
            return found;
          }
        }
      }
    }
    else
    {
      ClassLoader found = findClassLoader( fqn, jarName, csr.getParent(), visited );
      if( found != null )
      {
        return found;
      }
    }

    String resourcePath = fqn.replace( '.', '/' ) + ".class";
    URL[] urls = ((UrlClassLoader)csr).getUrls().stream()
      .filter( url -> url.getPath().endsWith( jarName ) ).toArray( size -> new URL[size] );
    if( urlsContainClass( urls, resourcePath ) )
    {
      return csr;
    }

    return null;
  }

  private static boolean urlsContainClass( URL[] urls, String resourcePath )
  {
    for( URL url : urls )
    {
      try
      {
        if( !"file".equals( url.getProtocol() ) || !url.getPath().endsWith( ".jar" ) )
        {
          continue;
        }
        Path jarPath = Path.of( url.toURI() );
        try (JarFile jar = new JarFile( jarPath.toFile() ))
        {
          JarEntry entry = jar.getJarEntry( resourcePath );
          if( entry != null )
          {
            return true;
          }
        }
      }
      catch( Exception ignored )
      {
      }
    }
    return false;
  }
}

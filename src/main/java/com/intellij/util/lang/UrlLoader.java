package com.intellij.util.lang;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import manifold.api.host.NoBootstrap;
import org.jetbrains.annotations.NotNull;
import sun.net.www.ParseUtil;

/**
 * This class facilitates Manifold's class loader integration at runtime inside IJ's PluginClassLoader.
 * The actual usage of this class takes place reflectively in Manifold core. Here be dragons.
 */
@NoBootstrap
@SuppressWarnings("unused")
public class UrlLoader extends Loader
{
  private static final String[] NOT_OURS = {
    "java/",
    "javax/",
    "sun/",
  };

  public UrlLoader( URL url, int index )
  {
    super( url, index );
  }

  Resource getResource( final String name )
  {
    return getResource( name, false );
  }

  /** This signature supports older versions of IJ Loader class */
  Resource getResource( final String name, boolean flag )
  {
    if( isSurelyNotOurs( name ) )
    {
      return null;
    }

    final URL url = new URL( getBaseURL(), ParseUtil.encodePath( name, false ) );
    final URLConnection uc;
    try
    {
      uc = url.openConnection();
      uc.getInputStream();
    }
    catch( Exception e )
    {
      return null;
    }
    return new IjResource( new JavaResource( name, url, uc ) );
  }

  private boolean isSurelyNotOurs( String name )
  {
    return Arrays.stream( NOT_OURS ).anyMatch( name::startsWith );
  }

  @NotNull
  @Override
  ClasspathCache.LoaderData buildData()
  {
    return new ClasspathCache.LoaderData();
  }

  @NoBootstrap
  private static class IjResource extends Resource
  {
    private final JavaResource _resource;

    IjResource( JavaResource resource )
    {
      _resource = resource;
    }

    @Override
    public URL getURL()
    {
      return _resource.getURL();
    }

    @Override
    public InputStream getInputStream() throws IOException
    {
      return _resource.getInputStream();
    }

    @Override
    public byte[] getBytes() throws IOException
    {
      return _resource.getBytes();
    }
  }

  @NoBootstrap
  private class JavaResource extends sun.misc.Resource
  {
    private final String _name;
    private final URL _url;
    private final URLConnection _uc;

    private JavaResource( String name, URL url, URLConnection uc )
    {
      _name = name;
      _url = url;
      _uc = uc;
    }

    public String getName()
    {
      return _name;
    }

    public URL getURL()
    {
      return _url;
    }

    public URL getCodeSourceURL()
    {
      return getBaseURL();
    }

    public InputStream getInputStream() throws IOException
    {
      return _uc.getInputStream();
    }

    public int getContentLength()
    {
      return _uc.getContentLength();
    }
  }
}

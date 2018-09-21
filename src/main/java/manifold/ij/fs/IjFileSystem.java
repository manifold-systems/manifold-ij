/*
 * Manifold
 */

package manifold.ij.fs;

import com.google.common.collect.Maps;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.openapi.vfs.impl.http.RemoteFileInfo;
import com.intellij.openapi.vfs.impl.jar.JarFileSystemImpl;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.jar.JarFile;
import manifold.api.fs.IDirectory;
import manifold.api.fs.IFile;
import manifold.api.fs.IFileSystem;
import manifold.api.fs.IResource;
import manifold.api.fs.jar.JarFileDirectoryImpl;
import manifold.api.fs.url.URLFileImpl;
import manifold.api.service.BaseService;
import manifold.ij.core.ManProject;

public class IjFileSystem extends BaseService implements IFileSystem
{
  private static final Object CACHED_FILE_SYSTEM_LOCK = new Object();

  private final ManProject _project;
  private final Map<File, IDirectory> _cachedDirInfo;
  private final IDirectoryResourceExtractor _dirExtractor;
  private final IFileResourceExtractor _fileExtractor;


  public IjFileSystem( ManProject project )
  {
    _project = project;
    _cachedDirInfo = Maps.newHashMap();
    _dirExtractor = new IDirectoryResourceExtractor();
    _fileExtractor = new IFileResourceExtractor();
  }

  public ManProject getProject()
  {
    return _project;
  }

  @Override
  public IDirectory getIDirectory( File dir )
  {
    String pathString = dir.getAbsolutePath().replace( File.separatorChar, '/' );
    return getIDirectory( pathString );
  }

  IDirectory getIDirectory( String pathString )
  {
    VirtualFile file = LocalFileSystemImpl.getInstance().findFileByPath( pathString );
    if( file != null && pathString.endsWith( ".jar" ) )
    {
      file = JarFileSystem.getInstance().getJarRootForLocalFile( file );
      if( file == null )
      {
        throw new RuntimeException( "Cannot load Jar file for: " + pathString );
      }
      return new IjJarDirectory( this, file );
    }
    return file != null ? new IjDirectory( this, file ) : new IjDirectory( this, pathString );
  }

  @Override
  public IFile getIFile( File file )
  {
    String pathString = file.getAbsolutePath().replace( File.separatorChar, '/' );
    return getIFile( pathString );
  }


  IFile getIFile( String pathString )
  {
    VirtualFile file = LocalFileSystemImpl.getInstance().findFileByPath( pathString );
    if( file == null && pathString.contains( ".jar!" ) )
    {
      file = JarFileSystemImpl.getInstance().findFileByPath( pathString );
    }
    return file != null ? new IjFile( this, file ) : new IjFile( this, pathString );
  }


  public IjFile getIFile( VirtualFile file )
  {
    if( file instanceof HttpVirtualFile )
    {
      RemoteFileInfo fileInfo = ((HttpVirtualFile)file).getFileInfo();
      if( fileInfo != null && fileInfo.getLocalFile() != null )
      {
        return getIFile( fileInfo.getLocalFile() );
      }
    }

    return new IjFile( this, file );
  }


  public IjDirectory getIDirectory( VirtualFile file )
  {
    return new IjDirectory( this, file );
  }


  @Override
  public IDirectory getIDirectory( URL url )
  {
    return _dirExtractor.getClassResource( url );
  }


  @Override
  public IFile getIFile( URL url )
  {
    return _fileExtractor.getClassResource( url );
  }


  private IDirectory createDir( File dir )
  {
    if( dir.getName().endsWith( ".jar" ) && dir.isFile() )
    {
      return new JarFileDirectoryImpl( dir );
    }
    else
    {
      return getIDirectory( dir );
    }
  }

  @Override
  public void setCachingMode( CachingMode cachingMode )
  {
  }

  @Override
  public void clearAllCaches()
  {
  }

  private abstract class ResourceExtractor<J extends IResource>
  {
    J getClassResource( URL _url )
    {
      if( _url == null )
      {
        return null;
      }

      String protocol = _url.getProtocol();

      switch( protocol )
      {
        case "file":
          return getIResourceFromJavaFile( _url );
        case "jar":
          JarURLConnection urlConnection;
          JarFile jarFile;
          try
          {
            urlConnection = (JarURLConnection)_url.openConnection();
            jarFile = urlConnection.getJarFile();
          }
          catch( IOException e )
          {
            throw new RuntimeException( e );
          }
          File dir = new File( jarFile.getName() );

          IDirectory jarFileDirectory;
          synchronized( CACHED_FILE_SYSTEM_LOCK )
          {
            jarFileDirectory = _cachedDirInfo.get( dir );
            if( jarFileDirectory == null )
            {
              jarFileDirectory = createDir( dir );
              _cachedDirInfo.put( dir, jarFileDirectory );
            }
          }
          return getIResourceFromJarDirectoryAndEntryName( jarFileDirectory, urlConnection.getEntryName() );
        case "http":
        case "https":
          return getRemoteFile( _url );
        default:
          throw new RuntimeException( "Unrecognized protocol: " + protocol );
      }
    }

    abstract J getIResourceFromJarDirectoryAndEntryName( IDirectory jarFS, String entryName );

    abstract J getIResourceFromJavaFile( URL location );

    abstract J getRemoteFile( URL location );


    File getFileFromURL( URL url )
    {
      try
      {
        URI uri = url.toURI();
        if( uri.getFragment() != null )
        {
          uri = new URI( uri.getScheme(), uri.getSchemeSpecificPart(), null );
        }
        return new File( uri );
      }
      catch( URISyntaxException ex )
      {
        throw new RuntimeException( ex );
      }
      catch( IllegalArgumentException ex )
      {
        // debug getting IAE only in TH - unable to parse URL with fragment identifier
        throw new IllegalArgumentException( "Unable to parse URL " + url.toExternalForm(), ex );
      }
    }

  }

  private class IFileResourceExtractor extends ResourceExtractor<IFile>
  {
    IFile getIResourceFromJarDirectoryAndEntryName( IDirectory jarFS, String entryName )
    {
      return jarFS.file( entryName );
    }

    IFile getIResourceFromJavaFile( URL location )
    {
      return getProject().getFileSystem().getIFile( getFileFromURL( location ) );
    }

    @Override
    IFile getRemoteFile( URL location )
    {
      return new URLFileImpl( location );
    }
  }

  private class IDirectoryResourceExtractor extends ResourceExtractor<IDirectory>
  {
    protected IDirectory getIResourceFromJarDirectoryAndEntryName( IDirectory jarFS, String entryName )
    {
      return jarFS.dir( entryName );
    }

    protected IDirectory getIResourceFromJavaFile( URL location )
    {
      return getProject().getFileSystem().getIDirectory( getFileFromURL( location ) );
    }

    @Override
    IDirectory getRemoteFile( URL location )
    {
      throw new UnsupportedOperationException( "unable to obtain directory via remote protocol. " + location );
    }
  }
}

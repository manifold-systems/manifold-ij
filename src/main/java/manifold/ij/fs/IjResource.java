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

/*
 * Manifold
 */

package manifold.ij.fs;

import com.google.common.base.Objects;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.jar.JarFileSystemImpl;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import java.io.File;
import java.io.IOException;
import java.net.URI;

import com.intellij.testFramework.LightVirtualFile;
import manifold.api.fs.IDirectory;
import manifold.api.fs.IResource;
import manifold.api.fs.ResourcePath;

public abstract class IjResource implements IResource
{
  private IjFileSystem _fs;
  VirtualFile _virtualFile;
  final String _path;

  IjResource( IjFileSystem fs, VirtualFile virtualFile )
  {
    _fs = fs;
    _virtualFile = virtualFile;
    _path = removeJarSeparator( virtualFile.getPath() );
  }

  IjResource( IjFileSystem fs, String dir )
  {
    _fs = fs;
    _path = dir;
  }

  public IjFileSystem getFileSystem()
  {
    return _fs;
  }

  @Override
  public IDirectory getParent()
  {
    if( _virtualFile != null )
    {
      if( _virtualFile.getParent() != null )
      {
        return new IjDirectory( getFileSystem(), _virtualFile.getParent() );
      }
      if( !(_virtualFile instanceof LightVirtualFile) || _path.length() <= 3 )
      {
        return null;
      }
    }
    return getFileSystem().getIDirectory( _path.substring( 0, _path.lastIndexOf( '/' ) ) );
  }

  @Override
  public String getName()
  {
    return _virtualFile != null ? _virtualFile.getName() : new File( _path ).getName();
  }

  @Override
  public boolean exists()
  {
    if( _virtualFile != null )
    {
      return _virtualFile.exists();
    }
    else
    {
      return new File( _path ).exists();
    }
  }

  @Override
  public boolean delete() throws IOException
  {
    return false;
  }

  @Override
  public URI toURI()
  {
    return new File( _path ).toURI();
  }

  @Override
  public ResourcePath getPath()
  {
    return ResourcePath.parse( _path );
  }

  @Override
  public boolean isChildOf( IDirectory dir )
  {
    String dirPath = ((IjDirectory)dir)._path;
    return _path.length() > dirPath.length() && _path.startsWith( dirPath ) && _path.charAt( dirPath.length() ) == '/';
  }

  @Override
  public boolean isDescendantOf( IDirectory dir )
  {
    if( dir instanceof IjDirectory )
    {
      // note, trailing '/' prevents /root/src2 matching against /root/src
      if( _path.contains( ".jar" ) )
      {
        return _path.startsWith( ((IjDirectory)dir)._path );
      }
      else
      {
        return (_path + '/').startsWith( ((IjDirectory)dir)._path + '/' );
      }
    }
    return false;
  }


  @Override
  public File toJavaFile()
  {
    return new File( _path.replace( '/', File.separatorChar ) );
  }

  @Override
  public boolean isJavaFile()
  {
    return _virtualFile.getFileSystem() instanceof LocalFileSystemImpl;
  }

  public String toString()
  {
    return _path;
  }

  @Override
  public boolean equals( Object o )
  {
    if( o instanceof IjResource )
    {
      IjResource other = (IjResource)o;
      if( Objects.equal( _virtualFile, other._virtualFile ) )
      {
        return true;
      }
      if( (_virtualFile == null || other._virtualFile == null) &&
          Objects.equal( _path, other._path ) )
      {
        return true;
      }
    }
    return false;
  }

  @Override
  public int hashCode()
  {
    return Objects.hashCode( _virtualFile );
  }

  @Override
  public boolean create()
  {
    if( _virtualFile == null )
    {
      final IjDirectory parent = (IjDirectory)getParent();
      parent.create();
      final int index = _path.lastIndexOf( '/' );
      final String name = _path.substring( index + 1 );
      _virtualFile = create( parent._virtualFile, name );
      parent._virtualFile.refresh( false, true );
      return true;
    }
    return true;
  }

  protected abstract VirtualFile create( VirtualFile virtualFile, String name ) throws IOException;

  public VirtualFile getVirtualFile()
  {
    return _virtualFile;
  }

  public VirtualFile resolveVirtualFile()
  {
    if( _virtualFile == null )
    {
      //try re-resolve virtual file if it null
      _virtualFile = LocalFileSystemImpl.getInstance().findFileByPath( _path );
      if( _virtualFile == null && _path.contains( ".jar!" ) )
      {
        _virtualFile = JarFileSystemImpl.getInstance().findFileByPath( _path );
      }
    }
    return _virtualFile;
  }

  @Override
  public boolean isInJar()
  {
    return _virtualFile != null && _virtualFile.getFileSystem() instanceof JarFileSystem;
  }

  private static String removeJarSeparator( String path )
  {
    if( path.endsWith( JarFileSystem.JAR_SEPARATOR ) )
    {
      path = path.substring( 0, path.length() - JarFileSystem.JAR_SEPARATOR.length() );
    }
    return path;
  }
}

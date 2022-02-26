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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import manifold.api.fs.IDirectory;
import manifold.api.fs.IFile;
import manifold.api.fs.IResource;

public class IjDirectory extends IjResource implements IDirectory
{
  IjDirectory( IjFileSystem fs, VirtualFile dir )
  {
    super( fs, dir );
  }

  IjDirectory( IjFileSystem fs, String dir )
  {
    super( fs, dir );
  }

  @Override
  public IDirectory dir( String relativePath )
  {
    if( relativePath.startsWith( File.separator ) )
    {
      relativePath = relativePath.substring( 1 );
    }

    String path = _path + "/" + relativePath.replace( File.separatorChar, '/' );
    return getFileSystem().getIDirectory( path );
  }

  @Override
  public IFile file( String relativePath )
  {
    if( relativePath.startsWith( File.separator ) )
    {
      relativePath = relativePath.substring( 1 );
    }
    String path = _path + "/" + relativePath.replace( File.separatorChar, '/' );
    return getFileSystem().getIFile( path );
  }

  @Override
  public boolean mkdir() throws IOException
  {
    return create();
  }

  @Override
  public List<? extends IDirectory> listDirs()
  {
    List<IDirectory> result = new ArrayList<>();
    if( _virtualFile != null && _virtualFile.isValid() )
    {
      for( VirtualFile child : _virtualFile.getChildren() )
      {
        if( child.isDirectory() )
        {
          result.add( new IjDirectory( getFileSystem(), child ) );
        }
      }
    }
    return result;
  }

  @Override
  public List<? extends IFile> listFiles()
  {
    List<IFile> result = new ArrayList<>();
    if( _virtualFile != null && _virtualFile.isValid() )
    {
      for( VirtualFile child : _virtualFile.getChildren() )
      {
        if( !child.isDirectory() )
        {
          result.add( new IjFile( getFileSystem(), child ) );
        }
      }
    }
    return result;
  }

  @Override
  public String relativePath( IResource resource )
  {
    String path = ((IjResource)resource)._path;
    int index = _path.length() + 1;
    return index > path.length() ? "" : path.substring( index );
  }

  @Override
  public void clearCaches()
  {
    throw new RuntimeException( "Not supported" );
  }

  @Override
  public boolean hasChildFile( String path )
  {
    IFile childFile = file( path );
    return childFile != null && childFile.exists();
  }

  @Override
  public boolean isAdditional()
  {
    return false;
  }

  protected VirtualFile create( final VirtualFile virtualFile, final String name ) throws IOException
  {
    final VirtualFile[] result = new VirtualFile[1];
    ApplicationManager.getApplication().runWriteAction( new Runnable()
    {
      @Override
      public void run()
      {
        try
        {
          result[0] = virtualFile.createChildDirectory( this, name );
        }
        catch( IOException e )
        {
          e.printStackTrace();
        }
      }

    } );
    return result[0];
  }
}

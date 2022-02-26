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

import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.io.IOException;
import manifold.api.fs.IDirectory;
import manifold.api.fs.IFile;
import manifold.api.fs.IResource;

public class IjJarDirectory extends IjDirectory
{
  IjJarDirectory( IjFileSystem fs, VirtualFile virtualFile )
  {
    super( fs, virtualFile );
  }

  @Override
  public IDirectory dir( String relativePath )
  {
    VirtualFile child = _virtualFile.findFileByRelativePath( normalize( relativePath ) );
    return child == null ? null : new IjJarDirectory( getFileSystem(), child );
  }

  @Override
  public IFile file( String path )
  {
    VirtualFile child = _virtualFile.findFileByRelativePath( normalize( path ) );
    return child == null ? null : new IjFile( getFileSystem(), child );
  }

  private String normalize( String relativePath )
  {
    return relativePath.replace( File.separatorChar, '/' );
  }

  @Override
  public String relativePath( IResource resource )
  {
    if( ((IjResource)resource)._path.length() > _path.length() + 2 )
    {
      return ((IjResource)resource)._path.substring( _path.length() + 2 );
    }
    return _path;
  }

  @Override
  protected VirtualFile create( VirtualFile virtualFile, String name ) throws IOException
  {
    throw new RuntimeException( "Not supported" );
  }

  @Override
  public boolean mkdir() throws IOException
  {
    throw new RuntimeException( "Not supported" );
  }
}

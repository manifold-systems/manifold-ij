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
    return ((IjResource)resource)._path.substring( _path.length() + 2 );
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

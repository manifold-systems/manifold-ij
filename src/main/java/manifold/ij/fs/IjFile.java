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
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import manifold.api.fs.IFile;

public class IjFile extends IjResource implements IFile
{
  private Charset charset = StandardCharsets.UTF_8;

  IjFile( IjFileSystem fs, VirtualFile file )
  {
    super( fs, file );
    if( file.isCharsetSet() )
    {
      charset = file.getCharset();
    }
  }

  IjFile( IjFileSystem fs, String file )
  {
    super( fs, file );
    if( _virtualFile != null && _virtualFile.isCharsetSet() )
    {
      charset = _virtualFile.getCharset();
    }
  }

  @Override
  public InputStream openInputStream() throws IOException
  {
    if( _virtualFile != null &&
      (getExtension().equalsIgnoreCase( "jar" ) ||
        getExtension().equalsIgnoreCase( "zip" ) ||
        getExtension().equalsIgnoreCase( "class" )) )
    {
      return new ByteArrayInputStream( _virtualFile.contentsToByteArray() );
    }

    String temporaryBuffer = getTemporaryBuffer( this );
    if( temporaryBuffer != null )
    {
      return new ByteArrayInputStream( temporaryBuffer.getBytes( charset ) );
    }
    else
    {
      return _virtualFile != null ? _virtualFile.getInputStream() : new FileInputStream( new File( _path ) );
    }
  }

  private String getTemporaryBuffer( IjFile file )
  {
    final VirtualFile virtualFile = file.getVirtualFile();

    // we're getting the cached documents since getDocument() forces PSI creating which will cause deadlock !!!
    if( virtualFile != null && !virtualFile.getFileType().isBinary() )
    {
//      return LoadTextUtil.loadText( virtualFile ).toString();
      final Document document = FileDocumentManager.getInstance().getCachedDocument( virtualFile );
      final String[] result = new String[1];
      if( document != null )
      {
        if( ApplicationManagerEx.getApplicationEx().tryRunReadAction( () -> result[0] = document.getText() ) )
        {
          return result[0];
        }
        else
        {
          return document.getCharsSequence().toString();
        }
      }
    }

    return null;
  }

  @Override
  public OutputStream openOutputStream() throws IOException
  {
    return _virtualFile != null ? _virtualFile.getOutputStream( this ) : null;
  }

  @Override
  public OutputStream openOutputStreamForAppend() throws IOException
  {
    return _virtualFile != null ? _virtualFile.getOutputStream( this ) : null;
  }

  @Override
  public String getExtension()
  {
    return _virtualFile != null
           ? _virtualFile.getExtension() == null ? "" : _virtualFile.getExtension()
           : FileUtilRt.getExtension( _path );
  }

  @Override
  public String getBaseName()
  {
    if( _virtualFile != null )
    {
      return _virtualFile.getNameWithoutExtension();
    }
    else
    {
      String name = _path.substring( _path.lastIndexOf( '/' ) + 1 );
      name = name.substring( 0, name.lastIndexOf( '.' ) );
      return name;
    }
  }

  protected VirtualFile create( final VirtualFile virtualFile, final String name )
  {
    final VirtualFile[] result = new VirtualFile[1];
    ApplicationManager.getApplication().runWriteAction( new Runnable()
    {
      @Override
      public void run()
      {
        try
        {
          result[0] = virtualFile.createChildData( this, name );
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

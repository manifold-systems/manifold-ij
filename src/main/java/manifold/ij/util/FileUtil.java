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

package manifold.ij.util;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.indexing.IndexingDataKeys;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import manifold.api.type.ITypeManifold;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.fs.IjDirectory;
import manifold.ij.fs.IjFile;
import manifold.ij.fs.IjResource;

/**
 */
public class FileUtil
{
  public static IjFile toIFile( Project ijProject, VirtualFile file )
  {
    return ManProject.manProjectFrom( ijProject ).getFileSystem().getIFile( file );
  }

  public static IjFile toIFile( ManProject manProject, VirtualFile file )
  {
    return manProject.getFileSystem().getIFile( file );
  }

  public static IjResource toIResource( Project ijProject, VirtualFile file )
  {
    if( file.isDirectory() )
    {
      return toIDirectory( ijProject, file );
    }
    else
    {
      return toIFile( ijProject, file );
    }
  }

  public static IjDirectory toIDirectory( Project ijProject, VirtualFile file )
  {
    return ManProject.manProjectFrom( ijProject ).getFileSystem().getIDirectory( file );
  }

  public static VirtualFile toVirtualFile( PsiFile file )
  {
    VirtualFile vfile = file.getUserData( IndexingDataKeys.VIRTUAL_FILE );
    if( vfile == null )
    {
      vfile = file.getVirtualFile();
      if( vfile == null )
      {
        vfile = file.getOriginalFile().getVirtualFile();
        if( vfile == null )
        {
          vfile = file.getViewProvider().getVirtualFile();
        }
      }
      else if( vfile instanceof LightVirtualFile )
      {
        PsiFile containingFile = file.getContainingFile();
        if( containingFile != null && containingFile != file )
        {
          PsiFile originalFile = containingFile.getOriginalFile();
          SmartPsiElementPointer owningFile = originalFile.getUserData( FileContextUtil.INJECTED_IN_ELEMENT );
          if( owningFile != null )
          {
            vfile = owningFile.getVirtualFile();
          }
        }
      }
    }
    return vfile;
  }

  public static VirtualFile getOriginalFile( VirtualFileWindow window )
  {
    VirtualFile file = window.getDelegate();
    if( file instanceof LightVirtualFile )
    {
      final VirtualFile original = ((LightVirtualFile)file).getOriginalFile();
      if( original != null )
      {
        file = original;
      }
    }
    return file;
  }

  public static Set<String> typesForFile( VirtualFile vfile, ManModule module )
  {
    IjFile ijFile = toIFile( module.getProject(), vfile );
    return typesForFile( ijFile, module );
  }

  public static Set<String> typesForFile( IjFile file, ManModule module )
  {
    Set<String> typeNames = new HashSet<>();
    Set<ITypeManifold> typeManifolds = module.getTypeManifolds();
    for( ITypeManifold sp : typeManifolds )
    {
      String[] fqns = sp.getTypesForFile( file );
      if( fqns.length > 0 )
      {
        typeNames.addAll( Arrays.asList( fqns ) );
      }
    }
    return typeNames;
  }

}

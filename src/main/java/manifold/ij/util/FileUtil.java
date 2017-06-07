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
import manifold.api.sourceprod.ISourceProducer;
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
    Set<ISourceProducer> sourceProducers = module.getSourceProducers();
    for( ISourceProducer sp : sourceProducers )
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

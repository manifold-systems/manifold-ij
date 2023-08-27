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

package manifold.ij.extensions;

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.light.LightClass;
import com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl;
import com.intellij.psi.util.ClassUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.Icon;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;

import manifold.api.fs.IFile;
import manifold.api.fs.IFileFragment;
import manifold.ij.core.ManModule;
import manifold.ij.fs.IjFile;

public class ManifoldPsiClass extends LightClass
{
  public static final Key<ManifoldPsiClass> KEY_MANIFOLD_PSI_CLASS = new Key<>( "Facade" );

  private final List<SmartPsiElementPointer<PsiFile>> _files;
  private final List<IFile> _ifiles;
  private final String _fqn;
  private final ManModule _manModule;
  private final DiagnosticCollector<JavaFileObject> _issues;

  public ManifoldPsiClass( PsiClass delegate, ManModule module, List<IFile> files, String fqn, DiagnosticCollector<JavaFileObject> issues )
  {
    super( delegate );

    _ifiles = files;
    _fqn = fqn;
    _manModule = module;
    _issues = issues;
    PsiManager manager = PsiManagerImpl.getInstance( delegate.getProject() );
    _files = new ArrayList<>( _ifiles.size() );
    for( IFile ifile : _ifiles )
    {
      VirtualFile vfile = ((IjFile)ifile.getPhysicalFile()).getVirtualFile();
      if( vfile != null && vfile.isValid() )
      {
        PsiFile file = manager.findFile( vfile );
        if( file != null )
        {
          _files.add( SmartPointerManager.createPointer( file ) );
          file.putUserData( ModuleUtil.KEY_MODULE, module.getIjModule() );
        }
      }
    }
    if( _files.isEmpty() )
    {
      PsiFile containingFile = delegate.getContainingFile();
      if( containingFile != null )
      {
        _files.add( SmartPointerManager.createPointer( containingFile ) );
      }
    }
    if( getContainingClass() == null )
    {
      delegate.getContainingFile().putUserData( KEY_MANIFOLD_PSI_CLASS, this );
    }
    reassignFragmentContainer();
  }

  /**
   * Update the PsiComment hosting the fragment
   */
  private void reassignFragmentContainer()
  {
    if( !isFragment() )
    {
      return;
    }

    for( IFile file: _ifiles )
    {
      if( file instanceof IFileFragment )
      {
        MaybeSmartPsiElementPointer container = (MaybeSmartPsiElementPointer)((IFileFragment)file).getContainer();
        if( container == null || !(container.getElement() instanceof PsiFileFragment) )
        {
          continue;
        }
        ((IFileFragment)file).setContainer( null );

        PsiFile psiFile = PsiManager.getInstance( getProject() ).findFile( ((IjFile)file.getPhysicalFile()).getVirtualFile() );
        if( psiFile != null )
        {
          PsiElement elem = psiFile.findElementAt( ((IFileFragment)file).getOffset() );
          while( elem != null && !(elem instanceof PsiFileFragment) )
          {
            elem = elem.getParent();
          }
          if( elem != null )
          {
            ((IFileFragment)file).setContainer(
              new MaybeSmartPsiElementPointer( SmartPointerManagerImpl.createPointer( elem ) ) );
          }
        }
      }
    }
  }

  @Override
  public PsiFile getContainingFile()
  {
    // Returns the actual PsiFile backing this type, necessary for ManShortNamesCache

    final List<PsiFile> rawFiles = getRawFiles();
    // Sometimes there is no backing file e.g., SystemProperties class
    return rawFiles.isEmpty() ? null : rawFiles.get( 0 );
  }

  @Override
  public boolean isPhysical()
  {
    // Returns 'true' here so that this type works with ManShortNamesCache.
    // See DefaultClassNavigationContributor#processElementsWithName(), and its call to isPhysical()
    return true;
  }

  @Override
  public ItemPresentation getPresentation()
  {
    // Necessary for ManShortNamesCache
    return ItemPresentationProviders.getItemPresentation( this );
  }

  @Override
  public String getQualifiedName()
  {
    return _fqn;
  }

  @Override
  public String getName()
  {
    return ClassUtil.extractClassName( _fqn );
  }

  public String getNamespace()
  {
    return ClassUtil.extractPackageName( _fqn );
  }

  @Override
  public boolean isWritable()
  {
    return true;
  }

  public List<PsiFile> getRawFiles()
  {
    return _files.stream().map( f -> f.getElement() ).collect( Collectors.toList() );
  }

  public List<IFile> getFiles()
  {
    return _ifiles;
  }

  @Override
  public void navigate( boolean requestFocus )
  {
    final Navigatable navigatable = PsiNavigationSupport.getInstance().getDescriptor( this );
    if( navigatable != null )
    {
      navigatable.navigate( requestFocus );
    }
  }

  @Override
  public boolean canNavigate()
  {
    return true;
  }

  @Override
  public boolean canNavigateToSource()
  {
    return true;
  }

  @Override
  public String getText()
  {
    //todo: handle multiple files somehow
    return _files.isEmpty() ? "" : getRawFiles().get( 0 ).getText();
  }

  @Override
  public PsiElement getNavigationElement()
  {
    return _files.isEmpty() ? null : getRawFiles().get( 0 ).getNavigationElement();
  }

//  @Override
//  public void checkAdd( PsiElement element ) throws IncorrectOperationException
//  {
//
//  }

  @Override
  public Icon getIcon( int flags )
  {
    if( _files.isEmpty() )
    {
      return null;
    }
    PsiFile psiFile = getRawFiles().get( 0 );
    return psiFile == null ? null : psiFile.getIcon( flags );
  }

  @Override
  public PsiElement copy()
  {
    return new ManifoldPsiClass( (PsiClass)getDelegate().copy(), _manModule, _ifiles, _fqn, _issues );
  }

  public Module getModule()
  {
    return _manModule.getIjModule();
  }

  public DiagnosticCollector<JavaFileObject> getIssues()
  {
    return _issues;
  }

  public boolean isFragment()
  {
    return _ifiles.stream().anyMatch( e -> e instanceof IFileFragment );
  }
}

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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.light.LightClass;
import com.intellij.psi.util.ClassUtil;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Icon;
import javax.tools.DiagnosticCollector;
import manifold.api.fs.IFile;
import manifold.ij.core.ManModule;
import manifold.ij.fs.IjFile;

public class ManifoldPsiClass extends LightClass
{
  public static final Key<ManifoldPsiClass> KEY_MANIFOLD_PSI_CLASS = new Key<>( "Facade" );

  private List<PsiFile> _files;
  private List<IFile> _ifiles;
  private String _fqn;
  private ManModule _manModule;
  private DiagnosticCollector _issues;

  public ManifoldPsiClass( PsiClass delegate, ManModule module, List<IFile> files, String fqn, DiagnosticCollector issues )
  {
    super( delegate );

    initialize( delegate, module, files, fqn, issues );
  }

  public void initialize( PsiClass delegate, ManModule manModule, List<IFile> files, String fqn, DiagnosticCollector issues )
  {
    _ifiles = files;
    _fqn = fqn;
    _manModule = manModule;
    _issues = issues;
    PsiManager manager = PsiManagerImpl.getInstance( delegate.getProject() );
    _files = new ArrayList<>( _ifiles.size() );
    for( IFile ifile : _ifiles )
    {
      VirtualFile vfile = ((IjFile)ifile).getVirtualFile();
      if( vfile != null )
      {
        PsiFile file = manager.findFile( vfile );
        _files.add( file );
        file.putUserData( ModuleUtil.KEY_MODULE, manModule.getIjModule() );
      }
    }
    delegate.getContainingFile().putUserData( KEY_MANIFOLD_PSI_CLASS, this );
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
    return _files;
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
    return _files.isEmpty() ? "" : _files.get( 0 ).getText();
  }

  @Override
  public PsiElement getNavigationElement()
  {
    return _files.isEmpty() ? null : _files.get( 0 ).getNavigationElement();
  }

//  @Override
//  public void checkAdd( PsiElement element ) throws IncorrectOperationException
//  {
//
//  }

  @Override
  public Icon getIcon( int flags )
  {
    return _files.isEmpty() ? null : _files.get( 0 ).getIcon( flags );
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

  public DiagnosticCollector getIssues()
  {
    return _issues;
  }
}

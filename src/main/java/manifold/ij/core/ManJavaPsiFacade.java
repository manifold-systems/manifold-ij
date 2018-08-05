package manifold.ij.core;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.JavaPsiFacadeImpl;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.psi.search.GlobalSearchScope;
import manifold.ij.extensions.ManTypeFinder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ManJavaPsiFacade extends JavaPsiFacadeImpl
{
  private final JavaPsiFacadeImpl _delegate;

  ManJavaPsiFacade( JavaPsiFacade delegate )
  {
    super( delegate.getProject(),
           PsiManager.getInstance( delegate.getProject() ),
           JavaFileManager.getInstance( delegate.getProject() ),
           delegate.getProject().getMessageBus() );
    _delegate = (JavaPsiFacadeImpl)delegate;
  }

  @NotNull
  @Override
  public PsiClass[] findClasses( @NotNull String qName, final @NotNull GlobalSearchScope scope )
  {
    PsiClass[] ourClasses = new ManTypeFinder( getProject() ).findClasses( qName, scope );
    if( ourClasses.length > 0 )
    {
      return ourClasses;
    }
    return _delegate.findClasses( qName, scope );
  }

  @NotNull
  @Override
  public PsiClass[] getClasses( @NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope )
  {
    PsiClass[] ourClasses = new ManTypeFinder( getProject() ).getClasses( psiPackage, scope );
    if( ourClasses.length > 0 )
    {
      return ourClasses;
    }
    return _delegate.getClasses( psiPackage, scope );
  }

  @Nullable
  @Override
  public PsiClass findClass( @NotNull String qName, @NotNull GlobalSearchScope scope )
  {
    PsiClass ourClass = new ManTypeFinder( getProject() ).findClass( qName, scope );
    if( ourClass != null )
    {
      return ourClass;
    }
    return _delegate.findClass( qName, scope );
  }
}

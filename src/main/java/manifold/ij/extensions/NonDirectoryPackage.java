package manifold.ij.extensions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.file.PsiPackageImpl;

/**
 */
public class NonDirectoryPackage extends PsiPackageImpl
{
  public NonDirectoryPackage( PsiManager manager, String qualifiedName )
  {
    super( manager, qualifiedName );
  }

  @Override
  public PsiElement copy()
  {
    return new NonDirectoryPackage( getManager(), getQualifiedName() );
  }

  @Override
  public boolean isValid()
  {
    return true;
  }
}
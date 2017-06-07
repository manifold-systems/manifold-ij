package manifold.ij.extensions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.file.PsiPackageImpl;

/**
 */
public class NonDirectoryPackage extends PsiPackageImpl
{
  private final String _name;

  public NonDirectoryPackage( PsiManager manager, String qualifiedName )
  {
    super( manager, qualifiedName );
    _name = qualifiedName;
  }

  @Override
  public PsiElement copy()
  {
    return new NonDirectoryPackage( getManager(), _name );
  }

  @Override
  public boolean isValid()
  {
    return true;
  }

  @Override
  public boolean equals( Object o )
  {
    if( this == o )
    {
      return true;
    }
    if( o == null || getClass() != o.getClass() )
    {
      return false;
    }
    if( !super.equals( o ) )
    {
      return false;
    }

    NonDirectoryPackage that = (NonDirectoryPackage)o;

    return _name.equals( that._name );
  }

  @Override
  public int hashCode()
  {
    int result = super.hashCode();
    result = 31 * result + _name.hashCode();
    return result;
  }
}
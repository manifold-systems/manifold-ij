package manifold.ij.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.light.LightMethod;
import com.intellij.util.IncorrectOperationException;

/**
 */
public class ManLightMethodImpl extends LightMethod implements ManLightMethod
{
  private final PsiMethod _method;

  public ManLightMethodImpl( PsiManager manager, PsiMethod valuesMethod, PsiClass psiClass )
  {
    super( manager, valuesMethod, psiClass );
    _method = valuesMethod;
  }

  @Override
  public ManLightMethod withNavigationElement( PsiElement navigationElement )
  {
    setNavigationElement( navigationElement );
    return this;
  }

  public PsiElement getParent()
  {
    PsiElement result = super.getParent();
    result = null != result ? result : getContainingClass();
    return result;
  }

  public PsiFile getContainingFile()
  {
    PsiClass containingClass = getContainingClass();
    return containingClass != null ? containingClass.getContainingFile() : null;
  }

  public PsiElement copy()
  {
    return new ManLightMethodImpl( myManager, (PsiMethod)_method.copy(), getContainingClass() );
  }

  public ASTNode getNode()
  {
    return _method.getNode();
  }

  @Override
  public PsiElement replace( PsiElement newElement ) throws IncorrectOperationException
  {
    // just add new element to the containing class
    final PsiClass containingClass = getContainingClass();
    if( null != containingClass )
    {
      CheckUtil.checkWritable( containingClass );
      return containingClass.add( newElement );
    }
    return null;
  }

  @Override
  public void delete() throws IncorrectOperationException
  {
  }

  @Override
  public void checkDelete() throws IncorrectOperationException
  {
  }
}

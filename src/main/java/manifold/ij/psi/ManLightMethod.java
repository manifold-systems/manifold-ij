package manifold.ij.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;

/**
 */
public interface ManLightMethod extends PsiMethod
{
  ManLightMethod withNavigationElement( PsiElement navigationElement );
}

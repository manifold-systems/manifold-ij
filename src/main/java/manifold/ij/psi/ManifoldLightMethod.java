package manifold.ij.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;

/**
 */
public interface ManifoldLightMethod extends PsiMethod
{
  ManifoldLightMethod withNavigationElement( PsiElement navigationElement );
}

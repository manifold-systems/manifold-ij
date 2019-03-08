package manifold.ij.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;

/**
 */
public interface ManLightMethod extends PsiMethod
{
  ManLightMethod withNavigationElement( PsiElement navigationElement );

  ManLightMethod withMethodReturnType( PsiType type );

  ManLightMethod withParameter( PsiParameter delegate, PsiType type );
}

package manifold.ij.psi;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;

/**
 */
public interface ManLightFieldBuilder extends PsiField
{
  ManLightFieldBuilder withContainingClass( PsiClass psiClass );

  ManLightFieldBuilder withModifier( String modifier );

  ManLightFieldBuilder withNavigationElement( PsiElement navigationElement );
}

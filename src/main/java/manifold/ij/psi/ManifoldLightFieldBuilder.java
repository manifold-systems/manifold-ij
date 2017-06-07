package manifold.ij.psi;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;

/**
 */
public interface ManifoldLightFieldBuilder extends PsiField
{
  ManifoldLightFieldBuilder withContainingClass( PsiClass psiClass );

  ManifoldLightFieldBuilder withModifier( String modifier );

  ManifoldLightFieldBuilder withNavigationElement( PsiElement navigationElement );
}

package manifold.ij.psi;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.impl.light.LightModifierList;

/**
 */
public interface ManLightFieldBuilder extends PsiField
{
  ManLightFieldBuilder withContainingClass( PsiClass psiClass );

  ManLightFieldBuilder withModifier( String modifier );

  ManLightFieldBuilder withModifierList( LightModifierList modifierList );

  ManLightFieldBuilder withNavigationElement( PsiElement navigationElement );
}

package manifold.ij.psi;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;

/**
 */
public interface ManifoldLightMethodBuilder extends PsiMethod
{
  ManifoldLightMethodBuilder withNavigationElement( PsiElement navigationElement );

  ManifoldLightMethodBuilder withContainingClass( PsiClass containingClass );

  ManifoldLightMethodBuilder withModifier( String modifier );

  ManifoldLightMethodBuilder withMethodReturnType( PsiType returnType );

  ManifoldLightMethodBuilder withParameter( String name, PsiType type );

  ManifoldLightMethodBuilder withException( PsiClassType type );

  ManifoldLightMethodBuilder withException( String fqName );

  ManifoldLightMethodBuilder withTypeParameter( PsiTypeParameter typeParameter );
}

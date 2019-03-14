package manifold.ij.psi;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import java.util.Set;
import manifold.ij.core.ManModule;

/**
 */
public interface ManLightMethodBuilder extends PsiMethod
{
  ManLightMethodBuilder withNavigationElement( PsiElement navigationElement );

  ManLightMethodBuilder withContainingClass( PsiClass containingClass );

  ManLightMethodBuilder withModifier( String modifier );

  ManLightMethodBuilder withMethodReturnType( PsiType returnType );

  ManLightMethodBuilder withParameter( String name, PsiType type );

  ManLightMethodBuilder withException( PsiClassType type );

  ManLightMethodBuilder withException( String fqName );

  ManLightMethodBuilder withTypeParameter( PsiTypeParameter typeParameter );

  ManLightMethodBuilder withAdditionalModule( ManModule module );

  ManModule getModule();
  Set<ManModule> getModules();
}

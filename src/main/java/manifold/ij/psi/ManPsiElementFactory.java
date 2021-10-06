package manifold.ij.psi;

import com.intellij.psi.*;
import manifold.ij.core.ManModule;

/**
 */
public class ManPsiElementFactory
{
  private static ManPsiElementFactory INSTANCE = new ManPsiElementFactory();

  public static ManPsiElementFactory instance()
  {
    return INSTANCE;
  }

  private ManPsiElementFactory()
  {
  }

  public ManLightFieldBuilder createLightField( PsiManager manager, String fieldName, PsiType fieldType, boolean isProperty )
  {
    return new ManLightFieldBuilderImpl( manager, fieldName, fieldType, isProperty );
  }

  public ManLightMethodBuilder createLightMethod( ManModule manModule, PsiManager manager, String methodName )
  {
    return createLightMethod( manModule, manager, methodName, null );
  }
  public ManLightMethodBuilder createLightMethod( ManModule manModule, PsiManager manager, String methodName, PsiModifierList modifierList )
  {
    return new ManLightMethodBuilderImpl( manModule, manager, methodName, modifierList );
  }

  public ManLightMethod createLightMethod( PsiManager manager, PsiMethod valuesMethod, PsiClass psiClass )
  {
    return new ManLightMethodImpl( manager, valuesMethod, psiClass );
  }
}

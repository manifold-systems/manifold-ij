package manifold.ij.psi;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
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

  public ManLightFieldBuilder createLightField( PsiManager manager, String fieldName, PsiType fieldType )
  {
    return new ManLightFieldBuilderImpl( manager, fieldName, fieldType );
  }

  public ManLightMethodBuilder createLightMethod( ManModule manModule, PsiManager manager, String methodName )
  {
    return new ManLightMethodBuilderImpl( manModule, manager, methodName );
  }

  public ManLightMethod createLightMethod( PsiManager manager, PsiMethod valuesMethod, PsiClass psiClass )
  {
    return new ManLightMethodImpl( manager, valuesMethod, psiClass );
  }
}

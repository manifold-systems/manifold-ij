package manifold.ij.psi;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;

/**
 */
public class ManifoldPsiElementFactory
{
  private ManifoldPsiElementFactory()
  {
  }

  private static ManifoldPsiElementFactory ourInstance;

  public static ManifoldPsiElementFactory getInstance()
  {
    if( null == ourInstance )
    {
      ourInstance = new ManifoldPsiElementFactory();
    }
    return ourInstance;
  }

  public ManifoldLightFieldBuilder createLightField( PsiManager manager, String fieldName, PsiType fieldType )
  {
    return new ManifoldLightFieldBuilderImpl( manager, fieldName, fieldType );
  }

  public ManifoldLightMethodBuilder createLightMethod( PsiManager manager, String methodName )
  {
    return new ManifoldLightMethodBuilderImpl( manager, methodName );
  }

  public ManifoldLightMethod createLightMethod( PsiManager manager, PsiMethod valuesMethod, PsiClass psiClass )
  {
    return new ManifoldLightMethodImpl( manager, valuesMethod, psiClass );
  }
}

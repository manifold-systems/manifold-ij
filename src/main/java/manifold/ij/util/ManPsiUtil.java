package manifold.ij.util;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import manifold.ext.api.Structural;

public class ManPsiUtil
{
  public static boolean isStructuralInterface( PsiClass psiClass )
  {
    PsiAnnotation structuralAnno = psiClass == null || psiClass.getModifierList() == null
                                   ? null
                                   : psiClass.getModifierList().findAnnotation( Structural.class.getTypeName() );
    return structuralAnno != null;
  }

}

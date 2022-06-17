package manifold.ij.util;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import manifold.ext.rt.api.Structural;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;

import java.util.concurrent.Callable;

public class ManPsiUtil
{
  public static boolean isStructuralInterface( PsiClass psiClass )
  {
    PsiAnnotation structuralAnno = psiClass == null || psiClass.getModifierList() == null
                                   ? null
                                   : psiClass.getModifierList().findAnnotation( Structural.class.getTypeName() );
    return structuralAnno != null;
  }

  public static void runInTypeManifoldLoader( PsiElement context, Runnable code )
  {
    ManModule module = ManProject.getModule( context );
    if( module == null )
    {
      throw new NullPointerException();
    }
    module.runWithLoader( code );
  }
  public static <R> R runInTypeManifoldLoader( PsiElement context, Callable<R> code )
  {
    ManModule module = ManProject.getModule( context );
    if( module == null )
    {
      throw new NullPointerException();
    }
    return module.runWithLoader( code );
  }
}

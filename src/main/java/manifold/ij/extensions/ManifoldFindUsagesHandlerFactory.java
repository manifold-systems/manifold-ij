package manifold.ij.extensions;

import com.intellij.find.FindBundle;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.JavaFindUsagesHandler;
import com.intellij.find.findUsages.JavaFindUsagesHandlerFactory;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiPackage;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Finds usages from elements in the <i>resource file</i>, as opposed to from code usages
 */
public class ManifoldFindUsagesHandlerFactory extends JavaFindUsagesHandlerFactory
{
  private static final String ACTION_STRING = FindBundle.message( "find.super.method.warning.action.verb" );

  public ManifoldFindUsagesHandlerFactory( Project project )
  {
    super( project );
  }

  @Override
  public boolean canFindUsages( @NotNull PsiElement element )
  {
    List<PsiModifierListOwner> javaElem = ResourceToManifoldUtil.findJavaElementsFor( element );
    return !javaElem.isEmpty() && super.canFindUsages( javaElem.get( 0 ) );
  }

  @Nullable
  @Override
  public FindUsagesHandler createFindUsagesHandler( @NotNull PsiElement element, boolean forHighlightUsages )
  {
    List<PsiModifierListOwner> javaElements = ResourceToManifoldUtil.findJavaElementsFor( element );
    if( javaElements.isEmpty() )
    {
      return null;
    }

    if( element instanceof PsiDirectory )
    {
      final PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage( (PsiDirectory)element );
      return psiPackage == null ? null : new JavaFindUsagesHandler( psiPackage, this );
    }

    if( element instanceof PsiMethod && !forHighlightUsages )
    {
      final PsiMethod[] methods = SuperMethodWarningUtil.checkSuperMethods( (PsiMethod)element, ACTION_STRING );
      if( methods.length > 1 )
      {
        return new JavaFindUsagesHandler( element, methods, this );
      }
      if( methods.length == 1 )
      {
        return new JavaFindUsagesHandler( methods[0], this );
      }
      return FindUsagesHandler.NULL_HANDLER;
    }

    return new JavaFindUsagesHandler( element, this ) {
      @NotNull
      @Override
      public PsiElement[] getPrimaryElements()
      {
        return javaElements.toArray( new PsiElement[javaElements.size()] );
      }
    };
  }

}

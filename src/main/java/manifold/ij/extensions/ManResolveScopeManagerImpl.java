package manifold.ij.extensions;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.file.impl.ResolveScopeManagerImpl;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * Override IJ's default ResolveScopeManagerImpl to handle ManifoldExtendedPsiClass.
 * The resolve scope must match the module of the augmented class, otherwise if a
 * corresponding extension class adds a method where the signature of the method
 * refers to a type in the module, that type will not be accessible because the
 * default behavior of ResolveScopeManagerImpl calls getContainingFile() on
 * ManifoldExtendedPsiClass to get the module, which will be the module of the
 * extended class e.g., String.
 */
public class ManResolveScopeManagerImpl extends ResolveScopeManagerImpl
{
  public ManResolveScopeManagerImpl( Project project, ProjectRootManager projectRootManager, PsiManager psiManager )
  {
    super( project, projectRootManager, psiManager );
  }

  @Override
  @NotNull
  public GlobalSearchScope getResolveScope( @NotNull PsiElement element )
  {
    ProgressIndicatorProvider.checkCanceled();

    if( element instanceof ManifoldExtendedPsiClass )
    {
      return GlobalSearchScope.moduleWithDependenciesScope(
        ((ManifoldExtendedPsiClass)element).getModule().getIjModule() );
    }

    return super.getResolveScope( element );
  }

  @NotNull
  @Override
  public GlobalSearchScope getUseScope( @NotNull PsiElement element )
  {
    if( element instanceof ManifoldExtendedPsiClass )
    {
      Module module = ((ManifoldExtendedPsiClass)element).getModule().getIjModule();
      boolean isTest = element.getContainingFile() != null && element.getContainingFile().getVirtualFile() != null &&
                       TestSourcesFilter.isTestSources( element.getContainingFile().getVirtualFile(), module.getProject() );
      return isTest
             ? GlobalSearchScope.moduleTestsWithDependentsScope( module )
             : GlobalSearchScope.moduleWithDependentsScope( module );
    }

    return super.getUseScope( element );
  }
}

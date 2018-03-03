package manifold.ij.extensions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClassOwnerEx;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.ResolveScopeProvider;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.Set;
import manifold.api.type.ContributorKind;
import manifold.api.type.ITypeManifold;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;

/**
 * Provide resolve scope for extended files.
 * <p/>
 * {@link ManifoldExtendedPsiClass} augments a file that probably lives in a separate module.
 * Basically we need to use the scope of the Extension class, not the extended class.
 * This is mostly so any generic classes referenced in the extension class can resolve properly.
 */
public class ManResolveScopeProvider extends ResolveScopeProvider
{
  @Override
  public GlobalSearchScope getResolveScope( VirtualFile file, Project project )
  {
    ManProject manProject = ManProject.manProjectFrom( project );
    PsiFile psiFile = PsiManager.getInstance( project ).findFile( file );
    if( psiFile instanceof PsiClassOwnerEx )
    {
      PsiClassOwnerEx classFile = (PsiClassOwnerEx)psiFile;
      for( String name : classFile.getClassNames() )
      {
        String fqn = classFile.getPackageName() + '.' + name;
        for( ManModule module : manProject.getModules() )
        {
          if( isTypeExtendedInModule( fqn, module ) )
          {
            return GlobalSearchScope.moduleWithDependenciesAndLibrariesScope( module.getIjModule() );
          }
        }
      }
    }
    else
    {
      for( ManModule module : manProject.getModules() )
      {
        String[] fqns = module.getTypesForFile( manProject.getFileSystem().getIFile( file ) );
        for( String fqn: fqns )
        {
          if( isTypeExtendedInModule( fqn, module ) )
          {
            return GlobalSearchScope.moduleWithDependenciesAndLibrariesScope( module.getIjModule() );
          }
        }
      }

    }
    return null;
  }

  private boolean isTypeExtendedInModule( String fqn, ManModule module )
  {
    Set<ITypeManifold> tms = module.findTypeManifoldsFor( fqn );
    return tms.stream().anyMatch( tm -> tm.getContributorKind() == ContributorKind.Supplemental );
  }
}
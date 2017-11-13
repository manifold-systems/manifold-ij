package manifold.ij.extensions;

import com.intellij.framework.FrameworkTypeEx;
import com.intellij.framework.addSupport.FrameworkSupportInModuleConfigurable;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.jarRepository.RepositoryLibrarySupportInModuleConfigurable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import manifold.internal.host.ManifoldHost;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription;

/**
 */
public class ManFrameworkSupportProvider extends FrameworkSupportInModuleProvider
{
  @NotNull
  @Override
  public FrameworkTypeEx getFrameworkType()
  {
    return FrameworkTypeEx.EP_NAME.findExtension( ManFrameworkType.class );
  }

  @Override
  public boolean isEnabledForModuleBuilder( @NotNull ModuleBuilder builder )
  {
    return super.isEnabledForModuleBuilder( builder );
  }

  @Override
  public boolean isSupportAlreadyAdded( @NotNull Module module, @NotNull FacetsProvider facetsProvider )
  {
    PsiClass psiClass = JavaPsiFacade.getInstance( module.getProject() ).findClass( ManifoldHost.class.getName(), module.getModuleWithDependenciesScope() );
    return psiClass != null;
  }

  @Override
  @NotNull
  public FrameworkSupportInModuleConfigurable createConfigurable( @NotNull final FrameworkSupportModel model )
  {
    return new RepositoryLibrarySupportInModuleConfigurable( model.getProject(), RepositoryLibraryDescription.findDescription( "systems.manifold", "manifold-all" ) )
           {
             @Override
             public void addSupport( @NotNull Module module, @NotNull ModifiableRootModel rootModel, @NotNull ModifiableModelsProvider modifiableModelsProvider )
             {
               super.addSupport( module, rootModel, modifiableModelsProvider );
               ManSupportProvider.addToolsJar( rootModel );
               rootModel.commit();
             }
           };
  }

  @Override
  public boolean isEnabledForModuleType( @NotNull ModuleType moduleType )
  {
    // ? return moduleType instanceof JavaModuleType;
    return true;
  }

}

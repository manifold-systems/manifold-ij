/*
 *
 *  * Copyright (c) 2022 - Manifold Systems LLC
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package manifold.ij.extensions;

import com.intellij.framework.FrameworkTypeEx;
import com.intellij.framework.addSupport.FrameworkSupportInModuleConfigurable;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.jarRepository.RepositoryLibrarySupportInModuleConfigurable;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import manifold.api.host.IManifoldHost;
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
    PsiClass psiClass = JavaPsiFacade.getInstance( module.getProject() ).findClass( IManifoldHost.class.getName(), module.getModuleWithDependenciesAndLibrariesScope( false ) );
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
               
               // also add tools.jar to the SDK if not already present
               ApplicationManager.getApplication().invokeLater( () -> ApplicationManager.getApplication().runWriteAction( () -> {
                 ModifiableRootModel rootModel2 = ModuleRootManager.getInstance( module ).getModifiableModel();
                 ManSupportProvider.addToolsJar( rootModel2 );
                 rootModel2.commit();
               } ) );
             }
           };
  }

  @Override
  public boolean isEnabledForModuleType( @NotNull ModuleType moduleType )
  {
    // ? return moduleType instanceof JavaModuleType;

    // We are using RepositoryLibrarySupportInModuleConfigurable, which is available starting in IDEA v17.x
    return ApplicationInfo.getInstance().getBuild().getBaselineVersion() >= 17;
  }

}

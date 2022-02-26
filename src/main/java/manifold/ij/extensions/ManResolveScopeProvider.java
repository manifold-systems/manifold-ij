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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.ResolveScopeEnlarger;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.Set;

import com.intellij.psi.search.SearchScope;
import manifold.api.type.ContributorKind;
import manifold.api.type.ITypeManifold;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import org.jetbrains.annotations.NotNull;

/**
 * Provide resolve scope for extended files.
 * <p/>
 * {@link ManifoldPsiClass} augments a file that probably lives in a separate module.
 * Basically we need to use the scope of the Extension class, not the extended class.
 * This is mostly so any generic classes referenced in the extension class can resolve properly.
 */
public class ManResolveScopeProvider extends ResolveScopeEnlarger
{
  @Override
  public SearchScope getAdditionalResolveScope( @NotNull VirtualFile file, Project project )
  {
    if( !ManProject.isManifoldInUse( project ) || !file.isValid() )
    {
      return null;
    }

    ManProject manProject = ManProject.manProjectFrom( project );
    PsiFile psiFile = PsiManager.getInstance( project ).findFile( file );
    GlobalSearchScope unionScope = null;
    if( psiFile instanceof PsiClassOwner )
    {
      PsiClassOwner classFile = (PsiClassOwner)psiFile;
      String fqn = classFile.getPackageName() + '.' + FileUtil.getNameWithoutExtension( classFile.getName() );
      for( ManModule module : manProject.getModules().values() )
      {
        unionScope = addScopeIfExtended( fqn, unionScope, module );
      }
    }
    else
    {
      for( ManModule module : manProject.getModules().values() )
      {
        String[] fqns = module.getTypesForFile( manProject.getFileSystem().getIFile( file ) );
        for( String fqn: fqns )
        {
          unionScope = addScopeIfExtended( fqn, unionScope, module );
        }
      }
    }
    return unionScope;
  }

  private GlobalSearchScope addScopeIfExtended( String fqn, GlobalSearchScope unionScope, ManModule module )
  {
    if( isTypeExtendedInModule( fqn, module ) )
    {
      GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope( module.getIjModule() );
      if( unionScope == null )
      {
        unionScope = scope;
      }
      else
      {
        if( !scope.isSearchInModuleContent( module.getIjModule() ) )
        {
          unionScope = unionScope.union( scope );
        }
      }
    }
    return unionScope;
  }

  private boolean isTypeExtendedInModule( String fqn, ManModule module )
  {
    Set<ITypeManifold> tms = module.super_findTypeManifoldsFor( fqn, tm -> tm.getContributorKind() == ContributorKind.Supplemental );
    return !tms.isEmpty();
  }
}
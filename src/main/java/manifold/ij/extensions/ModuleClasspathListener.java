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

/*
 * Manifold
 */

package manifold.ij.extensions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import manifold.ij.core.ManProject;

public class ModuleClasspathListener implements ModuleRootListener
{
  @Override
  public void beforeRootsChange( ModuleRootEvent event )
  {
  }

  @Override
  public void rootsChanged( ModuleRootEvent event )
  {
    Project project = (Project)event.getSource();
    resetProject( project );
  }

  private void resetProject( Project project )
  {
    if( !project.isInitialized() )
    {
      return;
    }

    ManProject.manProjectFrom( project ).reset();
  }
}

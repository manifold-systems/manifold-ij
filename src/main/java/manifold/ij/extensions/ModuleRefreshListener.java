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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;

import java.util.List;
import manifold.ij.core.ManProject;
import org.jetbrains.annotations.NotNull;

public class ModuleRefreshListener implements ModuleListener
{
  public void moduleAdded( Project project, Module ijModule )
  {
    ManProject.manProjectFrom( project ).reset();
  }

  public void beforeModuleRemoved( Project project, Module ijModule )
  {
    ManProject.manProjectFrom( project ).reset();
  }

  public void moduleRemoved( Project project, Module module )
  {
    ManProject.manProjectFrom( project ).reset();
  }

  @Override
  public void modulesRenamed( Project project, @NotNull List<? extends Module> modules, @NotNull Function<? super Module, String> moduleStringFunction )
  {
    ManProject.manProjectFrom( project ).reset();
  }
}

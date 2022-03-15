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
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.openapi.util.IconLoader;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

/**
 * Facilitates adding Manifold manifold-all to a module in an existing project.
 * Similarly, ManSupportProvider supports the same functionality when creating a new project.
 */
public class ManFrameworkType extends FrameworkTypeEx
{
  protected ManFrameworkType()
  {
    super( "manifold" );
  }

  @NotNull
  @Override
  public FrameworkSupportInModuleProvider createProvider()
  {
    return new ManFrameworkSupportProvider();
  }

  @NotNull
  @Override
  public String getPresentableName()
  {
    return "Manifold";
  }

  @NotNull
  @Override
  public Icon getIcon()
  {
    return IconLoader.getIcon( "/manifold/ij/icons/manifold.png", getClass() );
  }
}

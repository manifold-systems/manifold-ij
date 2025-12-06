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

package manifold.ij.jps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.java.ResourcesTargetType;
import org.jetbrains.jps.incremental.BuilderService;
import org.jetbrains.jps.incremental.ModuleLevelBuilder;
import org.jetbrains.jps.incremental.TargetBuilder;

public class ManBuildService extends BuilderService
{
  @NotNull
  @Override
  public List<? extends BuildTargetType<?>> getTargetTypes()
  {
    return new ArrayList<BuildTargetType<?>>( ResourcesTargetType.ALL_TYPES );
  }

  @NotNull
  @Override
  public List <? extends ModuleLevelBuilder> createModuleLevelBuilders()
  {
    return Collections.singletonList( new ManFileFragmentBuilder() );
  }

  @NotNull
  @Override
  public List<? extends TargetBuilder<?, ?>> createBuilders()
  {
    return Collections.singletonList( new ManChangedResourcesBuilder() );
  }
}

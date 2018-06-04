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
  public List<? extends ModuleLevelBuilder> createModuleLevelBuilders()
  {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<? extends TargetBuilder<?, ?>> createBuilders()
  {
    return Collections.singletonList( new ManChangedResourcesBuilder() );
  }
}

package manifold.ij.jps;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import manifold.api.type.IIncrementalCompileDriver;

public class IjIncrementalCompileDriver implements IIncrementalCompileDriver
{
  static IjIncrementalCompileDriver INSTANCE;
  static Collection<File> FILES = null;

  private Map<File, Set<String>> _typesToFile;

  public IjIncrementalCompileDriver()
  {
    INSTANCE = this;
    _typesToFile = new ConcurrentHashMap<>();
  }

  @Override
  public Collection<File> getResourceFiles()
  {
    return FILES;
  }

  @Override
  public void mapTypesToFile( Set<String> set, File iFile )
  {
    _typesToFile.put( iFile, set );
  }
  Map<File, Set<String>> getTypesToFile()
  {
    return _typesToFile;
  }
}

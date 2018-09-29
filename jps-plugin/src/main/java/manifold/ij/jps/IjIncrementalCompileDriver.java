package manifold.ij.jps;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import manifold.api.type.IIncrementalCompileDriver;

public class IjIncrementalCompileDriver implements IIncrementalCompileDriver
{
  /** _Manifold_Temp_Main_.java file name -> compile driver */
  static ThreadLocal<Map<String, IjIncrementalCompileDriver>> INSTANCES = new ThreadLocal<>();
  public static IjIncrementalCompileDriver getInstance( int identityHash )
  {
    for( IjIncrementalCompileDriver driver: INSTANCES.get().values() )
    {
      if( System.identityHashCode( driver ) == identityHash )
      {
        return driver;
      }
    }
    throw new IllegalStateException();
  }
  
  private Collection<File> _files;
  private Map<File, Set<String>> _typesToFile;


  public IjIncrementalCompileDriver()
  {
    _files = new ArrayList<>();
    _typesToFile = new ConcurrentHashMap<>();
  }

  @Override
  public Collection<File> getResourceFiles()
  {
    return _files;
  }

  @Override
  public void mapTypesToFile( Set<String> set, File file )
  {
    _typesToFile.put( file, set );
  }
  Map<File, Set<String>> getTypesToFile()
  {
    return _typesToFile;
  }
}

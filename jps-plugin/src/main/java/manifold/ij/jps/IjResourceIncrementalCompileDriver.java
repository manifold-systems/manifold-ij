package manifold.ij.jps;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import manifold.api.type.IIncrementalCompileDriver;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.incremental.CompileContext;

public class IjResourceIncrementalCompileDriver implements IIncrementalCompileDriver
{
  /** _Manifold_Temp_Main_.java file name -> compile driver */
  static ThreadLocal<Map<String, IjResourceIncrementalCompileDriver>> INSTANCES = new ThreadLocal<>();
  public static IjResourceIncrementalCompileDriver getInstance( int identityHash )
  {
    for( IjResourceIncrementalCompileDriver driver: INSTANCES.get().values() )
    {
      if( System.identityHashCode( driver ) == identityHash )
      {
        return driver;
      }
    }
    throw new IllegalStateException();
  }

  private final CompileContext _context;
  private Collection<File> _files;
  private Map<File, Set<String>> _typesToFile;


  public IjResourceIncrementalCompileDriver( CompileContext context )
  {
    _context = context;
    _files = new ArrayList<>();
    _typesToFile = new ConcurrentHashMap<>();
  }

  public boolean isIncremental()
  {
    return JavaBuilderUtil.isCompileJavaIncrementally( _context );
  }

  @Override
  public Collection<File> getChangedFiles()
  {
    return _files;
  }

  @Override
  public void mapTypesToFile( Set<String> set, File file )
  {
    _typesToFile.put( file, set );
  }

  @Override
  public Map<File, Set<String>> getTypesToFile()
  {
    return _typesToFile;
  }
}

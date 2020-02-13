package manifold.ij.jps;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
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

  /**
   * @param context Context of build, null if rebuild
   */
  public IjResourceIncrementalCompileDriver( CompileContext context )
  {
    _context = context;
    _files = new ArrayList<>();
  }

  public boolean isIncremental()
  {
    return _context != null && JavaBuilderUtil.isCompileJavaIncrementally( _context );
  }

  @Override
  public Collection<File> getChangedFiles()
  {
    return _files;
  }
}

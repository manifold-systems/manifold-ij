package manifold.ij.jps;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import manifold.api.type.IIncrementalCompileDriver;

/**
 * Don't need to manage changed files because file fragments reside in Java source files. Only need to manage the
 * fragment type names associated with their enclosing Java source files so that JPS can register their .class files
 * with the enclosing Java source file (so the .class files will be cleaned on both incremental builds and rebuilds).
 */
public class IjFileFragmentIncrementalCompileDriver implements IIncrementalCompileDriver
{
  private static ThreadLocal<IjFileFragmentIncrementalCompileDriver> INSTANCES =
    ThreadLocal.withInitial( IjFileFragmentIncrementalCompileDriver::new );

  public static IjFileFragmentIncrementalCompileDriver getInstance()
  {
    return INSTANCES.get();
  }

  public static void removeInstance()
  {
    INSTANCES.remove();
  }

  private Map<File, Set<String>> _typesToFile;


  private IjFileFragmentIncrementalCompileDriver()
  {
    _typesToFile = new HashMap<>();
  }

  public boolean isIncremental()
  {
    // don't care about this
    return false;
  }

  @Override
  public Collection<File> getChangedFiles()
  {
    // don't care about this
    return Collections.emptySet();
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

package manifold.ij.jps;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class applies to an entire build (both incremental and full), it contains aggregated information for the
 * currentJPS build.
 * <p/>
 * Note, this class is accessed reflectively from the JavacPlugin.
 * </p>
 * todo: to avoid reflection, make this implement a service that the JavacPlugin can load
 */
@SuppressWarnings("WeakerAccess")
public class IjChangedResourceFiles
{
  private static ThreadLocal<List<File>> ChangedFiles =
    ThreadLocal.withInitial( () -> new ArrayList<>() );

  private static ThreadLocal<Map<File, Set<String>>> TypesToFile =
    ThreadLocal.withInitial( () -> new HashMap<>() );

  /**
   * @return All the changed resource files corresponding with an *entire* incremental build. Used by manifold's
   * ManifoldJavaFileManager to prevent unnecessary recompilation of types -- a resource file must be in this list to
   * recompile as part of the incremental build. Note this list only pertains to an incemental build; it is empty for a
   * rebuild (full bulid) as all types are compiled.
   */
  public static List<File> getChangedFiles()
  {
    return ChangedFiles.get();
  }


  /**
   * @return  Map keyed by resource file to the set of qualified class names, including inner classes, that were
   * compiled/generated as a product of the manifold type[s] produced from the file. This map corresponds with the
   * entire build or rebuild.  This map is used to maintain JPS bookkeeping to cross-tabulate class file and
   * resource/source file e.g., to know which .class files to delete/update for an incremental build, which facilitates
   * hotswap debugging.
   */
  public static Map<File, Set<String>> getTypesToFile()
  {
    return TypesToFile.get();
  }

  /**
   * To be called after a build completes
   */
  public static void clear()
  {
    ChangedFiles.remove();
    TypesToFile.remove();
  }
}
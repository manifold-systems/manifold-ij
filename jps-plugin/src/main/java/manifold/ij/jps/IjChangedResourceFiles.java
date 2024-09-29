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

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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
  private static CopyOnWriteArrayList<File> ChangedFiles = new CopyOnWriteArrayList<>();

  private static ConcurrentHashMap<File, Set<String>> TypesToFile = new ConcurrentHashMap<>();

  /**
   * @return All the changed resource files corresponding with an *entire* incremental build. Used by manifold's
   * ManifoldJavaFileManager to prevent unnecessary recompilation of types -- a resource file must be in this list to
   * recompile as part of the incremental build. Note this list only pertains to an incremental build; it is empty for a
   * rebuild (full build) as all types are compiled.
   */
  public static List<File> getChangedFiles()
  {
    return ChangedFiles;
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
    return TypesToFile;
  }

  /**
   * To be called after a build completes
   */
  public static void clear()
  {
    ChangedFiles = new CopyOnWriteArrayList<>();
    TypesToFile = new ConcurrentHashMap<>();
  }
}
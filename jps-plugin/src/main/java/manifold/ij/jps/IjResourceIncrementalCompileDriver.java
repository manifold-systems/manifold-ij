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

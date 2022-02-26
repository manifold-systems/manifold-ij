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

import com.intellij.compiler.server.BuildProcessParametersProvider;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import java.io.File;
import java.net.URISyntaxException;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public class ManBuildClasspathProvider extends BuildProcessParametersProvider
{
  @NotNull
  @Override
  public List<String> getClassPath()
  {
    return ((PluginClassLoader)getClass().getClassLoader()).getUrls().stream()
      .filter( e -> {
        try
        {
          new File( e.toURI() );
        }
        catch( Exception ue )
        {
          // if the URI is bad or cannot be interpreted as a File, we don't use it
          return false;
        }
        return true;
      } )
      .map( e -> {
        try
        {
          return new File( e.toURI() ).getAbsolutePath();
        }
        catch( URISyntaxException ex )
        {
          // should have been filtered in prior .filter() call
          throw new RuntimeException( ex );
        }
      } ).collect( Collectors.toList() );
  }
}

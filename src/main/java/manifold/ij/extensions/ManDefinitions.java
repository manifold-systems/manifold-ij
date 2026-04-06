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

package manifold.ij.extensions;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import java.io.File;
import java.util.Collections;
import java.util.Map;

import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.util.FileUtil;
import manifold.preprocessor.definitions.Definitions;
import manifold.preprocessor.definitions.EnvironmentDefinitions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * For preprocessor.  Provides a Definitions specific to IDE settings, as opposed to javac settings.
 */
public class ManDefinitions extends Definitions
{
  private static final String MODULE_INFO_FILE = "module-info.java";
  private final Project _project;
  private final VirtualFile _vFile;

  ManDefinitions( Project project, VirtualFile vFile )
  {
    super( FileUtil.toIFile( project, vFile ) );
    _project = project;
    _vFile = vFile;
  }

  public VirtualFile getFile()
  {
    return _vFile;
  }

  public ManModule getModule()
  {
    ManModule module = getModule( _vFile );
    if( module == null )
    {
      // default to project-dir-based module
      module = getModule( ProjectUtil.guessProjectDir( _project ) );
    }
    return module;
  }
  private ManModule getModule( VirtualFile vfile )
  {
    if( vfile == null )
    {
      return null;
    }
    Module moduleForFile = ModuleUtilCore.findModuleForFile( vfile, _project );
    return moduleForFile == null ? null : ManProject.getModule( moduleForFile );
  }

  @Override
  protected Map<String, String> loadJavacDefinitions()
  {
    //## todo: these are javac -Akey[=value] command line options,
    //    get these from IJ's compiler settings?
    return Collections.emptyMap();
  }

  @Override
  protected Map<String, String> loadEnvironmentDefinitions()
  {
    return new IdeEnvironmentDefinitions().getEnv();
  }

  private class IdeEnvironmentDefinitions extends EnvironmentDefinitions
  {
    @Override
    protected void addJavaVersion( Map<String, String> map )
    {
      int major = getJavaVersion();
      makeJavaVersionDefinitions( map, major );
    }

    @Override
    protected void addJpms( Map<String, String> map )
    {
      if( getJavaVersion() < 9 )
      {
        map.put( EnvironmentDefinitions.JPMS_NONE, "" );
      }
      else
      {
        ApplicationManager.getApplication()
          .runReadAction( () -> _addJpms( map ) );
      }
    }

    private void _addJpms( Map<String, String> map )
    {
      if( _vFile != null )
      {
        VirtualFile sourceRoot = ProjectFileIndex.getInstance( _project ).getSourceRootForFile( _vFile );
        if( sourceRoot != null )
        {
          File moduleInfoFile = findModuleInfoFile( sourceRoot, _project );
          if( moduleInfoFile != null )
          {
            map.put( EnvironmentDefinitions.JPMS_NAMED, "" );
            return;
          }
        }
      }
      else
      {
        ManModule module = getModule();
        if( module != null )
        {
          for( VirtualFile sourceRoot: ManProject.getSourceRoots( module.getIjModule() ) )
          {
            File moduleInfoFile = findModuleInfoFile( sourceRoot, module.getIjProject() );
            if( moduleInfoFile != null )
            {
              map.put( EnvironmentDefinitions.JPMS_NAMED, "" );
              return;
            }
          }
        }
      }
      map.put( EnvironmentDefinitions.JPMS_UNNAMED, "" );
    }

    private int getJavaVersion()
    {
      LanguageLevel languageLevel = ApplicationManager.getApplication()
        .runReadAction( (Computable<LanguageLevel>)() -> getLanguageLevel( _vFile, _project ) );
      return languageLevel.toJavaVersion().feature;
    }

    private LanguageLevel getLanguageLevel( VirtualFile file, Project project )
    {
      Module module = ModuleUtilCore.findModuleForFile( file, project );
      return module == null
        ? LanguageLevel.HIGHEST
        : LanguageLevelUtil.getEffectiveLanguageLevel( module );
    }

    private File findModuleInfoFile( VirtualFile root, Project project )
    {
      File file = new File( urlToOsPath( root.getUrl() ), MODULE_INFO_FILE );
      if( file.isFile() && !isExcluded( LocalFileSystem.getInstance().findFileByIoFile( file ), project ) )
      {
        return file;
      }
      return null;
    }

    private boolean isExcluded( final VirtualFile vFile, final Project project )
    {
      return vFile != null
             && project.getService( FileIndexFacade.class ).isInSource( vFile )
             && CompilerConfiguration.getInstance( project ).isExcludedFromCompilation( vFile );
    }

    private String urlToOsPath( @NotNull String url )
    {
      return FileUtilRt.toSystemDependentName( urlToPath( url ) );
    }

    private String urlToPath( @Nullable String url )
    {
      if( url == null )
      {
        return null;
      }
      if( url.startsWith( "file://" ) )
      {
        return url.substring( "file://".length() );
      }
      return url;
    }
  }
}

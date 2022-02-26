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
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiJavaFile;
import java.io.File;
import java.util.Map;

import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiUtil;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.fs.IjFile;
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
  private final ASTNode _chameleon;
  private final SmartPsiElementPointer<PsiJavaFile> _psiFile;

  ManDefinitions( ASTNode chameleon, SmartPsiElementPointer<PsiJavaFile> psiFile )
  {
    super( getFile( psiFile ) );
    _chameleon = chameleon;
    _psiFile = psiFile;
  }

  private static IjFile getFile( SmartPsiElementPointer<PsiJavaFile> psiFile )
  {
    if( psiFile == null )
    {
      return null;
    }
    PsiJavaFile psiJavaFile = psiFile.getElement();
    if( psiJavaFile == null )
    {
      return null;
    }
    return FileUtil.toIFile( psiFile.getProject(), FileUtil.toVirtualFile( psiJavaFile ) );
  }

  public SmartPsiElementPointer<PsiJavaFile> getPsiFile()
  {
    return _psiFile;
  }

  public ManModule getModule()
  {
    if( _psiFile != null )
    {
      PsiJavaFile psiJavaFile = _psiFile.getElement();
      if( psiJavaFile != null )
      {
        return ManProject.getModule( psiJavaFile );
      }
    }
    return ManProject.getModule( _chameleon.getPsi() );
  }

  @Override
  protected Map<String, String> loadEnvironmentDefinitions()
  {
    return new IdeEnvironmentDefinitions().getEnv();
  }

  private class IdeEnvironmentDefinitions extends EnvironmentDefinitions
  {
    @Override
    protected void addJavacEnvironment( Map<String, String> map )
    {
      int major = getJavaVersion();
      makeJavaVersionDefinitions( map, major );

      //## todo: see super class, need to get these from IJ's compiler settings
      //addAnnotationOptions();
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
        PsiJavaFile psiJavaFile;
        if( _psiFile != null && (psiJavaFile = _psiFile.getElement()) != null )
        {
          Project project = _psiFile.getProject();
          VirtualFile sourceRoot = ProjectFileIndex.getInstance( project )
            .getSourceRootForFile( FileUtil.toVirtualFile( psiJavaFile ) );
          if( sourceRoot != null )
          {
            File moduleInfoFile = findModuleInfoFile( sourceRoot, project );
            if( moduleInfoFile != null )
            {
              map.put( EnvironmentDefinitions.JPMS_NAMED, "" );
              return;
            }
          }
        }
        else
        {
          ManModule module = ManProject.getModule( _chameleon.getPsi() );
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
    }

    private int getJavaVersion()
    {
      PsiJavaFile psiJavaFile;
      LanguageLevel languageLevel = _psiFile != null && (psiJavaFile = _psiFile.getElement()) != null
        ? psiJavaFile.getLanguageLevel()
        : PsiUtil.getLanguageLevel( _chameleon.getPsi() );
      return languageLevel.toJavaVersion().feature;
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

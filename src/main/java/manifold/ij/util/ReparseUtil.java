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

package manifold.ij.util;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.util.FileContentUtil;
import com.intellij.util.FileContentUtilCore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import manifold.ij.core.ManProject;
import manifold.util.concurrent.ConcurrentHashSet;
import org.jetbrains.annotations.NotNull;

public class ReparseUtil
{
  private static ReparseUtil INSTANCE = null;

  public static ReparseUtil instance()
  {
    return INSTANCE == null ? INSTANCE = new ReparseUtil() : INSTANCE;
  }

  private final Set<Project> _reparsingProjects;
  private final Set<VirtualFile> _repsarsingFiles;

  private ReparseUtil()
  {
    _reparsingProjects = new ConcurrentHashSet<>();
    _repsarsingFiles = new ConcurrentHashSet<>();
  }

  public boolean isReparsing( Project project )
  {
    return _reparsingProjects.contains( project );
  }

  public boolean isReparsing( VirtualFile file )
  {
    return _repsarsingFiles.contains( file );
  }

  public void rerunAnnotators( @NotNull PsiFile psiFile )
  {
    ApplicationManager.getApplication().invokeLater(
      () -> {
        try
        {
          if( DaemonCodeAnalyzerEx.getInstance( psiFile.getProject() ).isHighlightingAvailable( psiFile ) )
          {
            DaemonCodeAnalyzer.getInstance( psiFile.getProject() ).restart( psiFile );
          }
        }
        catch( PsiInvalidElementAccessException ieae )
        {
          // not throwing on purpose,
          ieae.printStackTrace();
        }
      } );
  }

  public void reparseOpenJavaFilesForAllProjects()
  {
    for( Project project: ProjectManager.getInstance().getOpenProjects() )
    {
      reparseRecentJavaFiles( project );
    }
  }

  public void reparseRecentJavaFiles( @NotNull Project project )
  {
    reparseRecentJavaFiles( project, false );
  }
  public void reparseRecentJavaFiles( @NotNull Project project, boolean force )
  {
    if( project.isDisposed() )
    {
      return;
    }

    ManProject manProject = ManProject.manProjectFrom( project );
    if( manProject == null ||
      !force && !manProject.isPreprocessorEnabledInAnyModules() ||
      isReparsing( project ) )
    {
      // manifold-preprocessor is not used in this project, no need to reparse
      return;
    }

    _reparsingProjects.add( project ); // add here to prevent more reparse calls before invokeLater is processed
    try
    {
      ApplicationManager.getApplication().invokeLater(
        () -> {
          try
          {
            ApplicationManager.getApplication().runReadAction(
              () -> {
                if( !project.isDisposed() )
                {
                  // reparse recent files (except module-info.java files because that causes infinite reset)
                  Collection<? extends VirtualFile> openJavaFiles = getRecentJavaFiles( project ).stream()
                    .filter( vf -> !vf.getName().toLowerCase().endsWith( "module-info.java" ) )
                    .limit( 25 )
                    .collect( Collectors.toSet() );
                  FileContentUtil.reparseFiles( project, openJavaFiles, false );
                }
              } );
          }
          finally
          {
            _reparsingProjects.remove( project );
          }
        } );
    }
    catch( Throwable t )
    {
      // if invokeLater() throws before it runs
      _reparsingProjects.remove( project );
    }
  }

  public void reparseFile( @NotNull Project project, @NotNull VirtualFile file )
  {
    if( isReparsing( project ) || isReparsing( file ) )
    {
      return;
    }

    _repsarsingFiles.add( file ); // add here to prevent more reparse calls before invokeLater is processed
    try
    {
      ApplicationManager.getApplication().invokeLater(
        () -> {
          try
          {
            ApplicationManager.getApplication().runReadAction(
              () -> {
                if( !project.isDisposed() )
                {
                  FileContentUtilCore.reparseFiles( file );
                }
              } );
          }
          finally
          {
            _repsarsingFiles.remove( file );
          }
        } );
    }
    catch( Throwable t )
    {
      _repsarsingFiles.remove( file );
    }
  }

  private static Collection<? extends VirtualFile> getRecentJavaFiles( Project project )
  {
    List<VirtualFile> historyFiles = EditorHistoryManager.getInstance( project ).getFileList();
    List<VirtualFile> recentFiles = new ArrayList<>();
    for( int i = historyFiles.size() - 1; i >= 0; i-- )
    {
      VirtualFile vfile = historyFiles.get( i );
      if( "java".equalsIgnoreCase( vfile.getExtension() ) )
      {
        recentFiles.add( vfile );
      }
    }
    return recentFiles;
// used to return open java files, but if a file is opened in an editor then closed, it still needs to be reparsed, hence the change to using recent files
//    return Arrays.stream( FileEditorManager.getInstance( project ).getOpenFiles() )
//      .filter( vfile -> "java".equalsIgnoreCase( vfile.getExtension() ) )
//      .collect( Collectors.toSet() );
  }
}

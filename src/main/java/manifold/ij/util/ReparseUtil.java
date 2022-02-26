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
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.util.FileContentUtil;
import com.intellij.util.FileContentUtilCore;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

import manifold.ij.core.ManProject;
import org.jetbrains.annotations.NotNull;

public class ReparseUtil
{
  public static void rerunAnnotators( @NotNull PsiFile psiFile )
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

  public static void reparseOpenJavaFilesForAllProjects()
  {
    for( Project project: ProjectManager.getInstance().getOpenProjects() )
    {
      reparseOpenJavaFiles( project );
    }
  }

  public static void reparseOpenJavaFiles( @NotNull Project project )
  {
    if( project.isDisposed() )
    {
      return;
    }

    ManProject manProject = ManProject.manProjectFrom( project );
    if( manProject == null || !manProject.isPreprocessorEnabledInAnyModules() )
    {
      // manifold-preprocessor is not used in this project, no need to reparse
      return;
    }

    ApplicationManager.getApplication().invokeLater(
      () -> ApplicationManager.getApplication().runReadAction(
        () -> {
          if( !project.isDisposed() )
          {
            // reparse open files (except module-info.java files because that causes infinite reset)
            Collection<? extends VirtualFile> openJavaFiles = getOpenJavaFiles( project ).stream()
              .filter( vf -> !vf.getName().toLowerCase().endsWith( "module-info.java" ) ).collect( Collectors.toSet() );
            FileContentUtil.reparseFiles( project, openJavaFiles, false );
          }
        } ) );
  }

  public static void reparseFile( @NotNull VirtualFile file )
  {
    ApplicationManager.getApplication().invokeLater(
      () -> ApplicationManager.getApplication().runReadAction(
        () -> FileContentUtilCore.reparseFiles( file ) ) );
  }

  private static Collection<? extends VirtualFile> getOpenJavaFiles( Project project )
  {
    return Arrays.stream( FileEditorManager.getInstance( project ).getOpenFiles() )
      .filter( vfile -> "java".equalsIgnoreCase( vfile.getExtension() ) )
      .collect( Collectors.toSet() );
  }
}

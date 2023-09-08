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

package manifold.ij.core;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
import manifold.ij.util.ReparseUtil;
import manifold.preprocessor.definitions.Definitions;
import org.jetbrains.annotations.NotNull;

/**
 * For preprocessor and dbconfig.  When a build.properties or *.dbconfig file is saved, open Java files reparse.
 *
 * todo: make the conditions for reparsing, currently dbconfig and build.properties, pluggable.
 * todo: even better: if manifold IModel had concept of model dependencies, we could determine exactly the set of files
 *  that need to reparse instead of reparsing everything. We could also reparse non-open files, because we need to if the
 *  file was opened then closed because IJ does not retokenize when reopening a file.
 */
class ReparseFileTrigger implements FileDocumentManagerListener
{
  private final Project _ijProject;

  ReparseFileTrigger( Project ijProject )
  {
    _ijProject = ijProject;
  }

  @Override
  public void beforeDocumentSaving( @NotNull Document document )
  {
    maybeReparseOpenJavaFiles( document );
  }

  @Override
  public void fileContentReloaded( @NotNull VirtualFile file, @NotNull Document document )
  {
    maybeReparseOpenJavaFiles( document );
  }

  @Override
  public void fileContentLoaded( @NotNull VirtualFile file, @NotNull Document document )
  {
    maybeReparseOpenJavaFiles( document );
  }

  private void maybeReparseOpenJavaFiles( @NotNull Document document )
  {
    if( shouldReparse( document ) )
    {
      ReparseUtil.instance().reparseRecentJavaFiles( _ijProject );
    }
  }

  private boolean shouldReparse( Document document )
  {
    VirtualFile vfile = FileDocumentManager.getInstance().getFile( document );
    if( vfile == null || vfile instanceof LightVirtualFile )
    {
      // we check for LightVirtualFile because if that's the case IJ loses its mind if two or more projects are open
      // because a light vfile can only belong to one project, so our next call to PsiDocumentManager.getInstance( _project ).getPsiFile
      // below would otherwise log an ugly error (but not throw), thus we avoid the ugly error here
      return false;
    }

    try
    {
      PsiFile psiFile = PsiDocumentManager.getInstance( _ijProject ).getPsiFile( document );
      if( psiFile != null )
      {
        String fileExt = vfile.getExtension();
        if( fileExt != null && fileExt.equalsIgnoreCase( "dbconfig" ) )
        {
          // DbConfig file changed
          return true;
        }
        else if( Definitions.BUILD_PROPERTIES.equalsIgnoreCase( vfile.getName() ) )
        {
          // Build.properties file changed
          return true;
        }
      }
    }
    catch( Throwable ignore )
    {
      // NPE and other exceptions can happen as a result of calling getPsiFile():
      // - for some reason due to "Recursive file view provider creation"
      // - "Light files should have PSI only in one project"
    }
    return false;
  }
}

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

package manifold.ij.actions;

import com.intellij.ide.actions.CreateFileFromTemplateAction;
import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.json.JsonFileType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiDirectory;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import manifold.ij.core.ManLibraryChecker;
import manifold.ij.core.ManProject;
import manifold.ij.template.psi.ManTemplateFile;
import manifold.ij.util.ManBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 */
public class CreateManifoldTemplateAction extends CreateFileFromTemplateAction
{
  @NonNls
  private static final String DEFAULT_PROP = "DefaultManTLTemplate";

  private static final Map<String, String> FT_TO_EXT = new HashMap<>();

  static
  {
    FT_TO_EXT.put( "HTML ManTL File", "html" );
    FT_TO_EXT.put( "XML ManTL File",  "xml" );
    FT_TO_EXT.put( "JSON ManTL File", "json" );
    FT_TO_EXT.put( "Text ManTL File", "txt" );
  }

  @Override
  public void update( @NotNull AnActionEvent e )
  {
    Project project = e.getProject();
    if( project == null )
    {
      return;
    }

    if( !ManProject.isManifoldInUse( project ) )
    {
//      // Manifold jars are not used in the project
//      ManLibraryChecker.instance().warnFeatureRequiresManifold( e.getProject() );
      e.getPresentation().setEnabled( false );
      return;
    }

    super.update( e );
    e.getPresentation().setEnabledAndVisible( true );
  }

  public CreateManifoldTemplateAction()
  {
    super( ManBundle.message( "new.template.menu.action.text" ),
      ManBundle.message( "new.template.menu.action.description" ),
      IconLoader.getIcon( "/manifold/ij/icons/manifold.png", CreateManifoldTemplateAction.class ) );
  }

  @Override
  protected String getDefaultTemplateProperty()
  {
    return DEFAULT_PROP;
  }

  @Override
  protected void buildDialog( Project project, PsiDirectory directory, CreateFileFromTemplateDialog.Builder builder )
  {
    FT_TO_EXT.forEach( ( k, v ) -> addFileTemplate( project, k, "mtl" ) );

    builder
      .setTitle( ManBundle.message( "new.template.file.action.dialog.title" ) )
      .addKind( "HTML", StdFileTypes.HTML.getIcon(), "HTML ManTL File" )
      .addKind( "XML", StdFileTypes.XML.getIcon(), "XML ManTL File" )
      .addKind( "JSON", JsonFileType.INSTANCE.getIcon(), "JSON ManTL File" )
      .addKind( "Plain Text", StdFileTypes.PLAIN_TEXT.getIcon(), "Text ManTL File" );
  }

  @Override
  protected String getActionName( PsiDirectory directory, @NotNull String newName, String templateName )
  {
    return ManBundle.message( "new.template.progress.text" );
  }

  @Override
  protected ManTemplateFile createFile( String name, String templateName, PsiDirectory dir )
  {
    FileTemplate template = FileTemplateManager.getInstance( dir.getProject() ).getTemplate( templateName );
    ManTemplateFile mantlFile = (ManTemplateFile)createFileFromTemplate( name + '.' + FT_TO_EXT.get( templateName ), template, dir );
    ManLibraryChecker.instance().warnIfManifoldTemplatesNotConfiguredForProject( mantlFile );
    return mantlFile;
  }

  @Override
  public int hashCode()
  {
    return 0;
  }

  private void addFileTemplate( Project project, @NonNls String name, @NonNls String ext )
  {
    FileTemplate template = FileTemplateManager.getInstance( project ).getTemplate( name );
    if( template == null )
    {
      template = FileTemplateManager.getInstance( project ).addTemplate( name, ext );
      template.setText( FileUtil.loadTextAndClose(
        new InputStreamReader( getClass().getResourceAsStream(
          '/' + getClass().getPackage().getName().replace( '.', '/' ) + "/fileTemplates/"
          + name + "." + ext + ".ft" ) ) ) );
    }
  }
}

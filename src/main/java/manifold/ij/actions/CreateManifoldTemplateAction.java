package manifold.ij.actions;

import com.intellij.ide.actions.CreateFileFromTemplateAction;
import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.json.JsonFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import javax.swing.ImageIcon;
import manifold.ij.util.ManBundle;
import org.jetbrains.annotations.NonNls;

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

  public CreateManifoldTemplateAction()
  {
    super( ManBundle.message( "new.template.menu.action.text" ),
      ManBundle.message( "new.template.menu.action.description" ),
      IconLoader.getIcon( "/manifold/ij/icons/manifold.png" ) );
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
  protected String getActionName( PsiDirectory directory, String newName, String templateName )
  {
    return ManBundle.message( "new.template.progress.text" );
  }

  @Override
  protected PsiFile createFile( String name, String templateName, PsiDirectory dir )
  {
    FileTemplate template = FileTemplateManager.getInstance( dir.getProject() ).getTemplate( templateName );
    return createFileFromTemplate( name + '.' + FT_TO_EXT.get( templateName ), template, dir );
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
      try
      {
        template = FileTemplateManager.getInstance( project ).addTemplate( name, ext );
        template.setText( FileUtil.loadTextAndClose(
          new InputStreamReader( getClass().getResourceAsStream(
            '/' + getClass().getPackage().getName().replace( '.', '/' ) + "/fileTemplates/"
            + name + "." + ext + ".ft" ) ) )
        );
      }
      catch( IOException e )
      {
        throw new RuntimeException( e );
      }
    }
  }
}

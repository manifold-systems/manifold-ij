package manifold.ij.actions;

import com.google.common.io.CharSink;
import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.IncorrectOperationException;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import manifold.ij.icons.manifold_20_png;
import manifold.ij.util.ManBundle;

/**
 */
public class CreateExtensionMethodsClassAction extends AnAction implements DumbAware
{
  public CreateExtensionMethodsClassAction()
  {
    super( ManBundle.message( "new.ext.method.class.menu.action.text" ),
           ManBundle.message( "new.ext.method.class.menu.action.description" ),
           manifold_20_png.get() );
  }

  @Override
  public void update( AnActionEvent e )
  {
    super.update( e );

    boolean enabled;
    final DataContext dataContext = e.getDataContext();

    final IdeView view = LangDataKeys.IDE_VIEW.getData( dataContext );
    if( view == null )
    {
      enabled = false;
    }
    else
    {
      final Project project = PlatformDataKeys.PROJECT.getData( dataContext );

      final PsiDirectory dir = view.getOrChooseDirectory();
      if( dir == null || project == null )
      {
        enabled = false;
      }
      else
      {
        PsiPackage pkg = JavaDirectoryService.getInstance().getPackage( dir );
        enabled = pkg != null;
      }
    }
    e.getPresentation().setEnabled( enabled );
  }

  public void actionPerformed( AnActionEvent e )
  {
    final DataContext dataContext = e.getDataContext();

    final IdeView view = LangDataKeys.IDE_VIEW.getData( dataContext );
    if( view == null )
    {
      return;
    }

    final Project project = PlatformDataKeys.PROJECT.getData( dataContext );

    final PsiDirectory dir = view.getOrChooseDirectory();
    if( dir == null || project == null )
    {
      return;
    }

    final CreateExtensionMethodClassDialog.Builder builder = CreateExtensionMethodClassDialog.createDialog( project );
    builder.setTitle( ManBundle.message( "action.create.new.extension.class" ) );

    final PsiFile createdElement =
      builder.show( ManBundle.message( "error.new.class.dlg.title" ), new CreateExtensionMethodClassDialog.FileCreator<PsiFile>()
      {
        public PsiFile createFile( String name, String fqnExtended )
        {
          return doCreate( dir, name, fqnExtended );
        }

        public String getActionName( String name )
        {
          return CreateExtensionMethodsClassAction.this.getActionName( dir, name );
        }
      } );
    if( createdElement != null )
    {
      view.selectElement( createdElement );
    }
  }

  private String getActionName( PsiDirectory directory, String newName )
  {
    PsiPackage pkg = JavaDirectoryService.getInstance().getPackage( directory );
    return ManBundle.message( "new.ext.method.class.progress.text", pkg.getQualifiedName(), newName );
  }

  private PsiFile doCreate( PsiDirectory dir, String className, String fqnExtended ) throws IncorrectOperationException
  {
    String fileName = className + ".java";
    VirtualFile srcRoot = ProjectRootManager.getInstance( dir.getProject() ).getFileIndex().getSourceRootForFile( dir.getVirtualFile() );
    dir = getPsiDirectoryForExtensionClass( dir, fqnExtended, srcRoot );

    final PsiPackage pkg = JavaDirectoryService.getInstance().getPackage( dir );
    if( pkg == null )
    {
      throw new IncorrectOperationException( ManBundle.message( "error.new.artifact.nopackage" ) );
    }

    String text =
      "package " + pkg.getQualifiedName() + ";\n" +
      "\n" +
      "import manifold.ext.api.Extension;\n" +
      "import manifold.ext.api.This;\n" +
      "import " + fqnExtended + ";\n" +
      "\n" +
      "@Extension\n" +
      "public class " + className + " {\n" +
      "  public static void helloWorld(@This " + ClassUtil.extractClassName( fqnExtended ) + " thiz) {\n" +
      "    System.out.println(\"hello world!\");\n" +
      "  }\n" +
      "}";

    dir.checkCreateFile( fileName );

    final PsiFile file = dir.createFile( fileName );

    try
    {
      new CharSink()
      {
        public Writer openStream() throws IOException
        {
          return new OutputStreamWriter( file.getVirtualFile().getOutputStream( null ) );
        }
      }.write( text );
    }
    catch( IOException e )
    {
      throw new IncorrectOperationException( e.getMessage(), (Throwable)e );
    }

    return file;
  }

  private PsiDirectory getPsiDirectoryForExtensionClass( PsiDirectory dir, String fqnExtended, VirtualFile srcRoot )
  {
    String srcDir = srcRoot.getPath().replace( '/', '\\' );
    File pkg = new File( srcDir, "extensions" + File.separatorChar + fqnExtended.replace( '.', File.separatorChar ) );
    //noinspection ResultOfMethodCallIgnored
    pkg.mkdirs();
    VirtualFile pkgFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile( pkg );
    dir = dir.getManager().findDirectory( pkgFile );
    return dir;
  }
}

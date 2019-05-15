package manifold.ij.actions;

import com.google.common.io.CharSink;
import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexUtil;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import javax.swing.RootPaneContainer;
import manifold.ExtIssueMsg;
import manifold.ij.core.ManLibraryChecker;
import manifold.ij.core.ManProject;
import manifold.ij.extensions.StubBuilder;
import manifold.ij.util.ManBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

/**
 */
public class CreateExtensionMethodsClassAction extends AnAction implements DumbAware
{
  public CreateExtensionMethodsClassAction()
  {
    super( ManBundle.message( "new.ext.method.class.menu.action.text" ),
      ManBundle.message( "new.ext.method.class.menu.action.description" ),
      IconLoader.getIcon( "/manifold/ij/icons/manifold.png" ) );
  }

  @Override
  public void update( @NotNull AnActionEvent e )
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
        ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance( project ).getFileIndex();
        enabled = pkg != null && projectFileIndex.isUnderSourceRootOfType( dir.getVirtualFile(), JavaModuleSourceRootTypes.SOURCES );
      }
    }
    e.getPresentation().setEnabled( enabled );
  }

  public void actionPerformed( @NotNull AnActionEvent e )
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

    if( !ManProject.isManifoldInUse( project ) )
    {
      // Manifold jars are not used in the project
      ManLibraryChecker.instance().warnFeatureRequiresManifold( project );
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
    return ManBundle.message( "new.ext.method.class.progress.text", pkg == null ? "" : pkg.getQualifiedName(), newName );
  }

  private PsiFile doCreate( PsiDirectory dir, String className, String fqnExtended ) throws IncorrectOperationException
  {
    Project project = dir.getProject();

    // Just in case other stuff needs indexing
    DumbService.getInstance( project ).completeJustSubmittedTasks();
    
    String fileName = className + ".java";
    VirtualFile srcRoot = ProjectRootManager.getInstance( project ).getFileIndex().getSourceRootForFile( dir.getVirtualFile() );
    if( srcRoot == null )
    {
      throw new IncorrectOperationException( "Directory '" + dir.getName() + "' is not in a source root" );
    }

    dir = getPsiDirectoryForExtensionClass( dir, fqnExtended, srcRoot );

    // The dir we just created causes the system to enter "Dumb" mode for indexing the new directory.
    // Must let indexing finish (in dumb mode) before proceeding.
    DumbService.getInstance( project ).completeJustSubmittedTasks();

    // Just in case other stuff needs indexing
    DumbService.getInstance( project ).completeJustSubmittedTasks();

    final PsiPackage pkg = JavaDirectoryService.getInstance().getPackage( dir );
    if( pkg == null )
    {
      throw new IncorrectOperationException( ManBundle.message( "error.new.artifact.nopackage" ) );
    }

    PsiClass psiExtended = JavaPsiFacade.getInstance( project ).findClass( fqnExtended, GlobalSearchScope.projectScope( project ) );
    if( psiExtended != null &&
        FileIndexUtil.isJavaSourceFile( project, psiExtended.getContainingFile().getVirtualFile() ) &&
        ManProject.getIjModule( psiExtended ) == ManProject.getIjModule( dir ) )
    {
      warningCannotExtendSourceFile( fqnExtended );
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
      "  public static " + processTypeVars( dir, fqnExtended, StubBuilder::makeTypeVar ) + "void helloWorld(@This " + ClassUtil.extractClassName( fqnExtended ) + processTypeVars( dir, fqnExtended, PsiNamedElement::getName ) + " thiz) {\n" +
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

  private void warningCannotExtendSourceFile( String fqnExtended )
  {
    String message = ExtIssueMsg.MSG_CANNOT_EXTEND_SOURCE_FILE.get( fqnExtended );
    JBPopupFactory popupFactory = JBPopupFactory.getInstance();
    popupFactory.createHtmlTextBalloonBuilder( message, MessageType.WARNING, null )
      .setCloseButtonEnabled( true )
      .createBalloon()
      .show( ((RootPaneContainer)((WindowManagerEx)WindowManager.getInstance()).getMostRecentFocusedWindow()).getLayeredPane() );
  }

  private String processTypeVars( PsiDirectory dir, String fqnExtended, Function<PsiTypeParameter, String> processor )
  {
    boolean alt = false;
    DumbService dumbService = DumbService.getInstance( dir.getProject() );
    if( dumbService.isDumb() )
    {
      dumbService.setAlternativeResolveEnabled( alt = true );
    }
    try
    {
      PsiClass extendedClass = JavaPsiFacadeEx.getInstanceEx( dir.getProject() ).findClass( fqnExtended );
      if( extendedClass == null )
      {
        return "";
      }
      PsiTypeParameter[] typeParameters = extendedClass.getTypeParameters();
      if( typeParameters.length == 0 )
      {
        return "";
      }

      StringBuilder sb = new StringBuilder();
      sb.append( "<" );
      for( int i = 0; i < typeParameters.length; i++ )
      {
        PsiTypeParameter tp = typeParameters[i];
        if( i > 0 )
        {
          sb.append( ", " );
        }
        sb.append( processor.fun( tp ) );
      }
      sb.append( "> " );
      return sb.toString();
    }
    finally
    {
      if( alt )
      {
        dumbService.setAlternativeResolveEnabled( false );
      }
    }
  }

  private PsiDirectory getPsiDirectoryForExtensionClass( PsiDirectory dir, String fqnExtended, VirtualFile srcRoot )
  {
    String srcDir = srcRoot.getPath().replace( '/', File.separatorChar );
    File pkg = new File( srcDir,
      IdentifierTextField.makeValidIdentifier( dir.getProject().getName(), true, true )
        .replace( '.', File.separatorChar ) + File.separatorChar + "extensions" + File.separatorChar + fqnExtended.replace( '.', File.separatorChar ) );
    //noinspection ResultOfMethodCallIgnored
    pkg.mkdirs();
    VirtualFile pkgFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile( pkg );
    if( pkgFile == null )
    {
      throw new IncorrectOperationException( "Could not find file for '" + pkg.getAbsolutePath() + "'" );
    }
    dir = dir.getManager().findDirectory( pkgFile );
    return dir;
  }
}

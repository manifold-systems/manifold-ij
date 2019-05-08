package manifold.ij.actions;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import java.util.Arrays;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.extensions.ManifoldPsiClass;
import manifold.ij.extensions.ManifoldPsiClassCache;
import manifold.ij.util.FileUtil;
import org.jetbrains.annotations.NotNull;

public class ViewJavaSourceAction extends AnAction implements DumbAware
{
  public ViewJavaSourceAction()
  {
    super( "Show Java Source", "Show Java Source", AllIcons.Toolwindows.Documentation );
  }

  @Override
  public void update( @NotNull AnActionEvent e )
  {
    Presentation presentation = e.getPresentation();
    presentation.setEnabled( getPsiClass( e ) != null );
  }

  private static ManifoldPsiClass getPsiClass( AnActionEvent e )
  {
    Project project = e.getProject();
    if( project != null )
    {
      Editor editor = FileEditorManager.getInstance( project ).getSelectedTextEditor();
      if( editor != null )
      {
        VirtualFile file = FileDocumentManager.getInstance().getFile( editor.getDocument() );
        if( file != null )
        {
          Module module = ModuleUtilCore.findModuleForFile( file, e.getProject() );
          if( module != null )
          {
            ManModule manModule = ManProject.getModule( module );
            String[] fqns = manModule.getTypesForFile( FileUtil.toIFile( manModule.getIjProject(), file ) );
            return Arrays.stream( fqns ).filter( fqn -> {
              PsiClass psiClass = ManifoldPsiClassCache.getPsiClass( manModule, fqn );
              return psiClass instanceof ManifoldPsiClass;
            } ).map( fqn -> (ManifoldPsiClass)ManifoldPsiClassCache.getPsiClass( manModule, fqn ) )
              .findFirst().orElse( null );
          }
        }
      }
    }
    return null;
  }

  @Override
  public void actionPerformed( @NotNull AnActionEvent e )
  {
    ManifoldPsiClass psiClass = getPsiClass( e );
    assert psiClass != null; // action should've been disabled in this case

    Project project = getEventProject( e );
    assert project != null;
    final JavaSourceViewerComponent component = new JavaSourceViewerComponent( project );
    component.setText( psiClass.getDelegate().getContainingFile().getText() );

    JBPopup popup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder( component, component.getEditor().getComponent() )
      .setProject( project )
      .setResizable( true )
      .setMovable( true )
      .setRequestFocus( LookupManager.getActiveLookup( component.getEditor() ) == null )
      .setTitle( "Java Source for '" + psiClass.getName() + "'" )
      .createPopup();
    Disposer.register( popup, component );

    popup.showInBestPositionFor( e.getDataContext() );
  }
}
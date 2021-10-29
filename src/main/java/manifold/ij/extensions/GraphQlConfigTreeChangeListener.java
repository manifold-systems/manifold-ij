package manifold.ij.extensions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.*;
import com.intellij.util.FileContentUtil;
import manifold.ij.core.ManProject;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class GraphQlConfigTreeChangeListener extends PsiTreeChangeAdapter
{
  @Override
  public void childAdded( @NotNull PsiTreeChangeEvent event )
  {
    handleChange( event );
  }

  @Override
  public void childRemoved( @NotNull PsiTreeChangeEvent event )
  {
    handleChange( event );
  }

  @Override
  public void childReplaced( @NotNull PsiTreeChangeEvent event )
  {
    handleChange( event );
  }

  @Override
  public void childMoved( @NotNull PsiTreeChangeEvent event )
  {
    handleChange( event );
  }

  @Override
  public void childrenChanged( @NotNull PsiTreeChangeEvent event )
  {
    handleChange( event );
  }

  @Override
  public void propertyChanged( @NotNull PsiTreeChangeEvent event )
  {
    handleChange( event );
  }

  private void handleChange( PsiTreeChangeEvent event )
  {
    ApplicationManager.getApplication().invokeLater( () -> {
      try
      {
        PsiFile file = event.getFile();
        if( file != null )
        {
          if( "graphqlconfig".equalsIgnoreCase( file.getVirtualFile().getExtension() ))
          {
            Document document = PsiDocumentManager.getInstance( file.getProject() ).getDocument( file );
            if( document != null )
            {
              FileDocumentManager.getInstance().saveDocument( document );

              ManProject.manProjectFrom( file.getProject() ).reset();
//            psiFile.getManager().dropPsiCaches();
              ApplicationManager.getApplication().invokeLater( () ->
                FileContentUtil.reparseFiles( file.getProject(), Collections.emptyList(), true ) );
//            ((PsiModificationTrackerImpl)psiFile.getManager().getModificationTracker()).incCounter();
              //GqlScopeFinder.refresh();
            }
          }
        }
      }
      catch( Throwable ignore )
      {
        // NPE and other exceptions can happen as a result of calling getPsiFile():
        // - for some reason due to "Recursive file view provider creation"
        // - "Light files should have PSI only in one project"
      }
    } );
  }
}

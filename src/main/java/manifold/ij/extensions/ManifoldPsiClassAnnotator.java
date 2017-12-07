package manifold.ij.extensions;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;

/**
 */
public class ManifoldPsiClassAnnotator implements Annotator
{
  @Override
  public void annotate( PsiElement element, AnnotationHolder holder )
  {
    PsiClass psiClass =  ResourceToManifoldUtil.findPsiClass( element.getContainingFile() );
    if( psiClass == null )
    {
      return;
    }

    if( PsiErrorClassUtil.isErrorClass( psiClass ) && element instanceof PsiFileSystemItem )
    {
      holder.createErrorAnnotation( new TextRange( 0, element.getContainingFile().getTextLength() ), PsiErrorClassUtil.getErrorFrom( psiClass ).getMessage() );
      return;
    }

    if( !(psiClass instanceof ManifoldPsiClass) )
    {
      return;
    }

    DiagnosticCollector issues = ((ManifoldPsiClass)psiClass).getIssues();
    if( issues == null )
    {
      return;
    }

    for( Object obj : issues.getDiagnostics() )
    {
      Diagnostic issue = (Diagnostic)obj;

      if( element.getTextOffset() > issue.getStartPosition() ||
          element.getTextOffset() + element.getTextLength() <= issue.getStartPosition() )
      {
        continue;
      }

      PsiElement deepestElement = element.getContainingFile().findElementAt( (int)issue.getStartPosition() );
      if( deepestElement != element )
      {
        continue;
      }

      switch( issue.getKind() )
      {
        case ERROR:
          holder.createErrorAnnotation( deepestElement, issue.getMessage( Locale.getDefault() ) );
          break;
        case WARNING:
        case MANDATORY_WARNING:
          holder.createWarningAnnotation( deepestElement, issue.getMessage( Locale.getDefault() ) );
          break;
        case NOTE:
        case OTHER:
          holder.createInfoAnnotation( deepestElement, issue.getMessage( Locale.getDefault() ) );
          break;
      }
    }
  }

  public static PsiClass getContainingClass( PsiElement element )
  {
    final PsiClass psiClass = PsiTreeUtil.getParentOfType( element, PsiClass.class, false );
    if( psiClass == null )
    {
      final PsiFile containingFile = element.getContainingFile();
      if( containingFile instanceof PsiClassOwner )
      {
        final PsiClass[] classes = ((PsiClassOwner)containingFile).getClasses();
        if( classes.length == 1 )
        {
          return classes[0];
        }
      }
    }
    return psiClass;
  }
}

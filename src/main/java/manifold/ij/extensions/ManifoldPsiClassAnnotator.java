package manifold.ij.extensions;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;

/**
 */
public class ManifoldPsiClassAnnotator implements Annotator
{
  @Override
  public void annotate( PsiElement element, AnnotationHolder holder )
  {
    if( element instanceof PsiFile )
    {
      // We don't have file-level errors here, only parse errors wrt elements.
      // Otherwise, we'll have duplicated errors.
      return;
    }

    Set<PsiClass> psiClasses =  ResourceToManifoldUtil.findPsiClass( element.getContainingFile() );
    Set<Diagnostic> reported = new HashSet<>();
    psiClasses.forEach( psiClass -> annotate( psiClass, element, holder, reported ) );
  }

  private void annotate( PsiClass psiClass, PsiElement element, AnnotationHolder holder, Set<Diagnostic> reported )
  {
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
      if( !reported.contains( issue ) )
      {
        boolean created;
        if( element.getContainingFile() instanceof PsiPlainTextFile )
        {
          created = createIssueOnTextRange( holder, issue );
        }
        else
        {
          created = createIssueOnElement( holder, issue, element );
        }
        if( created )
        {
          reported.add( issue );
        }
      }
    }
  }

  private boolean createIssueOnTextRange( AnnotationHolder holder, Diagnostic issue )
  {
    TextRange range = new TextRange( (int)issue.getStartPosition(), (int)issue.getEndPosition() );
    switch( issue.getKind() )
    {
      case ERROR:
        holder.createErrorAnnotation( range, issue.getMessage( Locale.getDefault() ) );
        break;
      case WARNING:
      case MANDATORY_WARNING:
        holder.createWarningAnnotation( range, issue.getMessage( Locale.getDefault() ) );
        break;
      case NOTE:
      case OTHER:
        holder.createInfoAnnotation( range, issue.getMessage( Locale.getDefault() ) );
        break;
    }
    return true;
  }

  private boolean createIssueOnElement( AnnotationHolder holder, Diagnostic issue, PsiElement element )
  {
    if( element.getTextOffset() > issue.getStartPosition() ||
        element.getTextOffset() + element.getTextLength() <= issue.getStartPosition() )
    {
      return false;
    }

    PsiElement deepestElement = element.getContainingFile().findElementAt( (int)issue.getStartPosition() );
    if( deepestElement != element )
    {
      return false;
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
    return true;
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

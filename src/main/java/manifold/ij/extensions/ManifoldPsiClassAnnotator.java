package manifold.ij.extensions;

import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
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

import com.intellij.util.SmartList;
import manifold.ij.core.ManProject;
import org.jetbrains.annotations.NotNull;

/**
 */
public class ManifoldPsiClassAnnotator implements Annotator
{
  @Override
  public void annotate( @NotNull PsiElement element, @NotNull AnnotationHolder holder )
  {
    if( !ManProject.isManifoldInUse( element ) )
    {
      // No manifold jars in use for this project
      return;
    }

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
    PsiFile containingFile = element.getContainingFile();
    if( PsiErrorClassUtil.isErrorClass( psiClass ) && element instanceof PsiFileSystemItem )
    {
      String message = PsiErrorClassUtil.getErrorFrom( psiClass ).getMessage();
      String tooltip = makeTooltip( message );
      holder.newAnnotation( HighlightSeverity.ERROR, message )
        .range( new TextRange( 0, containingFile.getTextLength() ) )
        .tooltip( tooltip )
        .create();
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
        if( containingFile instanceof PsiPlainTextFile )
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
    String message = makeMessage( issue );
    String tooltip = makeTooltip( message );
    switch( issue.getKind() )
    {
      case ERROR:
        holder.newAnnotation( HighlightSeverity.ERROR, message )
          .range( range )
          .tooltip( tooltip )
          .create();
        break;
      case WARNING:
      case MANDATORY_WARNING:
        holder.newAnnotation( HighlightSeverity.WARNING, message )
          .range( range )
          .tooltip( tooltip )
          .create();
        break;
      case NOTE:
      case OTHER:
        holder.newAnnotation( HighlightSeverity.INFORMATION, message )
          .range( range )
          .tooltip( tooltip )
          .create();
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

    String message = makeMessage( issue );
    String tooltip = makeTooltip( message );
    if( hasAnnotation( (SmartList<Annotation>)holder, deepestElement ) )
    {
      //## todo: this is a "temporary" fix since IJ 2018.3, for some reason it calls this annotator twice with the same holder
      return false;
    }
    switch( issue.getKind() )
    {
      case ERROR:
        holder.newAnnotation( HighlightSeverity.ERROR, message )
          .range( deepestElement.getTextRange() )
          .tooltip( tooltip )
          .create();
        break;
      case WARNING:
      case MANDATORY_WARNING:
        holder.newAnnotation( HighlightSeverity.WARNING, message )
          .range( deepestElement.getTextRange() )
          .tooltip( tooltip )
          .create();
        break;
      case NOTE:
      case OTHER:
        holder.newAnnotation( HighlightSeverity.INFORMATION, message )
          .range( deepestElement.getTextRange() )
          .tooltip( tooltip )
          .create();
        break;
    }
    return true;
  }

  private boolean hasAnnotation( SmartList<Annotation> holder, PsiElement deepestElement )
  {
    TextRange textRange = deepestElement.getTextRange();
    for( Annotation annotation: holder )
    {
      if( annotation.getStartOffset() == textRange.getStartOffset() &&
          annotation.getEndOffset() == textRange.getEndOffset() )
      {
        return true;
      }
    }
    return false;
  }

  @NotNull
  private String makeMessage( Diagnostic issue )
  {
    return issue.getMessage( Locale.getDefault() );
  }
  @NotNull
  private String makeTooltip( String message )
  {
    String manIcon = "<img src=\"" + getClass().getResource( "/manifold/ij/icons/manifold.png" ) + "\">";
    return manIcon + "&nbsp;" + message;
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

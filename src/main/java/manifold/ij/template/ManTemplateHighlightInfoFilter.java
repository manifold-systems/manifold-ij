package manifold.ij.template;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import manifold.ij.template.psi.DirectiveParser;
import manifold.ij.template.psi.ManTemplateJavaFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ManTemplateHighlightInfoFilter implements HighlightInfoFilter
{
  @Override
  public boolean accept( @NotNull HighlightInfo hi, @Nullable PsiFile file )
  {
    if( isNotInititializedErrorOnParamRef( hi, file ) ||
        isNeverAssignedOnParam( hi, file ) )
    {
      return false;
    }

    return true;
  }

  private boolean isNeverAssignedOnParam( @NotNull HighlightInfo hi, @Nullable PsiFile file )
  {
    if( !(file instanceof ManTemplateJavaFile) )
    {
      return false;
    }

    PsiElement elem = file.findElementAt( hi.getStartOffset() );
    if( !(elem instanceof PsiIdentifier) )
    {
      return false;
    }

    PsiElement param = elem.getParent();
    if( !(param instanceof PsiLocalVariable) )
    {
      return false;
    }

    try
    {
      PsiElement params = param.getParent().getPrevSibling().getPrevSibling();
      if( params instanceof PsiIdentifier && params.getText().equals( DirectiveParser.PARAMS ) )
      {
        if( hi.getDescription().contains( "never assigned" ) )
        {
          return true;
        }
      }
    }
    catch( Exception ignore ) {}

    return false;
  }

  private boolean isNotInititializedErrorOnParamRef( @NotNull HighlightInfo hi, @Nullable PsiFile file )
  {
    if( !(file instanceof ManTemplateJavaFile) ||
        hi.getSeverity() != HighlightSeverity.ERROR )
    {
      return false;
    }

    PsiElement elem = file.findElementAt( hi.getStartOffset() );
    if( !(elem instanceof PsiIdentifier) )
    {
      return false;
    }

    PsiElement parent = elem.getParent();
    if( !(parent instanceof PsiReferenceExpression) )
    {
      return false;
    }

    PsiReference reference = parent.getReference();
    if( reference == null )
    {
      return false;
    }

    PsiElement param = reference.resolve();
    if( !(param instanceof PsiLocalVariable) )
    {
      return false;
    }

    try
    {
      PsiElement params = param.getParent().getPrevSibling().getPrevSibling();
      if( params instanceof PsiIdentifier && params.getText().equals( DirectiveParser.PARAMS ) )
      {
        if( hi.getDescription().contains( "might not have been initialized" ) )
        {
          return true;
        }
      }
    }
    catch( Exception ignore ) {}

    return false;
  }
}

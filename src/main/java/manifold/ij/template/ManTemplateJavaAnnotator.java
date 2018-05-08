package manifold.ij.template;

import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import manifold.ij.template.psi.DirectiveParser;
import org.jetbrains.annotations.NotNull;

public class ManTemplateJavaAnnotator implements Annotator
{
  @Override
  public void annotate( @NotNull final PsiElement element, @NotNull AnnotationHolder holder )
  {
    highlightDirectiveKeywords( element, holder );
  }

  private void highlightDirectiveKeywords( @NotNull PsiElement element, @NotNull AnnotationHolder holder )
  {
    if( element instanceof PsiIdentifier && isInManTLFile( element ) && isDirectiveKeyword( element ) )
    {
      Annotation annotation = holder.createInfoAnnotation( element.getTextRange(), null );
      annotation.setTextAttributes( JavaHighlightingColors.KEYWORD );
    }
  }

  private boolean isDirectiveKeyword( @NotNull PsiElement element )
  {
    String text = element.getText();
    switch( text )
    {
      case DirectiveParser.IMPORT:
      case DirectiveParser.EXTENDS:
      case DirectiveParser.PARAMS:
      case DirectiveParser.INCLUDE:
      case DirectiveParser.SECTION:
      case DirectiveParser.END:
      case DirectiveParser.LAYOUT:
      case DirectiveParser.CONTENT:
        return true;
    }
    return false;
  }

  private boolean isInManTLFile( @NotNull PsiElement element )
  {
    return element.getContainingFile().getLanguage() == ManTemplateJavaLanguage.INSTANCE;
  }
}
package manifold.ij.template;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import manifold.ij.core.ManProject;
import manifold.ij.template.psi.ManTemplateTokenImpl;
import org.jetbrains.annotations.NotNull;

public class ManTemplateAnnotator implements Annotator
{
  @Override
  public void annotate( @NotNull final PsiElement element, @NotNull AnnotationHolder holder )
  {
    if( !ManProject.isManifoldInUse( element ) )
    {
      // Manifold jars are not used in the project
      return;
    }

    if( element instanceof ManTemplateTokenImpl )
    {
    }
    else if( element instanceof OuterLanguageElement )
    {
    }
  }
}
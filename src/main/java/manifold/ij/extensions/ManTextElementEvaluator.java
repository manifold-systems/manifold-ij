package manifold.ij.extensions;

import com.intellij.codeInsight.TargetElementEvaluatorEx2;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class facilitates Find Usages in Plain Text files.
 * <p/>
 * See {@link com.intellij.codeInsight.TargetElementUtil#getNamedElement(PsiElement)}.
 */
public class ManTextElementEvaluator extends TargetElementEvaluatorEx2
{
  @Nullable
  @Override
  public PsiElement getNamedElement( @NotNull PsiElement element )
  {
    return element instanceof PsiFile ? element : element.getContainingFile();
  }

  @Nullable
  @Override
  public PsiElement getElementByReference( @NotNull PsiReference ref, int flags )
  {
    return null;
  }
}

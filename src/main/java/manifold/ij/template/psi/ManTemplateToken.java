package manifold.ij.template.psi;

import com.intellij.psi.PsiElement;

public interface ManTemplateToken extends PsiElement
{
  ManTemplateTokenType getTokenType();
}

package manifold.ij.core;

import com.intellij.psi.PsiReference;

/**
 * A potential reference to an overloaded operator method such as the "plus" method for a ManPsiBinaryExpressionImpl.
 */
public interface IManOperatorOverloadReference extends PsiReference
{
  /**
   * @return true if this expression instance uses an overloaded operator method
   */
  boolean isOverloaded();
}

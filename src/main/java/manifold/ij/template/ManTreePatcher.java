package manifold.ij.template;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.impl.java.stubs.JavaLiteralExpressionElementType;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.templateLanguages.SimpleTreePatcher;

/**
 * todo: this class exists to work around a bug with IntelliJ's internal code where it does not expect
 * a template content element as a child of Java language elements, maybe delete this class if this bug
 * is ever fixed.
 *
 * @See https://intellij-support.jetbrains.com/hc/en-us/community/posts/360000444624-Template-language-PSI-tree-sometimes-incorrect-wrt-outer-tokens
 */
public class ManTreePatcher extends SimpleTreePatcher
{
  @Override
  public void insert( CompositeElement parent, TreeElement anchorBefore, OuterLanguageElement toInsert )
  {
    if( anchorBefore != null )
    {
      anchorBefore = getElemToInsertBefore( anchorBefore );
    }
    super.insert( parent, anchorBefore, toInsert );
  }

  /**
   * Ensure content element is not inserted as the first child of an expression language
   * element where internal IJ code does not expect it.
   */
  private TreeElement getElemToInsertBefore( TreeElement anchorBefore )
  {
    CompositeElement parent = anchorBefore.getTreeParent();
    
    while( parent != null &&
           parent.rawFirstChild() == anchorBefore &&
           (parent.getElementType() instanceof JavaLiteralExpressionElementType ||
            parent instanceof PsiExpression) )
    {
      anchorBefore = parent;
      parent = parent.getTreeParent();
    }

    return anchorBefore;
  }
}

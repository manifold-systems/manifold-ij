package manifold.ij.template;

import com.intellij.lang.ASTFactory;
import com.intellij.psi.impl.java.stubs.JavaLiteralExpressionElementType;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.templateLanguages.TreePatcher;
import com.intellij.util.CharTable;

/**
 * //## todo: this class exists to work around a bug with IntelliJ's HighlightUtil.  Remove this class once this bug is fixed
 * @See https://intellij-support.jetbrains.com/hc/en-us/community/posts/360000444624-Template-language-PSI-tree-sometimes-incorrect-wrt-outer-tokens
 */
public class ManTreePatcher implements TreePatcher
{
  @Override
  public void insert( CompositeElement parent, TreeElement anchorBefore, OuterLanguageElement toInsert )
  {
    if( anchorBefore != null )
    {
      // Ensure content element is not inserted as a child of a language element where
      // IJ java highlighter does not expect it (like a PsiLiteralExpression).
      anchorBefore = getElemToInsertBefore( anchorBefore );

      anchorBefore.rawInsertBeforeMe( (TreeElement)toInsert );
    }
    else
    {
      parent.rawAddChildren( (TreeElement)toInsert );
    }
  }

  private TreeElement getElemToInsertBefore( TreeElement csr )
  {
    CompositeElement parent = csr.getTreeParent();
    if( parent != null && parent.getElementType() instanceof JavaLiteralExpressionElementType )
    {
      // intellij internal highlighter has a bug where is does not expect outer content to be the first child of
      // a literal expression
      return parent;
    }
    return csr;
  }

  @Override
  public LeafElement split( LeafElement leaf, int offset, final CharTable table )
  {
    final CharSequence chars = leaf.getChars();
    final LeafElement leftPart = ASTFactory.leaf( leaf.getElementType(), table.intern( chars, 0, offset ) );
    final LeafElement rightPart = ASTFactory.leaf( leaf.getElementType(), table.intern( chars, offset, chars.length() ) );
    leaf.rawInsertAfterMe( leftPart );
    leftPart.rawInsertAfterMe( rightPart );
    leaf.rawRemove();
    return leftPart;
  }
}

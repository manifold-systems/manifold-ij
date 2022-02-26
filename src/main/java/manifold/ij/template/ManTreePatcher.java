/*
 *
 *  * Copyright (c) 2022 - Manifold Systems LLC
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package manifold.ij.template;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiTypeElement;
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
           isFirstNonEmptyChild( anchorBefore, parent ) &&
           (parent.getElementType() instanceof JavaLiteralExpressionElementType ||
            parent instanceof PsiJavaCodeReferenceElement ||
            parent instanceof PsiTypeElement ||
            parent instanceof PsiExpression) )
    {
      anchorBefore = parent;
      parent = parent.getTreeParent();
    }

    return anchorBefore;
  }

  private boolean isFirstNonEmptyChild( TreeElement anchorBefore, CompositeElement parent )
  {
    TreeElement child = parent.rawFirstChild();
    if( child == anchorBefore )
    {
      return true;
    }

    while( child != null && child.getTextLength() == 0 )
    {
      child = child.getTreeNext();
    }

    return child == anchorBefore;
  }
}

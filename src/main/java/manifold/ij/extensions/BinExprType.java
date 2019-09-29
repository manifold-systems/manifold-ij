package manifold.ij.extensions;

import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;

class BinExprType
{
  PsiType _type;
  IElementType _operator;
  boolean _swapped;

  BinExprType( PsiType type, IElementType operator, boolean swapped )
  {
    _type = type;
    _operator = operator;
    _swapped = swapped;
  }
}

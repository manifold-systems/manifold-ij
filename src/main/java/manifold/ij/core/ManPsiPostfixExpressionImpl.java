package manifold.ij.core;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiPostfixExpressionImpl;
import com.intellij.util.IncorrectOperationException;
import manifold.ij.extensions.ManJavaResolveCache;
import org.jetbrains.annotations.NotNull;

// handle inc/dec postfix operator overloading
public class ManPsiPostfixExpressionImpl extends PsiPostfixExpressionImpl implements IManOperatorOverloadReference
{
  public ManPsiPostfixExpressionImpl()
  {
    super();
  }

  @Override
  public boolean isOverloaded()
  {
    PsiMethod method = ManJavaResolveCache.getBinaryOperatorMethod(
      getOperationSign(),
      getOperand().getType(),
      null, this );
    return method != null;
  }

  public PsiReference getReference() {
    return isOverloaded() ? this : super.getReference();
  }

  @NotNull
  @Override
  public PsiElement getElement() {
    return this;
  }

  @NotNull
  @Override
  public TextRange getRangeInElement()
  {
    return TextRange.EMPTY_RANGE;
  }

  @Override
  public PsiElement resolve() {
    if( isOverloaded() )
    {
      PsiMethod method = ManJavaResolveCache.getBinaryOperatorMethod( getOperationSign(), getOperand().getType(), null, this );
      return method;
    }
    return null;
  }

  @NotNull
  @Override
  public String getCanonicalText()
  {
    return "";
  }

  @Override
  public PsiElement handleElementRename( @NotNull String newElementName ) throws IncorrectOperationException
  {
    return null;
  }

  @Override
  public PsiElement bindToElement( @NotNull PsiElement element ) throws IncorrectOperationException
  {
    return null;
  }

  @Override
  public boolean isReferenceTo( @NotNull PsiElement element )
  {
    return resolve() == element;
  }

  @Override
  public boolean isSoft() {
    return false;
  }
}

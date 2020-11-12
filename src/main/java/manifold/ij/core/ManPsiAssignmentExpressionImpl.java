package manifold.ij.core;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveCache;
import com.intellij.psi.impl.source.tree.java.PsiAssignmentExpressionImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import manifold.ij.extensions.ManJavaResolveCache;
import org.jetbrains.annotations.NotNull;

// For operator overloading of compound assignment operators +=, *=, etc.
public class ManPsiAssignmentExpressionImpl extends PsiAssignmentExpressionImpl implements IManOperatorOverloadReference
{
  public ManPsiAssignmentExpressionImpl()
  {
    super();
  }

  // Handle compound equality operator overloading +=, *=, etc.
  @Override
  public PsiType getType()
  {
    PsiExpression lExpression = PsiUtil.deparenthesizeExpression( getLExpression() );
    if( !(lExpression instanceof PsiReferenceExpression || lExpression instanceof PsiArrayAccessExpression) )
    {
      return null;
    }
    return JavaResolveCache.getInstance( getProject() ).getType( this, e -> lExpression.getType() );
  }

  @Override
  public boolean isOverloaded()
  {
    if( getRExpression() == null )
    {
      return false;
    }
    PsiMethod method = ManJavaResolveCache.getBinaryOperatorMethod(
      getOperationSign(),
      getLExpression().getType(),
      getRExpression().getType(), this );
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
    if( getRExpression() == null )
    {
      return null;
    }
    PsiMethod method = ManJavaResolveCache.getBinaryOperatorMethod(
      getOperationSign(),
      getLExpression().getType(),
      getRExpression().getType(), this );
    return method;
  }

  @NotNull
  @Override
  public @NlsSafe String getCanonicalText()
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

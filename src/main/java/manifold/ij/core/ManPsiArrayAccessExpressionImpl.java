package manifold.ij.core;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiArrayAccessExpressionImpl;
import com.intellij.util.IncorrectOperationException;
import manifold.ij.extensions.ManJavaResolveCache;
import org.jetbrains.annotations.NotNull;

// handle indexed operator overloading
public class ManPsiArrayAccessExpressionImpl extends PsiArrayAccessExpressionImpl implements IManOperatorOverloadReference
{
  public ManPsiArrayAccessExpressionImpl()
  {
    super();
  }

  @Override
  public PsiType getType()
  {
    PsiType type = super.getType();
    if( type == null )
    {
      PsiType arrayType = getArrayExpression().getType();
      if( !(arrayType instanceof PsiArrayType) && getIndexExpression() != null )
      {
        if( getParent() instanceof PsiAssignmentExpression && ((PsiAssignmentExpression)getParent()).getLExpression() == this )
        {
          type = ManJavaResolveCache.getBinaryType( ManJavaResolveCache.INDEXED_SET, getArrayExpression().getType(),
            getIndexExpression().getType(), this );
        }
        else
        {
          type = ManJavaResolveCache.getBinaryType( ManJavaResolveCache.INDEXED_GET, getArrayExpression().getType(),
            getIndexExpression().getType(), this );
        }
      }
    }
    return type;
  }

  @Override
  public boolean isOverloaded()
  {
    PsiType type = super.getType();
    if( type == null )
    {
      PsiType arrayType = getArrayExpression().getType();
      if( !(arrayType instanceof PsiArrayType) && getIndexExpression() != null )
      {
        type = ManJavaResolveCache.getBinaryType( ManJavaResolveCache.INDEXED_GET, getArrayExpression().getType(),
          getIndexExpression().getType(), this );
      }
      return type != null;
    }
    return false;
  }

  public PsiReference getReference() {
    PsiType arrayType = getArrayExpression().getType();
    if( !(arrayType instanceof PsiArrayType) && getIndexExpression() != null )
    {
      return this;
    }
    return super.getReference();
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
    PsiType arrayType = getArrayExpression().getType();
    if( !(arrayType instanceof PsiArrayType) && getIndexExpression() != null )
    {
      PsiMethod method = ManJavaResolveCache.getBinaryOperatorMethod(
        getParent() instanceof PsiAssignmentExpression
        ? ManJavaResolveCache.INDEXED_SET
        : ManJavaResolveCache.INDEXED_GET,
        getArrayExpression().getType(),
        getIndexExpression().getType(), this );
      return method;
    }
    return null;
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
    return element instanceof PsiMethod && element.getManager().areElementsEquivalent( resolve(), element );
  }

  @Override
  public boolean isSoft() {
    return false;
  }
}

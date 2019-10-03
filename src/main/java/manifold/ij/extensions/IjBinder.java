package manifold.ij.extensions;

import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.tree.java.PsiBinaryExpressionImpl;
import com.intellij.psi.tree.IElementType;
import java.util.HashMap;
import java.util.Map;
import manifold.api.util.Pair;
import manifold.internal.javac.AbstractBinder;
import org.jetbrains.annotations.NotNull;

public class IjBinder extends AbstractBinder<BinExprType, PsiBinaryExpression, PsiExpression, IElementType>
{
  private final Map<Pair<PsiType, PsiType>, BinExprType> _mapReactions;
  private PsiBinaryExpression _expr;

  IjBinder( PsiBinaryExpression expr )
  {
    _expr = expr;
    _mapReactions = new HashMap<>();
  }

  @Override
  protected BinExprType findBinderMethod( Node<PsiExpression, IElementType> left, Node<PsiExpression, IElementType> right )
  {
    PsiType lhs = left.getExpr().getType();
    if( lhs == null )
    {
      return null;
    }
    PsiType rhs = right.getExpr().getType();
    if( rhs == null )
    {
      return null;
    }
    Pair<PsiType, PsiType> pair = Pair.make( lhs, rhs );
    if( right.getOperatorLeft() == null && _mapReactions.containsKey( pair ) )
    {
      return _mapReactions.get( pair );
    }
    BinExprType reaction = getReaction( lhs, rhs, right.getOperatorLeft() );
    if( right.getOperatorLeft() == null )
    {
      _mapReactions.put( pair, reaction );
    }
    return reaction;
  }

  private BinExprType getReaction( PsiType lhs, PsiType rhs, IElementType operator )
  {
    if( operator != null )
    {
      return resolveOperatorMethod( lhs, rhs, operator );
    }
    else
    {
      PsiType binder = ManJavaResolveCache.getBinaryType( "prefixBind", lhs, rhs, _expr );
      boolean swapped = false;
      if( binder == null )
      {
        binder = ManJavaResolveCache.getBinaryType( "postfixBind", rhs, lhs, _expr );
        swapped = true;
      }
      return binder == null ? null : new BinExprType( binder, null, swapped );
    }
  }

  private BinExprType resolveOperatorMethod( PsiType left, PsiType right, IElementType operator )
  {
    // Handle operator overloading

    boolean swapped = false;
    PsiType exprType = ManJavaResolveCache.getBinaryType( operator, left, right, _expr );
    if( exprType == null && ManJavaResolveCache.isCommutative( operator ) )
    {
      exprType = ManJavaResolveCache.getBinaryType( operator, right, left, _expr );
      swapped = true;
    }

    if( exprType != null )
    {
      return new BinExprType( exprType, operator, swapped );
    }

    return null;
  }

  @Override
  protected Node<PsiExpression, IElementType> makeBinaryExpression( Node<PsiExpression, IElementType> left,
                                                                    Node<PsiExpression, IElementType> right,
                                                                    BinExprType binderMethod )
  {

    PsiBinaryExpression binary = new MyPsiBinaryExpressionImpl( left.getExpr(), right.getExpr(), binderMethod );
    return new Node<>( binary, left.getOperatorLeft() );

//    PsiExpression lhs = left.getExpr();
//    PsiExpression rhs = right.getExpr();
//    boolean isLhsNode = lhs instanceof ASTNode;
//    boolean isRhsNode = rhs instanceof ASTNode;
//    int start = lhs.getTextRange().getEndOffset();
//    int end = rhs.getTextRange().getStartOffset();
//    String opStr = rhs.getContainingFile().getText().substring( start, end );
//    String expr = (isLhsNode ? "5" : lhs.getText()) + opStr + (isRhsNode ? "z" : rhs.getText());
//    PsiBinaryExpressionImpl newTree = (PsiBinaryExpressionImpl)JavaPsiFacade.getInstance(
//      _expr.getProject() ).getParserFacade().createExpressionFromText( expr, _expr );
//
//      ApplicationManager.getApplication().runWriteAction( () -> {
//          if( isLhsNode )
//          {
//            newTree.replaceChild( (ASTNode)newTree.getLOperand(), (ASTNode)lhs );
//          }
//          if( isRhsNode )
//          {
//            newTree.replaceChild( (ASTNode)newTree.getROperand(), (ASTNode)rhs );
//          }
//        } );
//
//    newTree.putUserData( KEY_BINARY_EXPR_TYPE, binderMethod._type );
//
//    //PsiBinaryExpression binary = new MyPsiBinaryExpressionImpl( left.getExpr(), right.getExpr(), binderMethod );
//    return new Node<>( newTree, left.getOperatorLeft() );
  }

  static class MyPsiBinaryExpressionImpl extends PsiBinaryExpressionImpl
  {
    private final PsiExpression _left;
    private final PsiExpression _right;
    private final BinExprType _binExprType;

    MyPsiBinaryExpressionImpl( PsiExpression left, PsiExpression right, BinExprType binExprType )
    {
      _left = left;
      _right = right;
      _binExprType = binExprType;
    }

    @NotNull
    @Override
    public PsiExpression getLOperand()
    {
      return _left;
    }

    @Override
    public PsiExpression getROperand()
    {
      return _right;
    }

    @Override
    public PsiType getType()
    {
      return _binExprType._type;
    }

    public boolean isSwapped()
    {
      return _binExprType._swapped;
    }
  }
}
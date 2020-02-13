package manifold.ij.extensions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.ResolveState;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.impl.source.resolve.JavaResolveCache;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.java.PsiBinaryExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import manifold.ext.api.Jailbreak;
import manifold.ij.core.ManProject;
import manifold.internal.javac.AbstractBinder.Node;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ManJavaResolveCache extends JavaResolveCache
{
  // public static final Key<CachedBindingPsiType> KEY_BINARY_EXPR_TYPE = new Key<>( "KEY_BINARY_EXPR_TYPE" );
  private static final String COMPARE_TO = "compareTo";
  private static final String COMPARE_TO_USING = "compareToUsing";
  private static Map<IElementType, String> BINARY_OP_TO_NAME = new HashMap<IElementType, String>()
  {{
    put( JavaTokenType.PLUS, "plus" );
    put( JavaTokenType.MINUS, "minus" );
    put( JavaTokenType.ASTERISK, "times" );
    put( JavaTokenType.DIV, "div" );
    put( JavaTokenType.PERC, "rem" );
    // note ==, !=, >, >=, <, <=  are covered via IComparableWith**
  }};

  public ManJavaResolveCache( Project p )
  {
    super( p );
  }

  @Nullable
  public <T extends PsiExpression> PsiType getType( @NotNull T expr, @NotNull Function<? super T, ? extends PsiType> f )
  {
    if( !ManProject.isManifoldInUse( expr ) )
    {
      // Manifold jars are not used in the project
      return super.getType( expr, f );
    }

    if( expr instanceof PsiBinaryExpression )
    {
      PsiType type = getTypeForOverloadedBinaryOperator( (PsiBinaryExpression)expr );
      if( type != null )
      {
        return type;
      }
    }

    return super.getType( expr, f );
  }

  static boolean isBindingExpression( final PsiExpression expr )
  {
    if( !(expr instanceof PsiBinaryExpression) )
    {
      return false;
    }

    PsiElement opChild = getOpChild( (PsiBinaryExpressionImpl)expr );
    IElementType op = opChild == null ? null : ((PsiJavaToken)opChild).getTokenType();
    return op == null;
  }

  static PsiType getTypeForOverloadedBinaryOperator( final PsiBinaryExpression expr )
  {
    PsiElement opChild = getOpChild( (PsiBinaryExpressionImpl)expr );
    IElementType op = opChild == null ? null : ((PsiJavaToken)opChild).getTokenType();
    String opName = op == null ? null : BINARY_OP_TO_NAME.get( op );
    if( opName == null )
    {
      if( op == null ) // binding expr
      {
        if( isParentBindingExpr( expr.getParent() ) )
        {
          // ignore child binding expressions of the root binding expression
          // (note this is only because we haven't yet figured out how to replace the tree inside getOrCreate())
          return null;
        }
        //return CachedBindingPsiType.getOrCreate( expr )._type;
        PsiBinaryExpression newExpr = new IjBinder( expr ).bind( getBindingOperands( expr, new ArrayList<>() ) );
        return newExpr == null ? null : newExpr.getType();
      }
      if( isComparableOperator( op ) )
      {
        opName = COMPARE_TO_USING;
      }
      else
      {
        return null;
      }
    }

    PsiType left = expr.getLOperand().getType();
    if( left == null )
    {
      return null;
    }

    PsiExpression rOperand = expr.getROperand();
    if( rOperand == null )
    {
      return null;
    }

    PsiType right = rOperand.getType();
    if( right == null || left instanceof PsiPrimitiveType && right instanceof PsiPrimitiveType )
    {
      return null;
    }

    PsiType type = getBinaryType( opName, left, right, expr );
    if( type == null && isCommutative( op ) )
    {
      type = getBinaryType( opName, right, left, expr );
    }
    else if( type == null && opName.equals( COMPARE_TO_USING ) &&
             !(left instanceof PsiPrimitiveType) && isRelationalOperator( op ) )
    {
      // Support > >= < <= on any Comparable implementor
      type = getBinaryType( COMPARE_TO, left, right, expr );
      if( type != null && type.equals( PsiType.INT ) )
      {
        type = PsiType.BOOLEAN;
      }
    }
    return type;
  }

  private static boolean isParentBindingExpr( PsiElement expr )
  {
    if( expr instanceof PsiBinaryExpression && getOpChild( (PsiBinaryExpressionImpl)expr ) == null )
    {
      return true;
    }
    if( !(expr instanceof PsiExpression) || expr instanceof PsiParenthesizedExpression )
    {
      return false;
    }
    return isParentBindingExpr( expr.getParent() );
  }

  @Nullable
  static PsiType getBinaryType( IElementType op, PsiType left, PsiType right, PsiBinaryExpression context )
  {
    String opName = BINARY_OP_TO_NAME.get( op );
    return getBinaryType( opName, left, right, context );
  }

  @Nullable
  static PsiType getBinaryType( String opName, PsiType left, PsiType right, PsiBinaryExpression context )
  {
    PsiClass psiClassLeft = PsiTypesUtil.getPsiClass( left );
    if( psiClassLeft == null )
    {
      return null;
    }

    PsiMethod[] members = psiClassLeft.getAllMethods();

    PsiType operationReturnType = getBinaryOperationReturnType( opName, left, right, members, context );
    if( operationReturnType != null )
    {
      return operationReturnType;
    }

    // also look for default interface methods
    for( PsiType iface: left.getSuperTypes() )
    {
      PsiClass psiIface = PsiTypesUtil.getPsiClass( iface );
      if( psiIface != null && psiIface.isInterface() )
      {
        if( iface instanceof PsiClassType )
        {
          operationReturnType = getBinaryOperationReturnType( opName, iface, right, psiIface.getAllMethods(), context );
          if( operationReturnType != null )
          {
            return operationReturnType;
          }
        }
      }
    }

    return null;
  }

  @Nullable
  private static PsiType getGenericBinaryOperationReturnType( String opName, PsiType left, PsiType right, PsiBinaryExpression context )
  {
    int paramCount = opName.equals( COMPARE_TO_USING ) ? 2 : 1;
    PsiMethodCallExpressionImpl testExpr = (PsiMethodCallExpressionImpl)JavaPsiFacade.getInstance( context.getProject() )
      .getParserFacade()
      .createExpressionFromText( paramCount == 1
        ? "((" + left.getCanonicalText() + ")null)." + opName + "((" + right.getCanonicalText() + ")null)"
        : "((" + left.getCanonicalText() + ")null)." + opName + "((" + right.getCanonicalText() + ")null, null)", context );
    if( !testExpr.resolveMethodGenerics().isValidResult() )
    {
      return null;
    }
    return testExpr.getType();
  }

  @Nullable
  private static PsiType getBinaryOperationReturnType( String opName, PsiType left, PsiType right, PsiMethod[] members, PsiBinaryExpression context )
  {
    int paramCount = opName.equals( COMPARE_TO_USING ) ? 2 : 1;
    for( PsiMethod m: members )
    {
      if( m.getParameterList().getParametersCount() != paramCount )
      {
        continue;
      }

      if( opName.equals( m.getName() ) )
      {
        PsiType paramType = m.getParameterList().getParameters()[0].getType();
        PsiType parameterizedParam;
        PsiSubstitutor substitutor = null;
        if( left instanceof PsiClassType )
        {
          substitutor = getMemberSubstitutor( left, m );
          parameterizedParam = substitutor.substitute( paramType );

          // If the parameter is a type variable, use the parser facade to get the return type, but this is quite slow...
          //todo: researching a way to avoid using the parser facade and use substitutors etc.
          if( parameterizedParam instanceof PsiClassReferenceType )
          {
            PsiClass paramRef = ((PsiClassReferenceType)parameterizedParam).resolve();
            if( paramRef instanceof PsiTypeParameter )
            {
              PsiType returnType = getGenericBinaryOperationReturnType( opName, left, right, context );
              if( returnType != null )
              {
                return returnType;
              }
            }
          }
//
//          PsiSubstitutor castSub = TypeConversionUtil.getSuperClassSubstitutor(plainClass, (PsiClassType)castType);
//          PsiType typeAfterCast = toRaw(castSub.substitute(method.getReturnType()));
//
//          TypeConversionUtil.getSuperClassSubstitutor(
//            ((ClsTypeParameterImpl)((PsiClassReferenceType)paramType).resolve()).getExtendsListTypes()[0].resolveGenerics().getElement(), (PsiClassType)right );
//
//          JavaGenericsUtil.isRawToGeneric( ((ClsTypeParameterImpl)((PsiClassReferenceType)paramType).resolve()).getExtendsListTypes()[0], (PsiClassType)right )
        }
        else
        {
          parameterizedParam = paramType;
        }
        if( parameterizedParam.isAssignableFrom( right ) )
        {
          substitutor = substitutor == null ? getMemberSubstitutor( left, m ) : substitutor;
          return substitutor.substitute( m.getReturnType() );
        }
      }
    }
    return null;
  }

  private static ArrayList<Node<PsiExpression, IElementType>> getBindingOperands( @NotNull PsiExpression tree, ArrayList<Node<PsiExpression, IElementType>> operands )
  {
    if( tree instanceof PsiBinaryExpression )
    {
      PsiElement opChild = getOpChild( (PsiBinaryExpressionImpl)tree );

      if( opChild == null )
      {
        getBindingOperands( ((PsiBinaryExpression)tree).getLOperand(), operands );
        PsiExpression rOperand = ((PsiBinaryExpression)tree).getROperand();
        if( rOperand != null )
        {
          getBindingOperands( rOperand, operands );
        }
      }
      else
      {
        PsiBinaryExpression binExpr = (PsiBinaryExpression)tree;

        getBindingOperands( binExpr.getLOperand(), operands );
        int index = operands.size();
        PsiExpression rOperand = binExpr.getROperand();
        if( rOperand != null )
        {
          getBindingOperands( rOperand, operands );

          Node<PsiExpression, IElementType> rhsNode = operands.get( index );

          IElementType op = ((PsiJavaToken)opChild).getTokenType();
          rhsNode.setOperatorLeft( op );
        }
      }
    }
    else
    {
      operands.add( new Node<>( tree ) );
    }
    return operands;
  }

  private static PsiElement getOpChild( PsiBinaryExpressionImpl tree )
  {
    return tree.findChildByRoleAsPsiElement( ChildRole.OPERATION_SIGN );
  }

  private static boolean isComparableOperator( IElementType tag )
  {
    return tag == JavaTokenType.EQEQ ||
           tag == JavaTokenType.NE ||
           tag == JavaTokenType.LT ||
           tag == JavaTokenType.LE ||
           tag == JavaTokenType.GT ||
           tag == JavaTokenType.GE;
  }

  private static boolean isRelationalOperator( IElementType tag )
  {
    return tag == JavaTokenType.NE ||
           tag == JavaTokenType.LT ||
           tag == JavaTokenType.LE ||
           tag == JavaTokenType.GT ||
           tag == JavaTokenType.GE;
  }

  static boolean isCommutative( IElementType tag )
  {
    return tag == JavaTokenType.PLUS ||
           tag == JavaTokenType.ASTERISK ||
           tag == JavaTokenType.OR ||
           tag == JavaTokenType.XOR ||
           tag == JavaTokenType.AND ||
           tag == JavaTokenType.EQEQ ||
           tag == JavaTokenType.NE;
  }

  public static PsiSubstitutor getMemberSubstitutor( @Nullable PsiType qualifierType, @NotNull final PsiMember member )
  {
    final Ref<PsiSubstitutor> subst = Ref.create( PsiSubstitutor.EMPTY );
    class MyProcessor implements PsiScopeProcessor, NameHint, ElementClassHint
    {
      @Override
      public boolean execute( @NotNull PsiElement element, @NotNull ResolveState state )
      {
        if( element == member )
        {
          subst.set( state.get( PsiSubstitutor.KEY ) );
        }
        return true;
      }

      @Override
      public String getName( @NotNull ResolveState state )
      {
        return member.getName();
      }

      @Override
      public boolean shouldProcess( @NotNull DeclarationKind kind )
      {
        return member instanceof PsiEnumConstant ? kind == DeclarationKind.ENUM_CONST :
               member instanceof PsiField ? kind == DeclarationKind.FIELD :
               kind == DeclarationKind.METHOD;
      }

      @Override
      public <T> T getHint( @NotNull Key<T> hintKey )
      {
        //noinspection unchecked
        return hintKey == NameHint.KEY || hintKey == ElementClassHint.KEY ? (T)this : null;
      }
    }

    PsiScopesUtil.processTypeDeclarations( qualifierType, member, new MyProcessor() );

    return subst.get();
  }

//  static class CachedBindingPsiType
//  {
//    static Map<Integer, PsiType> _cache = new ConcurrentHashMap<>();
//    static int foo = 0;
//
//    PsiType _type;
//    long _fingerprint;
//
//    CachedBindingPsiType( PsiType type, long fingerprint )
//    {
//      _type = type;
//      _fingerprint = fingerprint;
//    }
//
//    static CachedBindingPsiType getOrCreate( PsiBinaryExpression bindingExpr )
//    {
//        return new CachedBindingPsiType( find( bindingExpr ), 0 );
////      PsiBinaryExpression newExpr = new IjBinder( bindingExpr ).bind( getBindingOperands( bindingExpr, new ArrayList<>() ) );
////      return new CachedBindingPsiType( newExpr == null ? null : newExpr.getType(), 0 );
//
////      CachedBindingPsiType cachedPsiType = bindingExpr.getUserData( KEY_BINARY_EXPR_TYPE );
////      ArrayList<Node<PsiExpression, IElementType>> list = null;
////      Long fp = null;
////      if( cachedPsiType == null || cachedPsiType._fingerprint != (fp = fingerprint( list = getBindingOperands( bindingExpr, new ArrayList<>() ) )) )
////      {
////        ArrayList<Node<PsiExpression, IElementType>> bindingOperands = list == null ? getBindingOperands( bindingExpr, new ArrayList<>() ) : list;
////        PsiBinaryExpression newExpr = new IjBinder( bindingExpr ).bind( bindingOperands );
////        //((PsiBinaryExpressionImpl)expr).replaceAllChildrenToChildrenOf( (ASTNode)newExpr );
////        cachedPsiType = new CachedBindingPsiType( newExpr == null ? null : newExpr.getType(), fp == null ? fingerprint( bindingOperands ) : fp );
////        bindingExpr.putUserData( KEY_BINARY_EXPR_TYPE, cachedPsiType );
////        System.out.println(bindingExpr.getText());
////      }
////      return cachedPsiType;
//    }
//
//    static PsiType find( PsiBinaryExpression bindingExpr )
//    {
//      StringBuilder sb = new StringBuilder();
//      ArrayList<Node<PsiExpression, IElementType>> list = getBindingOperands( bindingExpr, new ArrayList<>() );
//      for( Node<PsiExpression, IElementType> node: list )
//      {
//        Object type = foo++; node.getExpr().getType();
//        IElementType operatorLeft = node.getOperatorLeft();
//        String op = operatorLeft == null ? "?" : operatorLeft.toString();
//        sb.append( type ).append( ':' ).append( op ).append( ',' );
//      }
//      return _cache.computeIfAbsent( sb.toString().hashCode(), key -> {
//        PsiBinaryExpression newExpr = new IjBinder( bindingExpr ).bind( list );
//        return newExpr == null ? null : newExpr.getType();
//      } );
//    }
//  }
}

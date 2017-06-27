package manifold.ij.extensions;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiDiamondType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VarHandler
{
  private static final VarHandler INSTANCE = new VarHandler();

  private static final String MAN_VAR = "var";
  private static final String MAN_VAR_FQN = "manifold.var";

  public static VarHandler instance()
  {
    return INSTANCE;
  }

  private VarHandler()
  {
  }

  public static boolean isVar( @NotNull PsiLocalVariable psiLocalVariable )
  {
    return psiLocalVariable.getInitializer() != null && isVar( psiLocalVariable.getTypeElement().getText() );
  }

  private static boolean isVar( String className )
  {
    return MAN_VAR.equals( className ) || MAN_VAR_FQN.equals( className );
  }

  private boolean isVarForEach( @NotNull PsiParameter psiParameter )
  {
    return psiParameter.getParent() instanceof PsiForeachStatement && isVar( psiParameter.getTypeElement().getText() );
  }

  @Nullable
  public PsiType inferType( PsiTypeElement typeElement )
  {
    PsiType psiType = null;

    final PsiElement parent = typeElement.getParent();
    if( (parent instanceof PsiLocalVariable && isVar( (PsiLocalVariable)parent )) ||
        (parent instanceof PsiParameter && isVarForEach( (PsiParameter)parent )) )
    {

      if( parent instanceof PsiLocalVariable )
      {
        psiType = processLocalVariableInitializer( ((PsiLocalVariable)parent).getInitializer() );
      }
      else
      {
        psiType = processForeach( ((PsiParameter)parent).getDeclarationScope() );
      }

      if( null == psiType )
      {
        psiType = PsiType.getJavaLangObject( typeElement.getManager(), GlobalSearchScope.allScope( typeElement.getProject() ) );
      }
    }
    return psiType;
  }

  private PsiType processLocalVariableInitializer( final PsiExpression psiExpression )
  {
    PsiType result = null;
    if( null != psiExpression && !(psiExpression instanceof PsiArrayInitializerExpression) )
    {

      if( psiExpression instanceof PsiConditionalExpression )
      {
        result = RecursionManager.doPreventingRecursion( psiExpression, true, () ->
        {
          final PsiExpression thenExpression = ((PsiConditionalExpression)psiExpression).getThenExpression();
          final PsiExpression elseExpression = ((PsiConditionalExpression)psiExpression).getElseExpression();

          final PsiType thenType = null != thenExpression ? thenExpression.getType() : null;
          final PsiType elseType = null != elseExpression ? elseExpression.getType() : null;

          if( thenType == null )
          {
            return elseType;
          }
          if( elseType == null )
          {
            return thenType;
          }

          if( TypeConversionUtil.isAssignable( thenType, elseType, false ) )
          {
            return thenType;
          }
          if( TypeConversionUtil.isAssignable( elseType, thenType, false ) )
          {
            return elseType;
          }
          return thenType;
        } );
      }
      else
      {
        result = RecursionManager.doPreventingRecursion( psiExpression, true, psiExpression::getType );
      }

      if( psiExpression instanceof PsiNewExpression )
      {
        final PsiJavaCodeReferenceElement reference = ((PsiNewExpression)psiExpression).getClassOrAnonymousClassReference();
        if( reference != null )
        {
          final PsiReferenceParameterList parameterList = reference.getParameterList();
          if( parameterList != null )
          {
            final PsiTypeElement[] elements = parameterList.getTypeParameterElements();
            if( elements.length == 1 && elements[0].getType() instanceof PsiDiamondType )
            {
              result = TypeConversionUtil.erasure( result );
            }
          }
        }
      }
    }

    return result;
  }

  private PsiType processForeach( PsiElement parentDeclarationScope )
  {
    PsiType result = null;
    if( parentDeclarationScope instanceof PsiForeachStatement )
    {
      final PsiForeachStatement foreachStatement = (PsiForeachStatement)parentDeclarationScope;
      final PsiExpression iteratedValue = foreachStatement.getIteratedValue();
      if( iteratedValue != null )
      {
        result = JavaGenericsUtil.getCollectionItemType( iteratedValue );
      }
    }
    return result;
  }
}

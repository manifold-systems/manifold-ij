package manifold.ij.extensions;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiCapturedWildcardType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeVariable;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.source.resolve.JavaResolveCache;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.util.Function;
import com.intellij.util.messages.MessageBus;
import java.util.ArrayList;
import java.util.List;
import manifold.ext.api.Self;
import manifold.util.ReflectUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ManJavaResolveCache extends JavaResolveCache
{
  private static final TypeEvaluator METHOD_CALL_TYPE_EVALUATOR = new TypeEvaluator();

  public ManJavaResolveCache( @Nullable("can be null in com.intellij.core.JavaCoreApplicationEnvironment.JavaCoreApplicationEnvironment") MessageBus messageBus )
  {
    super( messageBus );
  }

  @Nullable
  @Override
  public <T extends PsiExpression> PsiType getType( @NotNull T expr, @NotNull Function<T, PsiType> f )
  {
    if( expr instanceof PsiMethodCallExpression )
    {
      //noinspection unchecked
      f = (Function<T,PsiType>)METHOD_CALL_TYPE_EVALUATOR;
    }
    return super.getType( expr, f );
  }

  private static class TypeEvaluator implements Function<PsiMethodCallExpression, PsiType>
  {
    @Override
    @Nullable
    public PsiType fun( final PsiMethodCallExpression call )
    {
      PsiReferenceExpression methodExpression = call.getMethodExpression();
      final JavaResolveResult[] results = methodExpression.multiResolve( false );
      PsiFile file = call.getContainingFile();
      LanguageLevel languageLevel = PsiUtil.getLanguageLevel( file );

      final PsiElement callParent = PsiUtil.skipParenthesizedExprUp( call.getParent() );
      final PsiExpressionList parentArgList;
      if( languageLevel.isAtLeast( LanguageLevel.JDK_1_8 ) )
      {
        parentArgList = callParent instanceof PsiConditionalExpression && !PsiPolyExpressionUtil.isPolyExpression( (PsiExpression)callParent )
                        ? null : PsiTreeUtil.getParentOfType( call, PsiExpressionList.class, true, PsiReferenceExpression.class );
      }
      else
      {
        parentArgList = null;
      }
      final MethodCandidateInfo.CurrentCandidateProperties properties = MethodCandidateInfo.getCurrentMethod( parentArgList );
      final boolean genericMethodCall = properties != null && properties.getInfo().isToInferApplicability();

      PsiType theOnly = null;
      for( int i = 0; i < results.length; i++ )
      {
        final JavaResolveResult candidateInfo = results[i];

        if( genericMethodCall && PsiPolyExpressionUtil.isMethodCallPolyExpression( call, (PsiMethod)candidateInfo.getElement() ) )
        {
          if( callParent instanceof PsiAssignmentExpression )
          {
            return null;
          }
          // LOG.error("poly expression evaluation during overload resolution");
        }

        PsiType type = (PsiType)ReflectUtil.method( PsiMethodCallExpressionImpl.class.getTypeName() + "\$TypeEvaluator", "getResultType",
          PsiMethodCallExpression.class, PsiReferenceExpression.class, JavaResolveResult.class, LanguageLevel.class )
          .invokeStatic( call, methodExpression, candidateInfo, languageLevel );

        if( type == null )
        {
          return null;
        }

        PsiMethod psiMethod = call.resolveMethod();
        if( psiMethod == null )
        {
          return null;
        }

        type = handleSelfType( psiMethod.getReturnType(), type, methodExpression );

        // ((PsiTypeElementImpl)call.resolveMethod().getReturnTypeElement()).getApplicableAnnotations()[0].getQualifiedName()
        // ((PsiTypeElementImpl)call.resolveMethod().getReturnTypeElement()).getType().getAnnotations()[0].getQualifiedName()

        if( i == 0 )
        {
          theOnly = type;
        }
        else if( !theOnly.equals( type ) )
        {
          return null;
        }
      }

      return PsiClassImplUtil.correctType( theOnly, file.getResolveScope() );
    }

    private PsiType handleSelfType( PsiType declaredReturnType, PsiType exprType, PsiReferenceExpression methodExpression )
    {
      if( !hasSelfAnnotation( declaredReturnType ) )
      {
        return exprType;
      }

      PsiType qualifierType;
      PsiExpression qualifier = (PsiExpression)methodExpression.getQualifier();
      if( qualifier == null )
      {
        String qualifiedName = RefactoringChangeUtil.getThisClass( methodExpression ).getQualifiedName();
        if( qualifiedName == null )
        {
          return exprType;
        }
        qualifierType = JavaPsiFacade.getInstance( methodExpression.getProject() )
          .getParserFacade().createTypeFromText( qualifiedName, methodExpression );
      }
      else
      {
        qualifierType = qualifier.getType();
      }
      String replacedTypeText = makeSelfType( declaredReturnType, exprType, (PsiClassType)qualifierType );
      if( replacedTypeText == null )
      {
        return exprType;
      }

      try
      {
        return JavaPsiFacade.getInstance( methodExpression.getProject() )
          .getParserFacade().createTypeFromText( replacedTypeText, methodExpression );
      }
      catch( Exception e )
      {
        return exprType;
      }
    }

    /**
     * The retType is needed for cases where IJ does not transfer the @Self annotation to
     * the exprType.  This can happen when @Self is on a type argument in an extension
     * method return type.
     */
    private String makeSelfType( PsiType retType, PsiType exprType, PsiClassType receiverType )
    {
      if( exprType instanceof PsiClassType )
      {
        boolean replace = false;
        boolean root = false;
        if( hasSelfAnnotationDirectly( retType ) )
        {
          if( receiverType.hasParameters() )
          {
            return getActualTypeName( receiverType );
          }
          replace = true;
          root = true;
        }

        List<String> newParams = new ArrayList<>();
        PsiType[] parameters = ((PsiClassType)exprType).getParameters();
        PsiType[] retParameters = ((PsiClassType)retType).getParameters();
        for( int i = 0; i < retParameters.length && i < parameters.length; i++ )
        {
          PsiType typeArg = parameters[i];
          PsiType retTypeArg = retParameters[i];
          if( hasSelfAnnotation( retTypeArg ) )
          {
            replace = true;
            newParams.add( makeSelfType( retTypeArg, typeArg, receiverType ) );
          }
          else
          {
            String actualTypeName = getActualTypeName( typeArg );
            if( actualTypeName == null )
            {
              return null;
            }
            newParams.add( actualTypeName );
          }
        }
        if( replace )
        {
          PsiClass resolve = ((PsiClassType)(root ? receiverType : exprType)).resolve();
          if( resolve == null )
          {
            return null;
          }
          String fqn = resolve.getQualifiedName();
          if( fqn == null )
          {
            return null;
          }
          StringBuilder sb = new StringBuilder( fqn );
          if( !newParams.isEmpty() )
          {
            sb.append( '<' );
            for( int i = 0; i < newParams.size(); i++ )
            {
              String param = newParams.get( i );
              if( i > 0 )
              {
                sb.append( ", " );
              }
              sb.append( param );
            }
            sb.append( '>' );
          }
          return sb.toString();
        }
      }

      if( exprType instanceof PsiArrayType && hasSelfAnnotation( retType ) )
      {
        if( hasSelfAnnotationDirectly( retType ) )
        {
          StringBuilder sb = new StringBuilder( getActualTypeName( receiverType ) );
          for( int i = 0; i < retType.getArrayDimensions(); i++ )
          {
            sb.append( "[]" );
          }
          return sb.toString();
        }
        return makeSelfType( ((PsiArrayType)retType).getComponentType(), ((PsiArrayType)exprType).getComponentType(), receiverType ) + "[]";
      }

      if( retType instanceof PsiWildcardType )
      {
        if( exprType instanceof PsiCapturedWildcardType )
        {
          exprType = ((PsiCapturedWildcardType)exprType).getWildcard();
        }
        if( exprType instanceof PsiWildcardType )
        {
          PsiType bound = ((PsiWildcardType)retType).getBound();
          PsiType retBound = ((PsiWildcardType)exprType).getBound();
          if( bound != null && hasSelfAnnotation( retBound ) )
          {
            return "?" + (((PsiWildcardType)exprType).isExtends() ? " extends " : " super ") + makeSelfType( retBound, bound, receiverType );
          }
        }
      }

      return getActualTypeName( exprType );
    }

    private String getActualTypeName( PsiType type )
    {
      if( type instanceof PsiClassType )
      {
        PsiClass resolve = ((PsiClassType)type).resolve();
        if( resolve == null )
        {
          return null;
        }
        String fqn = resolve.getQualifiedName();
        if( fqn == null )
        {
          // for some reason type variables go through here
          return resolve.getName();
        }

        StringBuilder sb = new StringBuilder( fqn );
        if( ((PsiClassType)type).getParameterCount() > 0 )
        {
          sb.append( '<' );
          PsiType[] parameters = ((PsiClassType)type).getParameters();
          for( int i = 0; i < parameters.length; i++ )
          {
            if( i > 0 )
            {
              sb.append( ", " );
            }
            PsiType typeArg = parameters[i];
            sb.append( getActualTypeName( typeArg ) );
          }
          sb.append( '>' );
        }
        return sb.toString();
      }

      if( type instanceof PsiWildcardType )
      {
        PsiType bound = ((PsiWildcardType)type).getBound();
        return "?" + (((PsiWildcardType)type).isExtends() ? " extends " : " super ") + getActualTypeName( bound );
      }

      if( type instanceof PsiCapturedWildcardType )
      {
        return getActualTypeName( ((PsiCapturedWildcardType)type).getWildcard() );
      }

      if( type instanceof PsiArrayType )
      {
        return getActualTypeName( ((PsiArrayType)type).getComponentType() ) + "[]";
      }

      if( type instanceof PsiTypeVariable )
      {
        return type.getCanonicalText();
      }

      return type.getCanonicalText();
    }

    private boolean hasSelfAnnotationDirectly( PsiType type )
    {
      for( PsiAnnotation anno: type.getAnnotations() )
      {
        if( Self.class.getTypeName().equals( anno.getQualifiedName() ) )
        {
          return true;
        }
      }
      return false;
    }

    private boolean hasSelfAnnotation( PsiType type )
    {
      if( type instanceof PsiPrimitiveType )
      {
        return false;
      }

      if( hasSelfAnnotationDirectly( type ) )
      {
        return true;
      }

      if( type instanceof PsiClassType )
      {
        for( PsiType typeArg: ((PsiClassType)type).getParameters() )
        {
          if( hasSelfAnnotation( typeArg ) )
          {
            return true;
          }
        }
      }

      if( type instanceof PsiArrayType )
      {
        if( hasSelfAnnotation( ((PsiArrayType)type).getComponentType() ) )
        {
          return true;
        }
      }

      if( type instanceof PsiWildcardType )
      {
        PsiType bound = ((PsiWildcardType)type).getBound();
        if( bound != null && hasSelfAnnotation( bound ) )
        {
          return true;
        }
      }

      if( type instanceof PsiCapturedWildcardType )
      {
        return hasSelfAnnotation( ((PsiCapturedWildcardType)type).getWildcard() );
      }

      return false;
    }

  }
}

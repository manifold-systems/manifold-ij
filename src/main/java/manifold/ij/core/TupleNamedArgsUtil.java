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

package manifold.ij.core;

import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.jvm.annotation.JvmAnnotationConstantValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import manifold.api.gen.SrcExpression;
import manifold.api.gen.SrcRawExpression;
import manifold.ext.params.rt.manifold_params;
import manifold.ij.util.ComputeUtil;
import manifold.ij.util.ManPsiUtil;
import manifold.rt.api.util.ManStringUtil;

import java.util.*;

import static com.intellij.psi.util.TypeConversionUtil.isAssignable;

public class TupleNamedArgsUtil
{
//  public static PsiClassType getParamsClass( PsiReferenceExpression call, ManPsiTupleExpression tupleExpr )
//  {
//    return CachedValuesManager.getCachedValue( call, () -> CachedValueProvider.Result
//      .create( getParamsClassInternal( call, tupleExpr ), PsiModificationTracker.MODIFICATION_COUNT, call ) );
//  }
//

  /**
   * Generates a `new $param_class_name&lt;&gt;(...)` expression that the compiler/manifold produces in place of the
   * tuple expression when used as a means to call a method that has one or more optional parameters. The generated
   * new expression here is used solely to infer the type assigned to the tuple expression.
   * <p/>
   * Note, the new expression type matches the parameter type of the generated method that forwards to the optional
   * parameters method. So this is effectively resolving the method reference as well.
   */
  public static PsiClassType getNewParamsClassExprType( PsiCallExpression callExpr, ManPsiTupleExpression tupleExpr )
  {
    return getNewParamsClassExprType( callExpr, tupleExpr, null );
  }
  public static PsiClassType getNewParamsClassExprType( PsiCallExpression callExpr, ManPsiTupleExpression tupleExpr, AnnotationHolder holder )
  {
    final String methodName;
    final PsiClass containingClass;
    PsiType receiverType;
    final boolean isConstructor;

    if( callExpr instanceof PsiMethodCallExpression )
    {
      PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)callExpr).getMethodExpression();
      PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if( qualifierExpression != null )
      {
        receiverType = qualifierExpression.getType();
        if( receiverType == null )
        {
          PsiElement resolve = ((PsiReferenceExpression)qualifierExpression).resolve();
          if( resolve instanceof PsiClass )
          {
            receiverType = PsiTypesUtil.getClassType( (PsiClass)resolve );
          }
          if( receiverType == null )
          {
            return null;
          }
        }
        containingClass = PsiTypesUtil.getPsiClass( receiverType );
      }
      else
      {
        containingClass = ManPsiUtil.getContainingClass( callExpr );
        if( containingClass != null )
        {
          receiverType = JavaPsiFacade.getElementFactory( containingClass.getProject() ).createType( containingClass, PsiSubstitutor.EMPTY );
        }
        else
        {
          return null;
        }
      }
      methodName = methodExpression.getReferenceName();
      isConstructor = false;
    }
    else if( callExpr instanceof PsiNewExpression )
    {
      PsiJavaCodeReferenceElement classRef = ((PsiNewExpression)callExpr).getClassReference();
      if( classRef == null )
      {
        return null;
      }
      containingClass = (PsiClass)classRef.resolve();
      if( containingClass == null )
      {
        return null;
      }
      receiverType = PsiTypesUtil.getClassType( containingClass );
      methodName = "constructor";
      isConstructor = true;
    }
    else
    {
      return null;
    }

    if( containingClass == null )
    {
      return null;
    }


    Map<String, PsiExpression> namedArgs = new LinkedHashMap<>();
    ArrayList<PsiExpression> unnnamedArgs = new ArrayList<>();
    List<PsiExpression> tupleItems = tupleExpr.getValueExpressions();
    if( tupleItems == null )
    {
      return null;
    }
    for( PsiExpression arg : tupleItems )
    {
      String name = arg instanceof ManPsiTupleValueExpression ? ((ManPsiTupleValueExpression)arg).getName() : null;
      if( name != null )
      {
        namedArgs.put( name, ((ManPsiTupleValueExpression)arg).getValue() );
      }
      else
      {
        if( !namedArgs.isEmpty() )
        {
          if( holder != null )
          {
            holder.newAnnotation( HighlightSeverity.ERROR, "Positional arguments must appear before named arguments" )
              .range( arg )
              .create();
          }
          return null;
        }
        unnnamedArgs.add( arg instanceof ManPsiTupleValueExpression ? ((ManPsiTupleValueExpression)arg).getValue() : arg );
      }
    }

    List<PsiExpression> values = tupleItems.stream()
      .map( e -> e instanceof ManPsiTupleValueExpression ? ((ManPsiTupleValueExpression)e).getValue() : e )
      .toList();

    List<PsiClass> paramsClasses = Arrays.stream( containingClass.getAllInnerClasses() )
    .filter( inner -> inner.getName() != null && inner.getName().startsWith( "$" + methodName + "_" ) )
    .toList();

    boolean errant;
    String missingRequiredParam = null;
    nextParamsClass:
    for( Iterator<PsiClass> iterParamsClasses = paramsClasses.iterator(); iterParamsClasses.hasNext(); )
    {
      PsiClass paramsClass = iterParamsClasses.next();

      errant = false;

      Map<String, PsiExpression> namedArgsCopy = new LinkedHashMap<>( namedArgs );
      ArrayList<PsiExpression> unnamedArgsCopy = new ArrayList<>( unnnamedArgs );

      PsiMethod[] ctors = paramsClass.getConstructors();
      if( ctors.length > 0 )
      {
        PsiMethod ctor = ctors[0];
        List<String> paramsInOrder = getParamNames( paramsClass, true );
        //noinspection SlowListContainsAll
        if( paramsInOrder.containsAll( namedArgsCopy.keySet() ) )
        {
          ArrayList<SrcExpression<?>> args = new ArrayList<>();
          ArrayList<SrcExpression<?>> explicitArgs = new ArrayList<>();
          Map<Integer, Integer> psiNewExprArgPosToTupleArgPos = new LinkedHashMap<>();

          boolean optional = false;
          PsiParameter[] params = ctor.getParameterList().getParameters();
          List<String> paramNames = getParamNames( paramsClass, false );
          int targetParamsOffset = 0;
          for( int i = 0; i < params.length; i++ )
          {
            PsiParameter param = params[i];
            if( args.isEmpty() && !isConstructor )
            {
              PsiClass enclosingClass = paramsClass.getContainingClass();
              if( enclosingClass == null )
              {
                // errant state
                return null;
              }

              SrcExpression<?> typeVarsExpr = makeTypeVarsExpr( enclosingClass.getName(), param, (PsiClassType)receiverType );
              if( typeVarsExpr != null )
              {
                // relays generic type info, needed bc it's not always there in the normal parameters, and explicitly parameterizing the call is a pita
                args.add( typeVarsExpr );
                targetParamsOffset++;
                continue;
              }
            }

            // .class files don't preserve param names, using encoded param names in paramsClass name,
            // in the list optional params are tagged with an "opt$" prefix
            String paramName = paramNames.get( i - targetParamsOffset );

            if( paramName.startsWith( "opt$" ) )
            {
              if( !optional )
              {
                // skip the constructor's $isXxx param
                optional = true;
                targetParamsOffset++;
                continue;
              }
              else
              {
                paramName = paramName.substring( "opt$".length() );
              }
            }

            PsiExpression expr;
            if( unnamedArgsCopy.isEmpty() )
            {
              expr = namedArgsCopy.remove( paramName );
            }
            else
            {
              expr = unnamedArgsCopy.remove( 0 );
            }

            if( optional )
            {
              args.add( new SrcRawExpression( boolean.class, expr != null ) );
              SrcRawExpression explicitArg = expr == null
                ? new SrcRawExpression( ComputeUtil.getDefaultValue( param.getType() ) )
                : new SrcRawExpression( expr.getText() );
              args.add( explicitArg );
              explicitArgs.add( explicitArg );
              optional = false;
            }
            else if( expr != null )
            {
              SrcRawExpression explicitArg = new SrcRawExpression( expr.getText() );
              args.add( explicitArg );
              explicitArgs.add( explicitArg );
            }
            else
            {
              // missing required arg, try next paramsClass
              missingRequiredParam = paramName;
              continue nextParamsClass;
            }

            if( expr != null )
            {
              psiNewExprArgPosToTupleArgPos.put( args.size() - 1, values.indexOf( expr ) );
            }
          }

          missingRequiredParam = null;

          if( !unnamedArgsCopy.isEmpty() )
          {
            //return null;
            // add remaining to trigger compile error
            args.addAll( unnamedArgsCopy.stream().map( e -> new SrcRawExpression( e.getText() ) ).toList() );
            errant = true;
          }
          else if( !namedArgsCopy.isEmpty() )
          {
            //return null;
            // add remaining to trigger compile error
            args.addAll( namedArgsCopy.values().stream().map( e -> new SrcRawExpression( e.getText() ) ).toList() );
            errant = true;
          }

          // if paramsClass is generic, generate a `new $foo<>(...)` expr and infer the type from that.
          // note, still need to generate new expr for non-generic params class to produce compile error for extra args
          if( !errant || !iterParamsClasses.hasNext() )
          {
            PsiTypeParameterList typeParameterList = paramsClass.getTypeParameterList();
            String paramsClassExpr = typeParameterList == null || typeParameterList.getTypeParameters().isEmpty()
              ? containingClass.getName() + '.' + paramsClass.getName()
              : containingClass.getName() + '.' + paramsClass.getName() + "<>";
            String newExpr = "new " + paramsClassExpr + "(" + makeArgsList( args ) + ")";

            PsiExpression psiNewExpr = JavaPsiFacade.getElementFactory( containingClass.getProject() )
              .createExpressionFromText( newExpr, tupleExpr );
            if( holder != null && psiNewExpr instanceof PsiNewExpression )
            {
              checkArgTypes( (PsiNewExpression)psiNewExpr, tupleExpr, holder, params, psiNewExprArgPosToTupleArgPos );
            }
            return (PsiClassType)psiNewExpr.getType();
          }
        }
        else if( holder != null && !iterParamsClasses.hasNext() )
        {
          putErrorOnBestMatchingMethod( holder, tupleExpr, namedArgsCopy, paramsClasses );
          return null;
        }
      }
    }
    if( holder != null && missingRequiredParam != null )
    {
      holder.newAnnotation( HighlightSeverity.ERROR, "Missing required argument: " + missingRequiredParam )
        .range( tupleExpr )
        .create();
    }
    return null;
  }

  private static void putErrorOnBestMatchingMethod( AnnotationHolder holder, ManPsiTupleExpression tupleExpr,
                                                    Map<String, PsiExpression> namedArgsCopy, List<PsiClass> paramsClasses )
  {
    List<String> badNamedArgs = Collections.emptyList();
    for( PsiClass paramsClass: paramsClasses )
    {
      List<String> paramsInOrder = getParamNames( paramsClass, true );
      List<String> candidate = namedArgsCopy.keySet().stream()
        .filter( e -> !paramsInOrder.contains( e ) )
        .toList();
      if( badNamedArgs.isEmpty() || candidate.size() < badNamedArgs.size() )
      {
        badNamedArgs = candidate;
      }
    }

    holder.newAnnotation( HighlightSeverity.ERROR, "No matching parameters for named argument[s]: '" + String.join( "', '", badNamedArgs ) + "'" )
      .range( tupleExpr )
      .create();
  }

  private static void checkArgTypes( PsiNewExpression psiNewExpr, ManPsiTupleExpression tupleExpr, AnnotationHolder holder,
                                     PsiParameter[] params, Map<Integer, Integer> psiNewExprArgPosToTupleArgPos )
  {
    PsiExpression[] tupleArgs = tupleExpr.getExpressions();
    for( int i = 0; i < params.length; i++ )
    {
      PsiParameter param = params[i];
      if( param.getName().startsWith( "opt$" ) )
      {
        continue;
      }

      PsiExpressionList argumentList = psiNewExpr.getArgumentList();
      PsiExpression newExprArg;
      if( argumentList == null )
      {
        newExprArg = null;
      }
      else
      {
        PsiExpression[] args = argumentList.getExpressions();
        if( args.length <= i )
        {
          continue;
        }
        newExprArg = args[i];
      }
      if( newExprArg == null || newExprArg.getType() == null )
      {
        continue;
      }

      Integer tupleArgIndex = psiNewExprArgPosToTupleArgPos.get( i );
      if( tupleArgIndex == null )
      {
        continue;
      }

      PsiExpression tupleArg = tupleArgs[tupleArgIndex];
      if( tupleArg == null )
      {
        continue;
      }


      PsiType argType = useNewExprSubstitutor( psiNewExpr, newExprArg.getType() );
      PsiType paramType = useNewExprSubstitutor( psiNewExpr, param.getType() );
      if( /*argType != null && paramType != null && */!isAssignable( paramType, argType ) )
      {
        TextRange tupleItemRange = getTupleItemRange( tupleExpr, tupleArgIndex, param.getName() );
        if( tupleItemRange != null )
        {
          holder.newAnnotation( HighlightSeverity.ERROR, "Incompatible types: '" + argType.getPresentableText() +
              "' cannot be converted to '" + paramType.getPresentableText() + "'" )
            .range( tupleItemRange )
            .create();
        }
      }
    }
  }
  private static PsiType useNewExprSubstitutor( PsiNewExpression psiNewExpr, PsiType type )
  {
    if( type == null )
    {
      return null;
    }

    PsiClassType resultType = (PsiClassType)psiNewExpr.getType();
    if( resultType == null )
    {
      return type;
    }

    PsiSubstitutor psiSubstitutor = resultType.resolveGenerics().getSubstitutor();
    return psiSubstitutor.substitute( type );
  }

  private static TextRange getTupleItemRange( ManPsiTupleExpression tupleExpr, int paramIndex, String name )
  {
    List<PsiExpression> valueExpressions = tupleExpr.getValueExpressions();
    if( valueExpressions == null )
    {
      return null;
    }

    for( PsiExpression ve : valueExpressions )
    {
      String veName = ve instanceof ManPsiTupleValueExpression ? ((ManPsiTupleValueExpression)ve).getName() : null;
      if( veName != null && veName.equals( name ) )
      {
        // found named tuple item matching param name
        return ((ManPsiTupleValueExpression)ve).getValue().getTextRange();
      }
    }

    if( valueExpressions.size() > paramIndex )
    {
      // use positional tuple time matching param index
      PsiExpression ve = valueExpressions.get( paramIndex );
      return ve.getTextRange();
    }
    return null;
  }

  private static void checkExpr( PsiExpression psiNewExpr, AnnotationHolder holder )
  {
    if( holder == null )
    {
      return;
    }
    for( Annotator ann: LanguageAnnotators.INSTANCE.allForLanguage( JavaLanguage.INSTANCE ) )
    {
      ann.annotate( psiNewExpr, holder );
    }
  }

  private static String makeArgsList( ArrayList<SrcExpression<?>> args )
  {
    StringBuilder sb = new StringBuilder();
    for( SrcExpression<?> arg : args )
    {
      if( !sb.isEmpty() )
      {
        sb.append( ", " );
      }
      arg.render( sb, 0 );
    }
    return sb.toString();
  }

  private static SrcExpression<?> makeTypeVarsExpr( String paramsClassOwner, PsiParameter param, PsiClassType receiverType )
  {
    if( param.getName().equals( "$" + ManStringUtil.uncapitalize( paramsClassOwner ) ) )
    {
      PsiClass receiverPsiClass = PsiTypesUtil.getPsiClass( receiverType );
      if( receiverPsiClass != null )
      {
        return new SrcRawExpression( "(" + receiverType.getPresentableText( false ) + ")null" );
      }
    }
    return null;
  }

  private static String makeTypeParamString( PsiTypeParameterList typeParameterList )
  {
    StringBuilder sb = new StringBuilder();
    for( PsiTypeParameter tp : typeParameterList.getTypeParameters() )
    {
      if( !sb.isEmpty() )
      {
        sb.append( "," );
      }
      sb.append( tp.getName() );
    }
    sb.insert( 0, '<' ).append( '>' );
    return sb.toString();
  }

  public static List<String> getParamNames( PsiClass paramsClass, boolean removeOpt$ )
  {
    List<String> result = new ArrayList<>();
    PsiAnnotation anno = paramsClass.getAnnotation( manifold_params.class.getTypeName() );
    if( anno == null )
    {
      throw new IllegalStateException( "Expecting '@" + manifold_params.class.getTypeName() + "' annotation" );
    }
    JvmAnnotationConstantValue value = (JvmAnnotationConstantValue) anno.getAttributes().get( 0 ).getAttributeValue();
    String paramsInfo = value == null ? null : (String)value.getConstantValue();
    if( paramsInfo == null )
    {
      throw new IllegalStateException( "Expecting non-null value for '@" + manifold_params.class.getTypeName() + "' annotation" );
    }

    StringTokenizer tokenizer = new StringTokenizer( paramsInfo, "_" );
    while( tokenizer.hasMoreTokens() )
    {
      String paramName = tokenizer.nextToken();
      if( removeOpt$ )
      {
        if( paramName.startsWith( "opt$" ) )
        {
          paramName = paramName.substring( "opt$".length() );
        }
      }
      result.add( paramName );
    }
    return result;
  }
}

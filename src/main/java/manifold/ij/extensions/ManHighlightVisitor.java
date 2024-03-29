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

package manifold.ij.extensions;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import manifold.ij.core.ManProject;
import manifold.util.ReflectUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ManHighlightVisitor extends HighlightVisitorImpl
{
//  private static final Logger LOG = Logger.getInstance( "#manifold.ij.extensions.ManHighlightVisitor" );

  @NotNull
  @Override
  public ManHighlightVisitor clone()
  {
    return new ManHighlightVisitor();
  }

  @Override
  public void visitPolyadicExpression( PsiPolyadicExpression expression )
  {
    if( !ManProject.isManifoldInUse( expression ) )
    {
      // Manifold jars are not used in the project
      super.visitPolyadicExpression( expression );
      return;
    }

    visitExpression( expression );
    Object myHolder = ReflectUtil.field( this, "myHolder" ).get();
    if( !(boolean)ReflectUtil.method( myHolder, "hasErrorResults" ).invoke() )
    {
      ReflectUtil.method( myHolder, "add", HighlightInfo.class ).invoke( checkPolyadicOperatorApplicable( expression ) );
    }
  }

  @Nullable
  private static HighlightInfo checkPolyadicOperatorApplicable( @NotNull PsiPolyadicExpression expression )
  {
    PsiExpression[] operands = expression.getOperands();

    PsiType lType = operands[0].getType();
    IElementType operationSign = expression.getOperationTokenType();
    for( int i = 1; i < operands.length; i++ )
    {
      PsiExpression operand = operands[i];
      PsiType rType = operand.getType();
      if( !TypeConversionUtil.isBinaryOperatorApplicable( operationSign, lType, rType, false ) )
      {
        if( expression instanceof PsiBinaryExpression &&
            ManJavaResolveCache.getTypeForOverloadedBinaryOperator( (PsiBinaryExpression)expression ) != null )
        {
          continue;
        }
        else if( isInsideBindingExpression( expression ) )
        {
          continue;
        }
        PsiJavaToken token = expression.getTokenBeforeOperand( operand );
        assert token != null : expression;
        String message = JavaErrorBundle.message( "binary.operator.not.applicable", token.getText(),
          JavaHighlightUtil.formatType( lType ),
          JavaHighlightUtil.formatType( rType ) );
        return HighlightInfo.newHighlightInfo( HighlightInfoType.ERROR ).range( expression ).descriptionAndTooltip( message ).create();
      }
      lType = TypeConversionUtil.calcTypeForBinaryExpression( lType, rType, operationSign, true );
    }

    return null;
  }

  private static boolean isInsideBindingExpression( PsiElement expression )
  {
    if( !(expression instanceof PsiBinaryExpression) )
    {
      return false;
    }

    if( ManJavaResolveCache.isBindingExpression( (PsiBinaryExpression)expression ) )
    {
      return true;
    }

    return isInsideBindingExpression( expression.getParent() );
  }

//  @Nullable
//  public static PsiType calcTypeForBinaryExpression( PsiType lType, PsiType rType, @NotNull IElementType sign, boolean accessLType )
//  {
//    if( sign == JavaTokenType.PLUS )
//    {
//      // evaluate right argument first, since '+-/*%' is left associative and left operand tends to be bigger
//      if( rType == null )
//      {
//        return null;
//      }
//      if( rType.equalsToText( JAVA_LANG_STRING ) )
//      {
//        return rType;
//      }
//      if( !accessLType )
//      {
//        return NULL_TYPE;
//      }
//      if( lType == null )
//      {
//        return null;
//      }
//      if( lType.equalsToText( JAVA_LANG_STRING ) )
//      {
//        return lType;
//      }
//      return unboxAndBalanceTypes( lType, rType );
//    }
//    if( sign == JavaTokenType.MINUS || sign == JavaTokenType.ASTERISK || sign == JavaTokenType.DIV || sign == JavaTokenType.PERC )
//    {
//      if( rType == null )
//      {
//        return null;
//      }
//      if( !accessLType )
//      {
//        return NULL_TYPE;
//      }
//      if( lType == null )
//      {
//        return null;
//      }
//      return unboxAndBalanceTypes( lType, rType );
//    }
//    if( sign == JavaTokenType.LTLT || sign == JavaTokenType.GTGT || sign == JavaTokenType.GTGTGT )
//    {
//      if( !accessLType )
//      {
//        return NULL_TYPE;
//      }
//      if( PsiType.BYTE.equals( lType ) || PsiType.CHAR.equals( lType ) || PsiType.SHORT.equals( lType ) )
//      {
//        return PsiType.INT;
//      }
//      if( lType instanceof PsiClassType )
//      {
//        lType = PsiPrimitiveType.getUnboxedType( lType );
//      }
//      return lType;
//    }
//    if( PsiBinaryExpression.BOOLEAN_OPERATION_TOKENS.contains( sign ) )
//    {
//      return PsiType.BOOLEAN;
//    }
//    if( sign == JavaTokenType.OR || sign == JavaTokenType.XOR || sign == JavaTokenType.AND )
//    {
//      if( rType instanceof PsiClassType )
//      {
//        rType = PsiPrimitiveType.getUnboxedType( rType );
//      }
//
//      if( lType instanceof PsiClassType )
//      {
//        lType = PsiPrimitiveType.getUnboxedType( lType );
//      }
//
//      if( rType == null )
//      {
//        return null;
//      }
//      if( PsiType.BOOLEAN.equals( rType ) )
//      {
//        return PsiType.BOOLEAN;
//      }
//      if( !accessLType )
//      {
//        return NULL_TYPE;
//      }
//      if( lType == null )
//      {
//        return null;
//      }
//      if( PsiType.BOOLEAN.equals( lType ) )
//      {
//        return PsiType.BOOLEAN;
//      }
//      if( PsiType.LONG.equals( lType ) || PsiType.LONG.equals( rType ) )
//      {
//        return PsiType.LONG;
//      }
//      return PsiType.INT;
//    }
//    LOG.error( "Unknown token: " + sign );
//    return null;
//  }

}

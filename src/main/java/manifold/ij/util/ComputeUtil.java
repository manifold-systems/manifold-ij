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

package manifold.ij.util;

import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.source.PsiClassReferenceType;

public class ComputeUtil
{
  public static Object computeLiteralValue( PsiNameValuePair value )
  {
    Object literalValue = null;
    PsiAnnotationMemberValue detachedValue = value.getDetachedValue();
    if( detachedValue instanceof PsiReferenceExpression )
    {
      PsiElement resolve = ((PsiReferenceExpression)detachedValue).resolve();
      if( resolve instanceof PsiVariable )
      {
        literalValue = ((PsiVariable)resolve).computeConstantValue();
      }
    }
    if( literalValue == null )
    {
      if( detachedValue instanceof PsiLiteralExpression )
      {
        PsiType type = ((PsiLiteralExpression)detachedValue).getType();
        if( type instanceof PsiPrimitiveType )
        {
          return ((PsiLiteralExpression)detachedValue).getValue();
        }
        else if( type instanceof PsiClassReferenceType )
        {
          int dims = type.getArrayDimensions();
          if( dims > 0 )
          {
            return ((PsiLiteralExpression)detachedValue).getValue();
          }
          else
          {
            return value.getLiteralValue();
          }
        }
      }
      //((PsiPrimitiveType)((PsiLiteralExpression)value.getDetachedValue()).getType()).getName()
      //((PsiClassReferenceType)((PsiLiteralExpression)value.getDetachedValue()).getType()).getReference().getQualifiedName()
      literalValue = value.getLiteralValue();
    }
    return literalValue == null ? null : literalValue.toString();
  }


  private static Object computePrimitiveValue( String name, String literalValue )
  {
    switch( name )
    {
      case "boolean":
        return Boolean.valueOf( literalValue );
      case "char":
        return literalValue.charAt( 0 );
      case "byte":
        return Byte.valueOf( literalValue );
      case "short":
        return Short.valueOf( literalValue );
      case "int":
        return Integer.valueOf( literalValue );
      case "long":
        return Long.valueOf( literalValue );
      case "float":
        return Float.valueOf( literalValue );
      case "double":
        return Double.valueOf( literalValue );
      default:
        throw new IllegalStateException( "\"" + name + "\" is not a primitive type name" );
    }
  }

  public static String getDefaultValue( PsiType type )
  {
    if( type instanceof PsiPrimitiveType )
    {
      if( type.getCanonicalText().equals( "boolean" ) )
      {
        return "false";
      }
      return "0";
    }
    return "null";
  }
}

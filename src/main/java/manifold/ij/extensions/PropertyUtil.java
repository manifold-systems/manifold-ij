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

import java.util.List;
import java.util.Objects;

import com.intellij.lang.jvm.annotation.JvmAnnotationArrayValue;
import com.intellij.lang.jvm.annotation.JvmAnnotationAttribute;
import com.intellij.lang.jvm.annotation.JvmAnnotationAttributeValue;
import com.intellij.lang.jvm.annotation.JvmAnnotationEnumFieldValue;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiNameValuePair;
import manifold.ext.props.rt.api.PropOption;
import manifold.ext.props.rt.api.get;
import manifold.ext.props.rt.api.set;
import manifold.ext.props.rt.api.val;
import manifold.ext.props.rt.api.var;

class PropertyUtil
{

  private PropertyUtil()
  {
    // Hide Utility Class Constructor
  }

  public static boolean hasOption( PsiField field, PropOption option )
  {
    for( Class<?> cls : List.of( var.class, val.class, get.class, set.class ) )
    {
      PsiAnnotation propAnno = field.getAnnotation( cls.getTypeName() );
      if( propAnno != null && hasOption( propAnno.getAttributes(), option ) )
      {
        return true;
      }
    }
    return false;
  }

  public static boolean hasOption( List<JvmAnnotationAttribute> args, PropOption option )
  {
    if( args == null )
    {
      return false;
    }
    for( JvmAnnotationAttribute attr : args )
    {
      if( hasOption( attr, option ) )
      {
        return true;
      }
    }
    return false;
  }

  private static boolean hasOption( JvmAnnotationAttribute e, PropOption option )
  {
    return e instanceof PsiNameValuePair && hasOption( e.getAttributeValue(), option );
  }

  private static boolean hasOption( JvmAnnotationAttributeValue value, PropOption option )
  {
    if( value instanceof JvmAnnotationEnumFieldValue v )
    {
      return Objects.equals( v.getFieldName(), option.name() );
    }
    else if ( value instanceof JvmAnnotationArrayValue v )
    {
      for( JvmAnnotationAttributeValue val : v.getValues() )
      {
        if( hasOption( val, option) )
        {
          return true;
        }
      }
    }
    return false;
  }
}

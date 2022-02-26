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

package manifold.ij.psi;

import com.intellij.openapi.diagnostic.Logger;
import java.lang.reflect.Field;

/**
 */
public class ReflectionUtil
{
  private static final Logger LOG = Logger.getInstance( ReflectionUtil.class.getName() );

  public static <T, R> void setFinalFieldPerReflection( Class<T> clazz, T instance, Class<R> oldClazz, R newValue )
  {
    try
    {
      for( Field field : clazz.getDeclaredFields() )
      {
        if( field.getType().equals( oldClazz ) )
        {
          field.setAccessible( true );
          field.set( instance, newValue );
          break;
        }
      }
    }
    catch( IllegalArgumentException | IllegalAccessException x )
    {
      LOG.error( x );
    }
  }
}


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

import com.intellij.psi.PsiModifier;
import com.sun.tools.javac.code.Flags;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum ModifierMap
{
  PUBLIC( PsiModifier.PUBLIC, Modifier.PUBLIC ),
  PROTECTED( PsiModifier.PROTECTED, Modifier.PROTECTED ),
  PRIVATE( PsiModifier.PRIVATE, Modifier.PRIVATE ),
  PACKAGE_LOCAL( PsiModifier.PACKAGE_LOCAL, 0 ),
  STATIC( PsiModifier.STATIC, Modifier.STATIC ),
  ABSTRACT( PsiModifier.ABSTRACT, Modifier.ABSTRACT ),
  FINAL( PsiModifier.FINAL, Modifier.FINAL ),
  NATIVE( PsiModifier.NATIVE, Modifier.NATIVE ),
  SYNCHRONIZED( PsiModifier.SYNCHRONIZED, Modifier.SYNCHRONIZED ),
  STRICTFP( PsiModifier.STRICTFP, Modifier.STRICT ),
  TRANSIENT( PsiModifier.TRANSIENT, Modifier.TRANSIENT ),
  VOLATILE( PsiModifier.VOLATILE, Modifier.VOLATILE ),
  DEFAULT( PsiModifier.DEFAULT, Flags.DEFAULT );

  private final String _name;
  private final long _mod;

  ModifierMap( String name, long mod )
  {
    _name = name;
    _mod = mod;
  }

  public String getName()
  {
    return _name;
  }

  public long getMod()
  {
    return _mod;
  }

  public static ModifierMap byName( String name )
  {
    return Arrays.stream( ModifierMap.values() )
      .filter( e -> e._name.equals( name ) )
      .findFirst()
      .orElseThrow( () -> new IllegalArgumentException( name ) );
  }

  public static ModifierMap byValue( int value )
  {
    return Arrays.stream( ModifierMap.values() )
      .filter( e -> e._mod == value )
      .findFirst()
      .orElseThrow( () -> new IllegalArgumentException( String.valueOf( value ) ) );
  }

  public static List<String> fromBits( long bits )
  {
    List<String> list = new ArrayList<>();
    for( ModifierMap value: values() )
    {
      if( (bits & value.getMod()) != 0 )
      {
        list.add( value.getName() );
      }
    }
    if( (bits & (Modifier.PRIVATE|Modifier.PROTECTED|Modifier.PUBLIC)) == 0 )
    {
      list.add( PACKAGE_LOCAL.getName() );
    }
    return list;
  }
}

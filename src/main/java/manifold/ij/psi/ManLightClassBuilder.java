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

import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.impl.light.LightPsiClassBuilder;
import com.intellij.psi.impl.light.LightTypeParameterListBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

public class ManLightClassBuilder extends LightPsiClassBuilder
{
  //  private final Collection<PsiField> _fields;
  private final LightModifierList _modifierList;
  private final boolean _isInterface;

  public ManLightClassBuilder( @NotNull PsiElement context, @NotNull String name, boolean isInterface )
  {
    super( context, name );
    _isInterface = isInterface;
//    _fields = new ArrayList<>();
    _modifierList = new ManLightModifierListImpl( context.getManager(), JavaLanguage.INSTANCE );
  }

//  public LightPsiClassBuilder addField( PsiField method )
//  {
//    if( method instanceof LightMethodBuilder )
//    {
//      ((LightMethodBuilder)method).setContainingClass( this );
//    }
//
//    _fields.add( method );
//    return this;
//  }

  @Override
  public boolean isInterface()
  {
    return _isInterface;
  }

  @Override
  public @NotNull LightModifierList getModifierList()
  {
    return _modifierList;
  }

  @SuppressWarnings( "UnusedReturnValue" )
  public ManLightClassBuilder withTypeParameterDirect( PsiTypeParameter typeParameter )
  {
    LightTypeParameterListBuilder typeParameterList = getTypeParameterList();
    Objects.requireNonNull( typeParameterList ).addParameter( typeParameter );
    return this;
  }

  @Override
  public boolean equals( Object o )
  {
    if( this == o ) return true;
    if( o == null || getClass() != o.getClass() ) return false;
    ManLightClassBuilder that = (ManLightClassBuilder)o;
    return getName().equals( that.getName() ) &&
      _isInterface == that._isInterface;
  }

  @Override
  public int hashCode()
  {
    return Objects.hash( getName(), _isInterface );
  }
}

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

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightIdentifier;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.impl.light.LightParameter;
import com.intellij.psi.impl.light.LightVariableBuilder;

/**
 */
public class ManLightParameterImpl extends LightParameter
{
  private final LightIdentifier myNameIdentifier;

  public ManLightParameterImpl( String name, PsiType type, PsiElement declarationScope, Language language )
  {
    super( name, type, declarationScope, language );
    PsiManager manager = declarationScope.getManager();
    myNameIdentifier = new LightIdentifier( manager, name );
    ReflectionUtil.setFinalFieldPerReflection( LightVariableBuilder.class, this, LightModifierList.class,
                                               new ManLightModifierListImpl( manager, language ) );
  }

  @Override
  public PsiIdentifier getNameIdentifier()
  {
    return myNameIdentifier;
  }

  public ManLightParameterImpl setModifiers( String... modifiers )
  {
    ManLightModifierListImpl modifierList = new ManLightModifierListImpl( getManager(), getLanguage(), modifiers );
    ReflectionUtil.setFinalFieldPerReflection( LightVariableBuilder.class, this, LightModifierList.class, modifierList );
    return this;
  }
}

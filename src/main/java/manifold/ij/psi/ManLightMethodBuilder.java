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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import java.util.Set;
import manifold.ij.core.ManModule;

/**
 */
public interface ManLightMethodBuilder extends PsiMethod
{
  ManLightMethodBuilder withNavigationElement( PsiElement navigationElement );

  ManLightMethodBuilder withContainingClass( PsiClass containingClass );

  ManLightMethodBuilder withModifier( String modifier );

  ManLightMethodBuilder withMethodReturnType( PsiType returnType );

  ManLightMethodBuilder withParameter( String name, PsiType type );

  ManLightMethodBuilder withException( PsiClassType type );

  ManLightMethodBuilder withException( String fqName );

  ManLightMethodBuilder withTypeParameter( PsiTypeParameter typeParameter );
  /**
   * Uses {@code typeParameter} directly instead of wrapping it in a light type parameter.
   * This appears to be necessary for most PSI method injection/extension/replacement code
   */
  ManLightMethodBuilder withTypeParameterDirect( PsiTypeParameter typeParameter );

  ManLightMethodBuilder withAdditionalModule( ManModule module );

  ManLightMethodBuilder withConstructor( boolean constructor );

  ManModule getModule();
  Set<ManModule> getModules();
}

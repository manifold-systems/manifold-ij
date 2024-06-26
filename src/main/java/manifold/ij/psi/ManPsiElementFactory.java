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

import com.intellij.psi.*;
import manifold.ij.core.ManModule;

/**
 */
public class ManPsiElementFactory
{
  private static ManPsiElementFactory INSTANCE = new ManPsiElementFactory();

  public static ManPsiElementFactory instance()
  {
    return INSTANCE;
  }

  private ManPsiElementFactory()
  {
  }

  public ManLightFieldBuilder createLightField( PsiManager manager, String fieldName, PsiType fieldType, boolean isProperty )
  {
    return new ManLightFieldBuilderImpl( manager, fieldName, fieldType, isProperty );
  }

  public ManLightMethodBuilder createLightMethod( ManModule manModule, PsiManager manager, String methodName )
  {
    return createLightMethod( manModule, manager, methodName, null );
  }
  public ManLightMethodBuilder createLightMethod( ManModule manModule, PsiManager manager, String methodName, PsiModifierList modifierList )
  {
    return new ManLightMethodBuilderImpl( manModule, manager, methodName, modifierList );
  }

  public ManLightMethod createLightMethod( PsiManager manager, PsiMethod valuesMethod, PsiClass psiClass )
  {
    return new ManLightMethodImpl( manager, valuesMethod, psiClass );
  }

  public ManExtensionMethodBuilder createExtensionMethodMethod( ManModule manModule, PsiManager manager, String methodName, PsiMethod extMethodImpl )
  {
    return new ManExtensionMethodBuilder( manModule, manager, methodName, null, extMethodImpl );
  }
}

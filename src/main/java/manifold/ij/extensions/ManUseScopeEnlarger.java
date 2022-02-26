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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UseScopeEnlarger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Expands use-scope for elements in resource files that are represented as types via Manifold. For instance, a JSON
 * file have a "Foo" JSON property has a use-scope matching the "Foo" property of the Manifold-generated Java type. In
 * essence, the scope potentially enlarges to include modules dependent on the module enclosing the resource file.
 */
public class ManUseScopeEnlarger extends UseScopeEnlarger
{
  private static final ThreadLocal<Boolean> SHORT_CIRCUIT = ThreadLocal.withInitial( () -> false );

  @Nullable
  @Override
  public SearchScope getAdditionalUseScope( @NotNull PsiElement element )
  {
    if( SHORT_CIRCUIT.get() )
    {
      return null;
    }
    SHORT_CIRCUIT.set( true );
    try
    {
      if( !(element instanceof PsiModifierListOwner) )
      {
        Set<PsiModifierListOwner> javaElements = ResourceToManifoldUtil.findJavaElementsFor( element );
        if( !javaElements.isEmpty() )
        {
          return javaElements.iterator().next().getUseScope();
        }
      }
    }
    finally
    {
      SHORT_CIRCUIT.set( false );
    }
    return null;
  }
}

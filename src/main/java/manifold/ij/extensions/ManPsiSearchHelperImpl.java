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

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.search.PsiSearchHelperImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiNonJavaFileReferenceProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ManPsiSearchHelperImpl extends PsiSearchHelperImpl
{
//  @NonInjectable
//  public ManPsiSearchHelperImpl( @NotNull PsiManagerEx manager )
//  {
//    super( manager );
//  }

  public ManPsiSearchHelperImpl( @NotNull Project project )
  {
    super( project );
  }

  @Override
  public boolean processUsagesInNonJavaFiles( @Nullable final PsiElement originalElement,
                                              @NotNull String qName,
                                              @NotNull final PsiNonJavaFileReferenceProcessor processor,
                                              @NotNull final GlobalSearchScope initialScope )
  {
    if( qName.isEmpty() )
    {
      // There is a bug in IJ dealing with PsiMethod with empty name coming from Manifold Templates.
      // We avoid the super class throwing an exception if empty string.
      return false;
    }

    return super.processUsagesInNonJavaFiles( originalElement, qName, processor, initialScope );
  }
}

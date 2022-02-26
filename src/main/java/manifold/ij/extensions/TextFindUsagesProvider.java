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

import com.intellij.lang.HelpID;
import com.intellij.lang.cacheBuilder.SimpleWordsScanner;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import manifold.ij.core.ManProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Facilitates Find Usages functionality for text files
 */
public class TextFindUsagesProvider implements FindUsagesProvider
{
  @Nullable
  @Override
  public WordsScanner getWordsScanner()
  {
    return new SimpleWordsScanner();
  }

  @Override
  public boolean canFindUsagesFor( @NotNull PsiElement psiElement )
  {
    if( !ManProject.isManifoldInUse( psiElement ) )
    {
      return false;
    }

    return psiElement instanceof PsiNamedElement;
  }

  @Nullable
  @Override
  public String getHelpId( @NotNull PsiElement psiElement )
  {
    if( !ManProject.isManifoldInUse( psiElement ) )
    {
      return null;
    }

    return HelpID.FIND_OTHER_USAGES;
  }

  @NotNull
  @Override
  public String getType( @NotNull PsiElement element )
  {
    return "";
  }

  @NotNull
  @Override
  public String getDescriptiveName( @NotNull PsiElement element )
  {
    return element instanceof FakeTargetElement ? ((FakeTargetElement)element).getName() : element.getText();
  }

  @NotNull
  @Override
  public String getNodeText( @NotNull PsiElement element, boolean useFullName )
  {
    return element.getText();
  }
}

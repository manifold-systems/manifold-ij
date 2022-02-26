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
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.refactoring.rename.PreferrableNameSuggestionProvider;
import java.util.Set;
import manifold.ij.core.ManProject;
import org.jetbrains.annotations.Nullable;

/**
 * A hack to fix an issue with JS GraphQL plugin where the GraphQLVariable PSI (for a variable def e.g., $title) returns
 * null instead of "$title" when {@code element.getName()} is called.
 */
public class ManNameSuggestionProvider extends PreferrableNameSuggestionProvider
{
  @Nullable
  @Override
  public SuggestedNameInfo getSuggestedNames( PsiElement element, @Nullable PsiElement nameSuggestionContext, Set<String> result )
  {
    if( !ManProject.isManifoldInUse( element ) )
    {
      // Manifold jars are not used in the project
      return null;
    }

    if( element instanceof PsiNamedElement &&
        ((PsiNamedElement)element).getName() == null &&
        element.getClass().getTypeName().contains( "GraphQLVariable" ) )
    {
      String text = element.getText();
      if( text != null && text.startsWith( "$" ) )
      {
        result.clear();
        result.add( text );
      }
    }
    return null;
  }
}

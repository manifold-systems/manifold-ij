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

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;

/**
 * For Manifold's StringLiteral LanguageInjector, prevents "assigned but never accessed" warnings when there is a usage
 * in a String literal via Manifold string interpolation.
 */
public class ManStringLiteralTemplateUsageProvider implements ImplicitUsageProvider
{
  @Override
  public boolean isImplicitUsage( PsiElement element )
  {
    return isReferencedFromManStringLiteral( element );
  }

  @Override
  public boolean isImplicitRead( PsiElement element )
  {
    return isReferencedFromManStringLiteral( element );
  }

  @Override
  public boolean isImplicitWrite( PsiElement element )
  {
    return false;
  }

  private boolean isReferencedFromManStringLiteral( PsiElement elem )
  {
    PsiNamedElement namedElement = findNamedElement( elem );
    if( namedElement != null )
    {
      PsiFile containingFile = elem.getContainingFile();
      if( containingFile instanceof PsiJavaFile )
      {
        try
        {
          boolean result = !ReferencesSearch.search( namedElement,
            new LocalSearchScope( containingFile ) )
            .forEach( (Processor)e ->
              // keep looking while _not_ in a string literal,
              // if found in a string literal, stop the search
              !isInStringLiteral( ((PsiReference)e).getElement() ) );
          return result;
        }
        catch( Throwable t )
        {
          if( !(t instanceof ProcessCanceledException) )
          {
            System.out.println( t );
          }
        }
      }
    }
    return false;
  }

  private boolean isInStringLiteral( PsiElement element )
  {
    if( element == null )
    {
      return false;
    }
    if( element instanceof PsiField &&
        ManStringLiteralTemplateInjector.FIELD_NAME.equals( ((PsiField)element).getName() ) )
    {
      return true;
    }
    return isInStringLiteral( element.getParent() );
  }

  private PsiNamedElement findNamedElement( PsiElement elem )
  {
    if( elem == null )
    {
      return null;
    }
    int offset = elem.getTextOffset();
    while( elem != null && elem.getTextOffset() == offset && !(elem instanceof PsiNamedElement) )
    {
      elem = elem.getParent();
    }
    return (PsiNamedElement)elem;
  }
}

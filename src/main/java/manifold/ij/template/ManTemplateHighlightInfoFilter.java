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

package manifold.ij.template;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import manifold.ij.core.ManProject;
import manifold.ij.template.psi.DirectiveParser;
import manifold.ij.template.psi.ManTemplateJavaFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ManTemplateHighlightInfoFilter implements HighlightInfoFilter
{
  @Override
  public boolean accept( @NotNull HighlightInfo hi, @Nullable PsiFile file )
  {
    if( file == null )
    {
      return true;
    }

    if( !ManProject.isManifoldInUse( file ) )
    {
      // Manifold jars are not used in the project
      return true;
    }

    if( isNotInititializedErrorOnParamRef( hi, file ) ||
        isNeverAssignedOnParam( hi, file ) )
    {
      return false;
    }

    return true;
  }

  private boolean isNeverAssignedOnParam( @NotNull HighlightInfo hi, @Nullable PsiFile file )
  {
    if( !(file instanceof ManTemplateJavaFile) )
    {
      return false;
    }

    PsiElement elem = file.findElementAt( hi.getStartOffset() );
    if( !(elem instanceof PsiIdentifier) )
    {
      return false;
    }

    PsiElement param = elem.getParent();
    if( !(param instanceof PsiLocalVariable) )
    {
      return false;
    }

    try
    {
      if( isParamsParent( param ) )
      {
        if( hi.getDescription().contains( "never assigned" ) ||

            hi.getDescription().contains( "but never updated" ) ||
            hi.getDescription().contains( "可能尚未初始化" ) )
        {
          return true;
        }
      }
    }
    catch( Exception ignore ) {}

    return false;
  }

  private boolean isParamsParent( PsiElement param )
  {
    PsiElement parent = param.getParent();
    if( parent == null )
    {
      return false;
    }

    PsiElement csr = parent;
    while( true )
    {
      csr = csr.getPrevSibling();
      if( csr instanceof PsiIdentifier )
      {
        return csr.getText().equals( DirectiveParser.PARAMS );
      }
      if( csr == null )
      {
        return false;
      }
    }
  }

  private boolean isNotInititializedErrorOnParamRef( @NotNull HighlightInfo hi, @Nullable PsiFile file )
  {
    if( !(file instanceof ManTemplateJavaFile) ||
        hi.getSeverity() != HighlightSeverity.ERROR )
    {
      return false;
    }

    PsiElement elem = file.findElementAt( hi.getStartOffset() );
    if( !(elem instanceof PsiIdentifier) )
    {
      return false;
    }

    PsiElement parent = elem.getParent();
    if( !(parent instanceof PsiReferenceExpression) )
    {
      return false;
    }

    PsiReference reference = parent.getReference();
    if( reference == null )
    {
      return false;
    }

    PsiElement param = reference.resolve();
    if( !(param instanceof PsiLocalVariable) )
    {
      return false;
    }

    try
    {
      if( isParamsParent( param ) )
      {
        String desc = hi.getDescription();
        if( desc.contains( "might not have been initialized" ) ||
            desc.contains( "可能尚未初始化" ) )
        {
          return true;
        }
      }
    }
    catch( Exception ignore ) {}

    return false;
  }
}

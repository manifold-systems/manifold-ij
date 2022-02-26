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

import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import manifold.ij.core.ManProject;
import manifold.ij.template.psi.DirectiveParser;
import org.jetbrains.annotations.NotNull;

public class ManTemplateJavaAnnotator implements Annotator
{
  @Override
  public void annotate( @NotNull final PsiElement element, @NotNull AnnotationHolder holder )
  {
    if( !ManProject.isManifoldInUse( element ) )
    {
      // Manifold jars are not used in the project
      return;
    }

    if( element.getContainingFile().getLanguage() != ManTemplateJavaLanguage.INSTANCE )
    {
      return;
    }

    highlightDirectiveKeywords( element, holder );
    checkImportPosition( element, holder );
    checkExtendsPosition( element, holder );
    checkParamsPosition( element, holder );
  }

  private void checkImportPosition( PsiElement element, AnnotationHolder holder )
  {
    if( element instanceof PsiImportStatement )
    {
      boolean misplaced = false;
      if( !(element.getParent() instanceof PsiImportList) ||
          !(element.getParent().getParent() instanceof PsiFile) )
      {
        misplaced = true;
      }
      else
      {
        PsiElement csr = element.getPrevSibling();
        while( csr != null )
        {
          if( !(csr instanceof PsiWhiteSpace) &&
              !(csr instanceof OuterLanguageElement) &&
              !(csr instanceof PsiImportStatement) )
          {
            misplaced = true;
            break;
          }
          csr = csr.getPrevSibling();
        }
      }
      if( misplaced )
      {
        int startOffset = element.getTextRange().getStartOffset() + element.getText().indexOf( DirectiveParser.IMPORT );
        TextRange range = new TextRange( startOffset, startOffset + DirectiveParser.IMPORT.length() );
        holder.newAnnotation( HighlightSeverity.ERROR, "Illegal placement of 'import' directive, must precede others" )
          .range( range )
          .tooltip( "Illegal placement of <code>import</code> directive, must precede others." )
          .create();
      }
    }
  }

  private void checkExtendsPosition( PsiElement element, AnnotationHolder holder )
  {
    if( element instanceof PsiReferenceList && !element.getText().isEmpty() )
    {
      if( !(element.getParent() instanceof PsiClass) )
      {
        int startOffset = element.getTextRange().getStartOffset() + element.getText().indexOf( DirectiveParser.EXTENDS );
        TextRange range = new TextRange( startOffset, startOffset + DirectiveParser.EXTENDS.length() );
        holder.newAnnotation( HighlightSeverity.ERROR, "Illegal placement of 'extends' directive, must follow 'import' directives and precede others" )
          .range( range )
          .tooltip( "Illegal placement of <code>extends</code> directive, must follow <code>import</code> directives and precede others." )
          .create();
      }
      else
      {
        PsiJavaCodeReferenceElement[] refs = ((PsiReferenceList)element).getReferenceElements();
        if( refs.length > 0 )
        {
          PsiClass baseClass = JavaPsiFacadeEx.getInstanceEx( element.getProject() ).findClass( "manifold.templates.runtime.BaseTemplate" );
          PsiClass extendedClass = (PsiClass)refs[0].resolve();
          if( baseClass != null && extendedClass != null && !extendedClass.equals( baseClass ) && !extendedClass.isInheritor( baseClass, true ) )
          {
            int startOffset = refs[0].getTextOffset();
            TextRange range = new TextRange( startOffset, startOffset + refs[0].getTextLength() );
            holder.newAnnotation( HighlightSeverity.ERROR, "Extended class '${extendedClass.getName()}' does not inherit from 'manifold.templates.runtime.BaseTemplate'" )
              .range( range )
              .tooltip( "Extended class <code>${extendedClass.getName()}</code> does not inherit from <code>manifold.templates.runtime.BaseTemplate</code>" )
              .create();
          }
        }
      }
    }
  }

  private void checkParamsPosition( PsiElement element, AnnotationHolder holder )
  {
    if( element instanceof PsiIdentifier &&
        element.getText().equals( DirectiveParser.PARAMS ) )
    {
      if( !(element.getParent() instanceof PsiCodeBlock) ||
          (element.getParent().getFirstChild() != element &&
           element.getParent().getFirstChild().getNextSibling() != element) )
      {
        int startOffset = element.getTextRange().getStartOffset() + element.getText().indexOf( DirectiveParser.PARAMS );
        TextRange range = new TextRange( startOffset, startOffset + DirectiveParser.PARAMS.length() );
        holder.newAnnotation( HighlightSeverity.ERROR, "Illegal placement of 'params' directive, must follow 'import' and 'extends' directives and precede others" )
          .range( range )
          .tooltip( "Illegal placement of <code>params</code> directive, must follow <code>import</code> and <code>extends</code> directives and precede others." )
          .create();
      }
    }
  }

  private void highlightDirectiveKeywords( @NotNull PsiElement element, @NotNull AnnotationHolder holder )
  {
    if( element instanceof PsiIdentifier && isInManTLFile( element ) && isDirectiveKeyword( element ) )
    {
      holder.newAnnotation( HighlightSeverity.INFORMATION, "" )
        .range( element.getTextRange() )
        .textAttributes( JavaHighlightingColors.KEYWORD )
        .create();
    }
  }

  private boolean isDirectiveKeyword( @NotNull PsiElement element )
  {
    String text = element.getText();
    switch( text )
    {
      case DirectiveParser.IMPORT:
      case DirectiveParser.EXTENDS:
      case DirectiveParser.PARAMS:
      case DirectiveParser.INCLUDE:
      case DirectiveParser.NEST:
      case DirectiveParser.SECTION:
      case DirectiveParser.END:
      case DirectiveParser.LAYOUT:
      case DirectiveParser.CONTENT:
        return true;
    }
    return false;
  }

  private boolean isInManTLFile( @NotNull PsiElement element )
  {
    return element.getContainingFile().getLanguage() == ManTemplateJavaLanguage.INSTANCE;
  }
}
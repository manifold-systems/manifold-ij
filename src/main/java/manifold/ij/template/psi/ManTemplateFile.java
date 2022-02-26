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

package manifold.ij.template.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiImportHolder;
import com.intellij.psi.ServerPageFile;
import manifold.ij.core.ManLibraryChecker;
import manifold.ij.template.ManTemplateFileType;
import manifold.ij.template.ManTemplateLanguage;
import org.jetbrains.annotations.NotNull;


import static manifold.ij.template.psi.ManTemplateParser.Comment;
import static manifold.ij.template.psi.ManTemplateParser.Directive;

public class ManTemplateFile extends PsiFileBase
  implements PsiImportHolder, //!! must implement PsiImportHolder for full pass analysis and error highlighting to work, see HighlightVisitorImpl#suitableForFile
  ServerPageFile   //!! musts implement ServerPageFile to avoid psi checking at the file level e.g., no package stmt etc.
{
  ManTemplateFile( @NotNull FileViewProvider viewProvider )
  {
    super( viewProvider, ManTemplateLanguage.INSTANCE );
  }

  @NotNull
  @Override
  public FileType getFileType()
  {
    return ManTemplateFileType.INSTANCE;
  }

  @Override
  public String toString()
  {
    return "Manifold Template File";
  }

  @Override
  public boolean importClass( PsiClass importClass )
  {
    ManTemplateElementImpl parent = (ManTemplateElementImpl)getFirstChild();
    PsiElement lastImport = findLastImport( parent );
    if( lastImport != null )
    {
      ManTemplateFile dummyFile = (ManTemplateFile)PsiFileFactory.getInstance( getProject() )
        .createFileFromText( "dummy.mtl", ManTemplateFileType.INSTANCE,
          "\n<%@ import ${importClass.getQualifiedName()} %>" );
      PsiElement newLine = dummyFile.getFirstChild().getFirstChild();
      PsiElement importDirective = newLine.getNextSibling();

      parent.addRangeAfter( newLine, importDirective, lastImport );
    }
    else
    {
      ManTemplateFile dummyFile = (ManTemplateFile)PsiFileFactory.getInstance( getProject() )
        .createFileFromText( "dummy.mtl", ManTemplateFileType.INSTANCE,
          "<%@ import ${importClass.getQualifiedName()} %>\n" );
      PsiElement importDirective = dummyFile.getFirstChild().getFirstChild();
      PsiElement newLine = importDirective.getNextSibling();

      parent.addRangeBefore( importDirective, newLine, parent.getFirstChild() );
    }
    return true;
  }

  private PsiElement findLastImport( ManTemplateElementImpl parent )
  {
    ManTemplateElementImpl lastImport = null;
    PsiElement csr = parent.getFirstChild();
    while( csr != null )
    {
      if( csr instanceof ManTemplateElementImpl )
      {
        ManTemplateElementImpl elem = (ManTemplateElementImpl)csr;
        if( isImportDirective( elem ) )
        {
          lastImport = elem;
          csr = csr.getNextSibling();
          continue;
        }
        else if( isCommentDirective( elem ) )
        {
          csr = csr.getNextSibling();
          continue;
        }
      }
      else if( csr instanceof ManTemplateTokenImpl )
      {
        ManTemplateTokenImpl token = (ManTemplateTokenImpl)csr;
        if( token.getTokenType() == ManTemplateTokenType.CONTENT )
        {
          csr = csr.getNextSibling();
          continue;
        }
      }
      break;
    }
    return lastImport;
  }

  private boolean isImportDirective( ManTemplateElementImpl elem )
  {
    final ASTNode directive = elem.getNode();
    if( directive.getElementType() == Directive )
    {
      final ASTNode child = directive.getFirstChildNode();
      if( child.getElementType() == ManTemplateTokenType.DIR_ANGLE_BEGIN &&
          child.getTreeNext() != null && child.getTreeNext().getElementType() == ManTemplateTokenType.DIRECTIVE )
      {
        String text = child.getTreeNext().getText().trim();
        return text.startsWith( DirectiveParser.IMPORT );
      }
    }
    return false;
  }

  private boolean isCommentDirective( ManTemplateElementImpl elem )
  {
    final ASTNode directive = elem.getNode();
    return directive.getElementType() == Comment;
  }
}
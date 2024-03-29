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

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.PsiElementBase;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 */
class FakeTargetElement extends PsiElementBase implements PsiMetaOwner, PsiMetaData, PsiNamedElement
{
  private final PsiFile _file;
  private final int _iOffset;
  private int _iLength;
  private final String _kind;
  private String _name;

  FakeTargetElement( PsiFile file, int iOffset, int iLength, String name, String kind )
  {
    _file = file;
    _iOffset = iOffset;
    _iLength = iLength;
    _name = name;
    _kind = kind;
  }

  public String getKind()
  {
    return _kind;
  }

  @NotNull
  @Override
  public Language getLanguage()
  {
    return _file.getLanguage();
  }

  @Override
  public PsiManager getManager()
  {
    return _file.getManager();
  }

  @Override
  public PsiElement getParent()
  {
    return _file;
  }

  @NotNull
  @Override
  public PsiElement[] getChildren()
  {
    return PsiElement.EMPTY_ARRAY;
  }

  @Override
  public PsiFile getContainingFile()
  {
    return _file;
  }

  @Override
  public TextRange getTextRange()
  {
    return new TextRange( _iOffset, _iOffset + _iLength );
  }

  @Override
  public int getStartOffsetInParent()
  {
    return _iOffset;
  }

  @Override
  public final int getTextLength()
  {
    return _iLength;
  }

  @NotNull
  @Override
  public char[] textToCharArray()
  {
    return getText().substring( _iOffset, _iOffset + _iLength ).toCharArray();
  }

  @Override
  public boolean textMatches( CharSequence text )
  {
    return getText().equals( text.toString() );
  }

  @Override
  public boolean textMatches( PsiElement element )
  {
    return getText().equals( element.getText() );
  }

  @Override
  public PsiElement findElementAt( int offset )
  {
    return this;
  }

  @Override
  public int getTextOffset()
  {
    return _iOffset;
  }

  @Override
  public boolean isValid()
  {
    return true;
  }

  @Override
  public boolean isWritable()
  {
    return false;
  }

  @Override
  public boolean isPhysical()
  {
    return false;
  }

  @Override
  public String toString()
  {
    return _name;
  }

  @Override
  public void checkAdd( @NotNull PsiElement element ) throws IncorrectOperationException
  {
    throw new IncorrectOperationException( getClass().getName() );
  }

  @Override
  public PsiElement add( @NotNull PsiElement element ) throws IncorrectOperationException
  {
    throw new IncorrectOperationException( getClass().getName() );
  }

  @Override
  public PsiElement addBefore( @NotNull PsiElement element, PsiElement anchor ) throws IncorrectOperationException
  {
    throw new IncorrectOperationException( getClass().getName() );
  }

  @Override
  public PsiElement addAfter( @NotNull PsiElement element, PsiElement anchor ) throws IncorrectOperationException
  {
    throw new IncorrectOperationException( getClass().getName() );
  }

  @Override
  public void delete() throws IncorrectOperationException
  {
    throw new IncorrectOperationException( getClass().getName() );
  }

  @Override
  public void checkDelete() throws IncorrectOperationException
  {
    throw new IncorrectOperationException( getClass().getName() );
  }

  @Override
  public PsiElement replace( @NotNull PsiElement newElement ) throws IncorrectOperationException
  {
    throw new IncorrectOperationException( getClass().getName() );
  }

  @Override
  public ASTNode getNode()
  {
    return null;
  }

  @Override
  public String getText()
  {
    return _name;
  }

  @Override
  public PsiElement getDeclaration()
  {
    return this;
  }

  @Override
  public String getName( PsiElement context )
  {
    return getName();
  }

  @Override
  public String getName()
  {
    return _name;
  }

  @Override
  public PsiElement setName( @NotNull String name ) throws IncorrectOperationException
  {
    findDocument().replaceString( _iOffset, _iOffset + _name.length(), name );
    _iLength += name.length() - _name.length();
    _name = name;
    return this;
  }

  private Document findDocument()
  {
    Editor editor = ResourceToManifoldUtil.getActiveEditor( getProject() );
    if( editor instanceof EditorImpl )
    {
      EditorImpl editorImpl = (EditorImpl)editor;
      if( editorImpl.getVirtualFile().getPath().equals( _file.getVirtualFile().getPath() ) )
      {
        // get document from current editor
        return editorImpl.getDocument();
      }
    }

    // get document from file
    return _file.getViewProvider().getDocument();
  }

  @Override
  public void init( PsiElement element )
  {

  }

  @Override
  public void accept( @NotNull PsiElementVisitor visitor )
  {
  }

  @Override
  public PsiElement copy()
  {
    return null;
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement()
  {
    return this;
  }

  @Override
  public PsiElement getPrevSibling()
  {
    return null;
  }

  @Override
  public PsiElement getNextSibling()
  {
    return null;
  }

  @Nullable
  @Override
  public PsiMetaData getMetaData()
  {
    return this;
  }
}

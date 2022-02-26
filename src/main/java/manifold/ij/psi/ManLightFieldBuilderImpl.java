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
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.psi.impl.light.LightIdentifier;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.util.IncorrectOperationException;

/**
 */
public class ManLightFieldBuilderImpl extends LightFieldBuilder implements ManLightFieldBuilder
{
  protected final LightIdentifier _nameIdentifier;
  private final boolean _isProperty;

  public ManLightFieldBuilderImpl( PsiManager manager, String name, PsiType type, boolean isProperty )
  {
    super( manager, name, type );
    _nameIdentifier = new LightIdentifier( manager, name );
    _isProperty = isProperty;
  }

  public boolean isProperty()
  {
    return _isProperty;
  }

  @Override
  public ManLightFieldBuilder withContainingClass( PsiClass psiClass )
  {
    setContainingClass( psiClass );
    return this;
  }

  @Override
  public ManLightFieldBuilder withModifier( @PsiModifier.ModifierConstant String modifier )
  {
    ((LightModifierList)getModifierList()).addModifier( modifier );
    return this;
  }

  @Override
  public ManLightFieldBuilder withModifierList( LightModifierList modifierList )
  {
    setModifierList( modifierList );
    return this;
  }

  @Override
  public ManLightFieldBuilder withNavigationElement( PsiElement navigationElement )
  {
    setNavigationElement( navigationElement );
    return this;
  }

  @Override
  public PsiIdentifier getNameIdentifier()
  {
    return _nameIdentifier;
  }

  @Override
  public PsiFile getContainingFile()
  {
    PsiClass psiClass = getContainingClass();
    return psiClass == null ? null : psiClass.getContainingFile();
  }

  public String toString()
  {
    return "ManifoldLightFieldBuilder: " + getName();
  }

  @Override
  public PsiElement replace( PsiElement newElement ) throws IncorrectOperationException
  {
    // just add new element to the containing class
    final PsiClass containingClass = getContainingClass();
    if( null != containingClass )
    {
      CheckUtil.checkWritable( containingClass );
      return containingClass.add( newElement );
    }
    return null;
  }

  @Override
  public void delete() throws IncorrectOperationException
  {
  }

  @Override
  public void checkDelete() throws IncorrectOperationException
  {
  }
}

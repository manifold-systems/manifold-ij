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

import com.intellij.lang.ASTNode;
import com.intellij.psi.EmptySubstitutor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.light.LightMethod;
import com.intellij.psi.impl.light.LightParameterListWrapper;
import com.intellij.psi.impl.light.LightParameterWrapper;
import com.intellij.util.IncorrectOperationException;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 */
public class ManLightMethodImpl extends LightMethod implements ManLightMethod
{
  private final PsiMethod _method;
  private PsiType _returnType;
  private List<PsiParameter> _parameters;

  public ManLightMethodImpl( PsiManager manager, PsiMethod valuesMethod, PsiClass psiClass )
  {
    super( manager, valuesMethod, psiClass );
    _method = valuesMethod;
    _parameters = new ArrayList<>();
  }

  @Override
  public ManLightMethod withNavigationElement( PsiElement navigationElement )
  {
    setNavigationElement( navigationElement );
    return this;
  }

  @Override
  public ManLightMethodImpl withMethodReturnType( PsiType type )
  {
    _returnType = type;
    return this;
  }

  @Override
  public ManLightMethodImpl withParameter( PsiParameter delegate, PsiType theType )
  {
    LightParameterWrapper lp = new LightParameterWrapper( delegate, EmptySubstitutor.getInstance() ) {
      @NotNull
      @Override
      public PsiType getType()
      {
        return theType;
      }
    };
    _parameters.add( lp );
    return this;
  }

  @Override
  public PsiType getReturnType()
  {
    return mySubstitutor.substitute(_returnType != null ? _returnType : myMethod.getReturnType());
  }

  @NotNull
  @Override
  public PsiParameterList getParameterList()
  {
    return _parameters.isEmpty() ? super.getParameterList() :
      new LightParameterListWrapper( myMethod.getParameterList(), mySubstitutor ) {
        @NotNull
        @Override
        public PsiParameter[] getParameters()
        {
          return _parameters.toArray( new PsiParameter[0] );
        }
      };
  }

  public PsiElement getParent()
  {
    PsiElement result = super.getParent();
    result = null != result ? result : getContainingClass();
    return result;
  }

  public PsiFile getContainingFile()
  {
    PsiClass containingClass = getContainingClass();
    return containingClass != null ? containingClass.getContainingFile() : null;
  }

  public PsiElement copy()
  {
    return new ManLightMethodImpl( myManager, (PsiMethod)_method.copy(), getContainingClass() );
  }

  public ASTNode getNode()
  {
    return _method.getNode();
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

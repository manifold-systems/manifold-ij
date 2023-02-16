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
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.light.*;
import com.intellij.util.IncorrectOperationException;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import manifold.ij.core.ManModule;
import org.jetbrains.annotations.NotNull;

/**
 *
 */
public class ManLightMethodBuilderImpl extends LightMethodBuilder implements ManLightMethodBuilder
{
  private final ManModule _module;
  private final Set<ManModule> _modules;
  private LightIdentifier _nameIdentifier;
  private ASTNode _astNode;

  public ManLightMethodBuilderImpl( ManModule manModule, PsiManager manager, String name )
  {
    this( manModule, manager, name, null );
  }
  public ManLightMethodBuilderImpl( ManModule manModule, PsiManager manager, String name, PsiModifierList modifierList )
  {
    super( manager, JavaLanguage.INSTANCE, name,
      new LightParameterListBuilder( manager, JavaLanguage.INSTANCE ),
      modifierList == null ? new ManLightModifierListImpl( manager, JavaLanguage.INSTANCE ) : modifierList,
      new LightReferenceListBuilder( manager, JavaLanguage.INSTANCE, PsiReferenceList.Role.THROWS_LIST ) {
        @Override
        public TextRange getTextRange()
        {
          // IJ 2021.1 complains if range is null
          return TextRange.EMPTY_RANGE;
        }
      },
      new LightTypeParameterListBuilder( manager, JavaLanguage.INSTANCE ) );
    _module = manModule;
    _modules = new LinkedHashSet<>();
    _modules.add( manModule );
    _nameIdentifier = new LightIdentifier( manager, name );
  }

  public ManModule getModule()
  {
    return _module;
  }

  /**
   * Extension classes may be referenced from multiple modules, but a class may be augmented with only one version of
   * a given extension method, therefore a method may belong to more than one module.
   */
  public Set<ManModule> getModules()
  {
    return _modules;
  }
  public ManLightMethodBuilder withAdditionalModule( ManModule module )
  {
    _modules.add( module );
    return this;
  }

  @Override
  public ManLightMethodBuilder withNavigationElement( PsiElement navigationElement )
  {
    setNavigationElement( navigationElement );
    return this;
  }

  @Override
  public ManLightMethodBuilder withModifier( @PsiModifier.ModifierConstant String modifier )
  {
    addModifier( modifier );
    return this;
  }

  @Override
  public ManLightMethodBuilder withMethodReturnType( PsiType returnType )
  {
    setMethodReturnType( returnType );
    return this;
  }

  @Override
  public ManLightMethodBuilder withParameter( String name, PsiType type )
  {
    addParameter( new ManLightParameterImpl( name, type, this, JavaLanguage.INSTANCE ) );
    return this;
  }

  @Override
  public ManLightMethodBuilder withException( PsiClassType type )
  {
    addException( type );
    return this;
  }

  @Override
  public ManLightMethodBuilder withException( String fqName )
  {
    addException( fqName );
    return this;
  }

  @Override
  public ManLightMethodBuilder withContainingClass( PsiClass containingClass )
  {
    setContainingClass( containingClass );
    return this;
  }

//  @Override
//  public ManLightMethodBuilder withTypeParameter( PsiTypeParameter typeParameter )
//  {
//    addTypeParameter( typeParameter );
//    return this;
//  }

  @Override
  public ManLightMethodBuilder withTypeParameterDirect( PsiTypeParameter typeParameter )
  {
    LightTypeParameterListBuilder typeParameterList = (LightTypeParameterListBuilder)getTypeParameterList();
    Objects.requireNonNull( typeParameterList ).addParameter( typeParameter );
    return this;
  }

  @Override
  public PsiIdentifier getNameIdentifier()
  {
    return _nameIdentifier;
  }

  @Override
  public PsiElement getParent()
  {
    PsiElement result = super.getParent();
    result = null != result ? result : getContainingClass();
    return result;
  }

  @Override
  public PsiFile getContainingFile()
  {
    PsiClass containingClass = getContainingClass();
    return containingClass != null ? containingClass.getContainingFile() : null;
  }

  @Override
  public String getText()
  {
    ASTNode node = getNode();
    if( null != node )
    {
      return node.getText();
    }
    return "";
  }

  @Override
  public ASTNode getNode()
  {
    if( null == _astNode )
    {
      _astNode = rebuildMethodFromString().getNode();
    }
    return _astNode;
  }

  private PsiMethod rebuildMethodFromString()
  {
    final StringBuilder builder = new StringBuilder();
    builder.append( getAllModifierProperties( (LightModifierList)getModifierList() ) );
    if( getTypeParameterList().getTypeParameters().length > 0 )
    {
      builder.append( '<' );
      for( PsiTypeParameter p: getTypeParameterList().getTypeParameters() )
      {
        builder.append( p.getText() ).append( ',' );
      }
      builder.deleteCharAt( builder.length() - 1 );
      builder.append( '>' );
    }
    PsiType returnType = getReturnType();
    if( null != returnType )
    {
      builder.append( returnType.getCanonicalText() ).append( ' ' );
    }
    builder.append( getName() );
    builder.append( '(' );
    if( getParameterList().getParametersCount() > 0 )
    {
      for( PsiParameter parameter: getParameterList().getParameters() )
      {
        builder.append( parameter.getType().getCanonicalText() ).append( ' ' ).append( parameter.getName() ).append( ',' );
      }
      builder.deleteCharAt( builder.length() - 1 );
    }
    builder.append( ')' );
    builder.append( '{' ).append( "  " ).append( '}' );

    PsiElementFactory elementFactory = JavaPsiFacade.getInstance( getManager().getProject() ).getElementFactory();
    return elementFactory.createMethodFromText( builder.toString(), getContainingClass() );
  }

  public String getAllModifierProperties( LightModifierList modifierList )
  {
    final StringBuilder builder = new StringBuilder();
    for( String modifier: modifierList.getModifiers() )
    {
      if( !PsiModifier.PACKAGE_LOCAL.equals( modifier ) )
      {
        builder.append( modifier ).append( ' ' );
      }
    }
    return builder.toString();
  }

  public PsiElement copy()
  {
    return rebuildMethodFromString();
  }

  public String toString()
  {
    return "ManifoldLightMethodBuilder: " + getName();
  }

  @Override
  public PsiElement setName( @NotNull String name ) throws IncorrectOperationException
  {
    return _nameIdentifier = new LightIdentifier( getManager(), name );
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

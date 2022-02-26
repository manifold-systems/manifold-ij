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

import com.intellij.lang.Language;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.util.IncorrectOperationException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 */
public class ManLightModifierListImpl extends LightModifierList
{
  private static final Set<String> ALL_MODIFIERS = new HashSet<>( Arrays.asList( PsiModifier.MODIFIERS ) );

  private final Map<String, PsiAnnotation> _annotations;

  public ManLightModifierListImpl( PsiManager manager, final Language language, String... modifiers )
  {
    super( manager, language, modifiers );
    _annotations = new HashMap<>();
  }

  public void setModifierProperty( @PsiModifier.ModifierConstant String name, boolean value ) throws IncorrectOperationException
  {
    if( value )
    {
      addModifier( name );
    }
    else
    {
      if( hasModifierProperty( name ) )
      {
        removeModifier( name );
      }
    }
  }

  private void removeModifier( @PsiModifier.ModifierConstant String name )
  {
    final Collection<String> myModifiers = collectAllModifiers();
    myModifiers.remove( name );

    clearModifiers();

    for( String modifier : myModifiers )
    {
      addModifier( modifier );
    }
  }

  private Collection<String> collectAllModifiers()
  {
    Collection<String> result = new HashSet<>();
    for( @PsiModifier.ModifierConstant String modifier : ALL_MODIFIERS )
    {
      if( hasModifierProperty( modifier ) )
      {
        result.add( modifier );
      }
    }
    return result;
  }

  public void checkSetModifierProperty( @PsiModifier.ModifierConstant String name, boolean value ) throws IncorrectOperationException
  {
    throw new IncorrectOperationException();
  }

  @Override
  public PsiAnnotation addAnnotation( String qualifiedName )
  {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance( getProject() ).getElementFactory();
    final PsiAnnotation psiAnnotation = elementFactory.createAnnotationFromText( '@' + qualifiedName, null );
    _annotations.put( qualifiedName, psiAnnotation );
    return psiAnnotation;
  }

  public PsiAnnotation addRawAnnotation( String fqn, String rawAnnoWithArgs )
  {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance( getProject() ).getElementFactory();
    final PsiAnnotation psiAnnotation = elementFactory.createAnnotationFromText( rawAnnoWithArgs, null );
    _annotations.put( fqn, psiAnnotation );
    return psiAnnotation;
  }

  @Override
  public PsiAnnotation findAnnotation( String qualifiedName )
  {
    return _annotations.get( qualifiedName );
  }

  @Override
  public PsiAnnotation[] getAnnotations()
  {
    PsiAnnotation[] result = PsiAnnotation.EMPTY_ARRAY;
    if( !_annotations.isEmpty() )
    {
      Collection<PsiAnnotation> annotations = _annotations.values();
      result = annotations.toArray( new PsiAnnotation[annotations.size()] );
    }
    return result;
  }

  public String toString()
  {
    return "ManifoldLightModifierListImpl";
  }
}

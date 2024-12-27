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

package manifold.ij.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import manifold.api.gen.AbstractSrcClass;
import manifold.api.gen.AbstractSrcMethod;
import manifold.ext.rt.api.This;
import manifold.ext.rt.api.ThisClass;
import manifold.ij.core.ManModule;
import manifold.ij.psi.ManExtensionMethodBuilder;
import manifold.ij.psi.ManLightClassBuilder;
import manifold.ij.psi.ManLightMethodBuilder;
import manifold.ij.psi.ManPsiElementFactory;

import java.util.*;

public class ManPsiGenerationUtil
{
  public static PsiClass makePsiClass( AbstractSrcClass<?> srcClass, PsiElement psiElem )
  {
    StringBuilder sb = new StringBuilder();
    srcClass.render( sb, 0 );
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance( psiElem.getProject() ).getElementFactory();
    try
    {
      return elementFactory.createClassFromText( sb.toString(), psiElem );
    }
    catch( IncorrectOperationException ioe )
    {
      // the text of the class does not conform to class grammar, probably being edited in an IJ editor,
      // ignore these since the editor provides error information
      return null;
    }
  }

  public static PsiMethod makePsiMethod( AbstractSrcMethod<?> method, PsiElement psiClass )
  {
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance( psiClass.getProject() ).getElementFactory();
    StringBuilder sb = new StringBuilder();
    method.render( sb, 0 );
    try
    {
      return elementFactory.createMethodFromText( sb.toString(), psiClass );
    }
    catch( IncorrectOperationException ioe )
    {
      // the text of the method does not conform to method grammar, probably being edited in an IJ editor,
      // ignore these since the editor provides error information
      return null;
    }
  }

  public static PsiMethod plantMethodInPsiClass( ManModule manModule, PsiMethod refMethod, PsiClass psiClass, PsiMethod navMethod  )
  {
    return plantMethodInPsiClass( manModule, refMethod, psiClass, navMethod, false );
  }
  public static PsiMethod plantMethodInPsiClass( ManModule manModule, PsiMethod refMethod, PsiClass psiClass, PsiMethod navMethod, boolean isConstructor  )
  {
    if( null != refMethod )
    {
      ManPsiElementFactory manPsiElemFactory = ManPsiElementFactory.instance();
      String methodName = refMethod.getName();
      ManExtensionMethodBuilder method = manPsiElemFactory.createExtensionMethodMethod( manModule, psiClass.getManager(), methodName, navMethod )
        .withMethodReturnType( refMethod.getReturnType() )
        .withContainingClass( psiClass );

      method.setConstructor( refMethod.isConstructor() || isConstructor );
      
// do not add navigation element because PsiExtensionMethod (implemented by ManExtensionMethodBuilder) handles that separately with getTargetMethod(), additionally MethodCallUtils#getParameterForArgument() would fail
//      if( navMethod != null )
//      {
//        method.withNavigationElement( navMethod.getNavigationElement() );
//      }

      copyAnnotations( refMethod, method );

      copyModifiers( refMethod, method );

      int i = 0;
      for( PsiTypeParameter tv : refMethod.getTypeParameters() )
      {
//        LightTypeParameterBuilder lightTv = new LightTypeParameterBuilder( tv.getName(), method, i++ );
//        method.withTypeParameterDirect( lightTv );
        method.withTypeParameterDirect( tv );
      }

      PsiParameter[] parameters = refMethod.getParameterList().getParameters();
      for( PsiParameter psiParameter : parameters )
      {
        method.withParameter( psiParameter.getName(), psiParameter.getType() );

//        method.withParameter( psiParameter.getName(), mapToLightTypeParms(
//          manModule.getIjProject(), refMethod.getTypeParameters(), method.getTypeParameters(), psiParameter.getType() ) );
      }

      for( PsiClassType psiClassType : refMethod.getThrowsList().getReferencedTypes() )
      {
        method.withException( psiClassType );
      }

      if( isConstructor )
      {
        method.withBody( refMethod.getBody() );
      }

      return method;
    }
    return null;
  }

  private static PsiType mapToLightTypeParms( Project project, PsiTypeParameter[] modelTypeParameters, PsiTypeParameter[] lightTypeParams, PsiType type )
  {
    List<PsiTypeParameter> fromTypeParams = new ArrayList<>();
    fromTypeParams.addAll( Arrays.asList( modelTypeParameters ) );
    PsiSubstitutor substitutor = JavaPsiFacade.getElementFactory( project )
      .createSubstitutor( createSubstitutorMap( fromTypeParams.toArray( new PsiTypeParameter[0] ), lightTypeParams ) );
    PsiType substitutedType = substitutor.substitute( type );
    return substitutedType;
  }

  private static Map<PsiTypeParameter, PsiType> createSubstitutorMap( PsiTypeParameter[] from, PsiTypeParameter[] to )
  {
    Map<PsiTypeParameter, PsiType> map = new HashMap<>();
    for( PsiTypeParameter tpFrom: from )
    {
      Arrays.stream( to )
        .filter( toTp -> Objects.equals( toTp.getName(), tpFrom.getName() ) )
        .forEach( toTp -> map.put( tpFrom, PsiTypesUtil.getClassType( toTp ) ) );
    }
    return map;
  }

  public static PsiClass plantInnerClassInPsiClass( ManModule manModule, PsiClass innerClass, PsiClass psiClass, PsiMethod navMethod  )
  {
    if( innerClass != null )
    {
      String innerClassName = innerClass.getName();
      ManLightClassBuilder lightClass = new ManLightClassBuilder( psiClass, innerClassName, false );
      lightClass.setContainingClass( psiClass );

      if( navMethod != null )
      {
        lightClass.setNavigationElement( navMethod.getNavigationElement() );
      }

      copyAnnotations( innerClass, lightClass );

      copyModifiers( innerClass, lightClass );

      for( PsiTypeParameter tv : innerClass.getTypeParameters() )
      {
        lightClass.withTypeParameterDirect( tv );
      }

      PsiMethod constructor = innerClass.getConstructors()[0];
      PsiMethod lightClassCtor = plantMethodInPsiClass( manModule, constructor, lightClass, navMethod );
      lightClass.addMethod( lightClassCtor );
      return lightClass;
    }
    return null;
  }

  private static void copyModifiers( PsiMethod refMethod, ManLightMethodBuilder method )
  {
    addModifier( refMethod, method, PsiModifier.PUBLIC );
    addModifier( refMethod, method, PsiModifier.STATIC );
    addModifier( refMethod, method, PsiModifier.PACKAGE_LOCAL );
    addModifier( refMethod, method, PsiModifier.PROTECTED );
  }

  private static void copyModifiers( PsiClass psiClass, ManLightClassBuilder lightClass )
  {
    addModifier( psiClass, lightClass, PsiModifier.PUBLIC );
    addModifier( psiClass, lightClass, PsiModifier.STATIC );
    addModifier( psiClass, lightClass, PsiModifier.PACKAGE_LOCAL );
    addModifier( psiClass, lightClass, PsiModifier.PROTECTED );
  }

  private static void copyAnnotations( PsiModifierListOwner refMethod, PsiModifierListOwner method )
  {
    for( PsiAnnotation anno : refMethod.getModifierList().getAnnotations() )
    {
      String qualifiedName = anno.getQualifiedName();
      if( qualifiedName == null )
      {
        continue;
      }
      PsiAnnotation psiAnnotation = method.getModifierList().addAnnotation( qualifiedName );
      for( PsiNameValuePair pair : anno.getParameterList().getAttributes() )
      {
        psiAnnotation.setDeclaredAttributeValue( pair.getName(), pair.getValue() );
      }
    }
  }

  public static PsiMethod findExtensionMethodNavigationElement( PsiClass extClass, PsiMethod plantedMethod )
  {
    PsiMethod[] found = extClass.findMethodsByName( plantedMethod.getName(), false );
    outer:
    for( PsiMethod m : found )
    {
      PsiParameter[] extParams = m.getParameterList().getParameters();
      PsiParameter[] plantedParams = plantedMethod.getParameterList().getParameters();
      int offset = getParamOffset( extParams );
      if( extParams.length - offset == plantedParams.length )
      {
        for( int i = offset; i < extParams.length; i++ )
        {
          PsiParameter extParam = extParams[i];
          PsiParameter plantedParam = plantedParams[i - offset];
          PsiType extErased = TypeConversionUtil.erasure( extParam.getType() );
          PsiType plantedErased = TypeConversionUtil.erasure( plantedParam.getType() );
          if( !extErased.toString().equals( plantedErased.toString() ) )
          {
            continue outer;
          }
        }
        return m;
      }
    }
    return null;
  }

  private static int getParamOffset( PsiParameter[] params )
  {
    if( params.isEmpty() )
    {
      return 0;
    }
    boolean skipFirstParam = Arrays.stream( params[0].getAnnotations() )
      .anyMatch( anno ->
        anno.getQualifiedName() != null &&
          (anno.getQualifiedName().equals( This.class.getTypeName() ) ||
            anno.getQualifiedName().equals( ThisClass.class.getTypeName() )) );
    return skipFirstParam ? 1 : 0;
  }

  private static void addModifier( PsiMethod psiMethod, ManLightMethodBuilder method, String modifier )
  {
    if( psiMethod.hasModifierProperty( modifier ) )
    {
      method.withModifier( modifier );
    }
  }

  private static void addModifier( PsiClass psiClass, ManLightClassBuilder innerClass, String modifier )
  {
    if( psiClass.hasModifierProperty( modifier ) )
    {
      innerClass.getModifierList().addModifier( modifier );
    }
  }

}

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

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.lang.jvm.annotation.JvmAnnotationArrayValue;
import com.intellij.lang.jvm.annotation.JvmAnnotationAttribute;
import com.intellij.lang.jvm.annotation.JvmAnnotationAttributeValue;
import com.intellij.lang.jvm.annotation.JvmAnnotationClassValue;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import manifold.ext.typealias.rt.api.TypeAlias;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.psi.ManLightMethodBuilder;
import manifold.ij.psi.ManPsiElementFactory;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class TypeAliasMaker
{
  private static final ThreadLocal<Set<String>> _reenter = ThreadLocal.withInitial( () -> new HashSet<>() );

  private final boolean _isAbstractClass;

  private final PsiClass _psiClass;
  private final PsiClass _newPsiClass;

  private final LinkedHashSet<PsiMember> _features;

  static void checkTypeAlias( PsiExtensibleClass psiClass, Object issueInfo )
  {
//    new TypeAliasMaker( psiClass, issueInfo ).generateOrCheck();
  }

  static void generateMembers(PsiClass psiClass, PsiClass newPsiClass, LinkedHashSet<PsiMember> features )
  {
    String qname = psiClass.getQualifiedName();
    if( qname == null )
    {
      return;
    }

    if( _reenter.get().contains( qname ) )
    {
      return;
    }
    _reenter.get().add( qname );
    try
    {
      new TypeAliasMaker( psiClass, newPsiClass, features ).generateOrCheck();
    }
    finally
    {
      _reenter.get().remove( qname );
    }
  }

//  private TypeAliasMaker(PsiExtensibleClass psiClass, TypeAliasExternalAnnotator.Info issueInfo )
//  {
//    this( psiClass, issueInfo, null );
//  }

  private TypeAliasMaker( PsiClass psiClass, PsiClass newPsiClass, LinkedHashSet<PsiMember> features )
  {
    this( psiClass, newPsiClass, null, features );
  }

  private TypeAliasMaker( PsiClass psiClass, PsiClass newPsiClass, Object issueInfo, LinkedHashSet<PsiMember> features )
  {
    _psiClass = psiClass;
    _newPsiClass = newPsiClass;
    _isAbstractClass = psiClass.hasModifierProperty( PsiModifier.ABSTRACT );
    _features = features;
  }

  private void generateOrCheck()
  {
    if( _newPsiClass.getQualifiedName() == null )
    {
      return;
    }
    if( _features != null )
    {
      dump2(_features, _newPsiClass );
    }
  }

  private void dump2( Set<PsiMember> members, PsiClass psiClass )
  {
    dump2( members, psiClass.getAllFields() );
    dump2( members, psiClass.getAllMethods() );
    dump2( members, psiClass.getAllInnerClasses() );
  }

  private void dump2( Set<PsiMember> members, PsiMember[] psiMembers )
  {
    for( PsiMember member : psiMembers )
    {
      member = generateMemberFromSource( member );
      if( member != null)
      {
//        System.out.println(_psiClass + " adding "  + member);
        members.add(member);
      }
    }
  }

  private PsiMember generateMemberFromSource( PsiMember member )
  {
    // If add an abstract method to a non-abstract class,
    // it will cause the IDE to prompt `make to abstract`.
    if ( !_isAbstractClass && member.hasModifierProperty( PsiModifier.ABSTRACT ) )
    {
      return null;
    }

    if( !(member instanceof PsiMethod) || !((PsiMethod) member).isConstructor() )
    {
      return member;
    }

    // Only adding the self-class constructor.
    if( member.getContainingClass() != _newPsiClass )
    {
      return null;
    }

    PsiMethod refMethod = GenerateMembersUtil.substituteGenericMethod( (PsiMethod)member, PsiSubstitutor.EMPTY, _newPsiClass );
    ManPsiElementFactory manPsiElemFactory = ManPsiElementFactory.instance();
    String methodName = _psiClass.getName();
    ManModule manModule = ManProject.getModule( _newPsiClass );
    ManLightMethodBuilder method = manPsiElemFactory.createLightMethod( manModule, _newPsiClass.getManager(), methodName, refMethod.getModifierList() )
            .withNavigationElement( member )
            .withMethodReturnType( refMethod.getReturnType() )
            .withContainingClass( _newPsiClass )
            .withConstructor( true );

    for( PsiTypeParameter tv : refMethod.getTypeParameters() )
    {
      method.withTypeParameterDirect( tv );
    }

    PsiParameter[] parameters = refMethod.getParameterList().getParameters();
    for( PsiParameter psiParameter : parameters )
    {
      method.withParameter( psiParameter.getName(), psiParameter.getType() );
    }

    for( PsiClassType psiClassType : refMethod.getThrowsList().getReferencedTypes() )
    {
      method.withException( psiClassType );
    }

    return method;
  }

  public static PsiAnnotation getAnnotation(PsiClass psiClass )
  {
    if( psiClass != null && psiClass.getQualifiedName() != null )
    {
      return psiClass.getAnnotation( TypeAlias.class.getTypeName() );
    }
    return null;
  }

  public static PsiClass getAliasedType(PsiClass psiClass )
  {
    PsiAnnotation annotation = getAnnotation( psiClass );
    if( annotation == null )
    {
      return null;
    }
    try
    {
      for( JvmAnnotationAttribute attribute : annotation.getAttributes() )
      {
        String name = attribute.getAttributeName();
        JvmAnnotationAttributeValue value = attribute.getAttributeValue();
        if( !name.equals( "value" ) )
        {
          return null;
        }
        return getAttributeClassValue( value );
      }
      return psiClass.getSuperClass();
    }
    catch (Exception ex)
    {
      return null;
    }
  }

  private static PsiClass getAttributeClassValue( JvmAnnotationAttributeValue value )
  {
    if( value instanceof JvmAnnotationArrayValue )
    {
      for( JvmAnnotationAttributeValue cls : ((JvmAnnotationArrayValue)value).getValues() )
      {
        return getAttributeClassValue( cls );
      }
    }
    if( value instanceof  JvmAnnotationClassValue )
    {
      return (PsiClass)((JvmAnnotationClassValue)value).getClazz();
    }
    return null;
  }
}

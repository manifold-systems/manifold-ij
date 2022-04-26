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

import com.intellij.lang.jvm.annotation.JvmAnnotationConstantValue;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.util.ArrayUtil;
import java.util.Arrays;
import manifold.ext.rt.api.Self;
import org.jetbrains.annotations.NotNull;

public class SelfTypeUtil
{
  private static final SelfTypeUtil INSTANCE = new SelfTypeUtil();

  public static SelfTypeUtil instance()
  {
    return INSTANCE;
  }

  PsiType handleSelfType2( PsiType type, PsiType exprType, PsiReferenceExpression methodExpression )
  {
    if( !hasSelfAnnotation( type ) )
    {
      return exprType;
    }

    PsiType qualifierType;
    PsiExpression qualifier = (PsiExpression)methodExpression.getQualifier();
    if( qualifier == null )
    {
      PsiClass thisClass = RefactoringChangeUtil.getThisClass( methodExpression );
      String qualifiedName = thisClass.getQualifiedName();
      if( qualifiedName == null )
      {
        return exprType;
      }
      PsiElementFactory elementFactory = JavaPsiFacade.getInstance( methodExpression.getProject() )
        .getElementFactory();
      if( thisClass.getTypeParameters().length == 0 )
      {
        qualifierType = elementFactory.createType( thisClass );
      }
      else
      {
        // If the self type is generic, it must be parameterized with its type vars
        qualifierType = elementFactory.createType( thisClass,
          Arrays.stream( thisClass.getTypeParameters() ).map( elementFactory::createType ).toArray( PsiType[]::new ) );
      }
    }
    else
    {
      qualifierType = qualifier.getType();
      if( qualifierType == null && qualifier instanceof PsiReferenceExpressionImpl )
      {
        PsiElement element = ((PsiReferenceExpressionImpl)qualifier).resolve();
        if( element instanceof PsiClass )
        {
          qualifierType = PsiTypesUtil.getClassType( (PsiClass)element );
        }
      }
    }
    return type.accept( new SelfReplacer( qualifierType ) );
  }

  class SelfReplacer extends PsiTypeMapper
  {
    private final PsiType _self;

    SelfReplacer( PsiType self )
    {
      _self = self;
    }

    @Override
    public PsiType visitArrayType( PsiArrayType type )
    {
      if( hasSelfAnnotationDirectly( type ) )
      {
        PsiType componentType = type.getComponentType();
        return new PsiArrayType( _self.annotate( getMergedProviderMinusSelf( componentType, _self ) ),
          type.getAnnotationProvider() );
      }
      return super.visitArrayType( type );
    }

    @Override
    public PsiType visitClassType( PsiClassType classType )
    {
      if( hasSelfAnnotationDirectly( classType ) )
      {
        PsiType newType = makeSelfTypeDirectly( classType );
        if( newType instanceof PsiArrayType && selfIsComponentType( classType ))
        {
//          while( newType instanceof PsiArrayType )
//          {
            newType = ((PsiArrayType)newType).getComponentType();
//          }
        }
        return newType;
      }

      if( hasSelfAnnotation( classType ) )
      {
        PsiClassType.ClassResolveResult resolve = classType.resolveGenerics();
        PsiClass psiClass = resolve.getElement();
        if( psiClass == null )
        {
          return classType;
        }

        PsiSubstitutor substitutor = resolve.getSubstitutor();
        int i = 0;
        for( PsiType typeParam: classType.getParameters() )
        {
          PsiType newType = typeParam.accept( this );
          if( newType instanceof PsiArrayType && selfIsComponentType( classType ) )
          {
//            while( newType instanceof PsiArrayType )
//            {
              newType = ((PsiArrayType)newType).getComponentType();
//            }
          }
          PsiTypeParameter typeVar = psiClass.getTypeParameters()[i];
          substitutor = substitutor.put( typeVar, newType );
          i++;
        }
        classType = new PsiImmediateClassType( resolve.getElement(), substitutor );
      }

      return classType;
    }

    private PsiType makeSelfTypeDirectly( PsiClassType classType )
    {
      return _self.annotate( getMergedProviderMinusSelf( classType, _self ) );
// no need to derive type from @Self declared type, since only the *exact* enclosing class is allowed e.g., @Self Foo<T>  *not* @Self Foo<String>
//      if( classType.getParameterCount() > 0 )
//      {
//        replacedType = deriveParameterizedTypeFromAncestry( (PsiClassType)_self, classType );
//      }
//      return replacedType;
    }

    private PsiClassType deriveParameterizedTypeFromAncestry( PsiClassType subType, PsiClassType superType )
    {
      if( subType.rawType().equals( superType.rawType() ) )
      {
        return superType;
      }

      PsiType[] superTypes = subType.getSuperTypes();
      for( PsiType st: superTypes )
      {
        PsiClassType superT = deriveParameterizedTypeFromAncestry( (PsiClassType)st, superType );
        if( superT != null )
        {
          return deriveParameterizedTypeFromSuper( subType, superT );
        }
      }
      return null;
    }

    private PsiClassType deriveParameterizedTypeFromSuper( PsiClassType subType, PsiClassType superType )
    {
      PsiClass superPsiClass = superType.resolve();
      PsiClass subPsiClass = subType.resolve();
      if( subPsiClass == null || superPsiClass == null )
      {
        return subType;
      }

      PsiSubstitutor substitutor = EmptySubstitutor.getInstance();

      for( PsiClassType st: subPsiClass.getSuperTypes() )
      {
        if( st.getParameterCount() == 0 )
        {
          continue;
        }

        PsiClass stClass = st.resolve();
        if( stClass != null )
        {
          String stFqn = stClass.getQualifiedName();
          if( stFqn != null && stFqn.equals( superPsiClass.getQualifiedName() ) )
          {
            PsiType[] parameters = st.getParameters();
            for( int i = 0; i < parameters.length; i++ )
            {
              PsiType param = parameters[i];
              PsiTypeParameter[] typeParameters = subPsiClass.getTypeParameters();
              for( int j = 0; j < typeParameters.length; j++ )
              {
                PsiTypeParameter subParam = typeParameters[j];
                String subName = subParam.getName();
                if( subName != null && subName.equals( ((PsiClassType)param).getName() ) )
                {
                  PsiClass actualSubParam = ((PsiClassType)subType.getParameters()[j]).resolve();
                  if( actualSubParam instanceof PsiTypeParameter )
                  {
                    substitutor = substitutor.put( (PsiTypeParameter)actualSubParam, superType.getParameters()[i] );
                  }
                }
              }
            }
          }
        }
      }
      return (PsiClassType)substitutor.substitute( subType );
    }

    @NotNull
    private TypeAnnotationProvider getMergedProviderMinusSelf( @NotNull PsiType type1, @NotNull PsiType type2 )
    {
      TypeAnnotationProvider result;
      if( type1.getAnnotationProvider() == TypeAnnotationProvider.EMPTY && !(type1 instanceof PsiClassReferenceType) )
      {
        result = type2.getAnnotationProvider();
      }
      else if( type2.getAnnotationProvider() == TypeAnnotationProvider.EMPTY && !(type2 instanceof PsiClassReferenceType) )
      {
        result = type1.getAnnotationProvider();
      }
      else
      {
        result = () -> ArrayUtil.mergeArrays( type1.getAnnotations(), type2.getAnnotations() );
      }
      return () -> Arrays.stream( result.getAnnotations() )
               .filter( e -> !Self.class.getTypeName().equals( e.getQualifiedName() ) )
               .toArray( PsiAnnotation[]::new );
    }
  }

  private boolean hasSelfAnnotationDirectly( PsiType type )
  {
    if( type == null )
    {
      return false;
    }

    for( PsiAnnotation anno: type.getAnnotations() )
    {
      if( Self.class.getTypeName().equals( anno.getQualifiedName() ) )
      {
        return true;
      }
    }
    return false;
  }

  private boolean selfIsComponentTypeDirectly( PsiType type )
  {
    if( type == null )
    {
      return false;
    }

    for( PsiAnnotation anno: type.getAnnotations() )
    {
      if( Self.class.getTypeName().equals( anno.getQualifiedName() ) )
      {
        if( !anno.getAttributes().isEmpty() )
        {
          JvmAnnotationConstantValue value = (JvmAnnotationConstantValue) anno.getAttributes().get( 0 ).getAttributeValue();
          return value != null && (boolean)value.getConstantValue();
        }
        break;
      }
    }
    return false;
  }

  boolean hasSelfAnnotation( PsiType type )
  {
    if( type instanceof PsiPrimitiveType )
    {
      return false;
    }

    if( hasSelfAnnotationDirectly( type ) )
    {
      return true;
    }

    if( type instanceof PsiClassType )
    {
      for( PsiType typeArg: ((PsiClassType)type).getParameters() )
      {
        if( hasSelfAnnotation( typeArg ) )
        {
          return true;
        }
      }
    }

    if( type instanceof PsiArrayType )
    {
      if( hasSelfAnnotation( ((PsiArrayType)type).getComponentType() ) )
      {
        return true;
      }
    }

    if( type instanceof PsiWildcardType )
    {
      PsiType bound = ((PsiWildcardType)type).getBound();
      if( bound != null && hasSelfAnnotation( bound ) )
      {
        return true;
      }
    }

    if( type instanceof PsiCapturedWildcardType )
    {
      return hasSelfAnnotation( ((PsiCapturedWildcardType)type).getWildcard() );
    }

    return false;
  }

  boolean selfIsComponentType( PsiType type )
  {
    if( type instanceof PsiPrimitiveType )
    {
      return false;
    }

    if( selfIsComponentTypeDirectly( type ) )
    {
      return true;
    }

    if( type instanceof PsiClassType )
    {
      for( PsiType typeArg: ((PsiClassType)type).getParameters() )
      {
        if( selfIsComponentType( typeArg ) )
        {
          return true;
        }
      }
    }
    else if( type instanceof PsiArrayType )
    {
      if( selfIsComponentType( ((PsiArrayType)type).getComponentType() ) )
      {
        return true;
      }
    }
    else if( type instanceof PsiWildcardType )
    {
      PsiType bound = ((PsiWildcardType)type).getBound();
      if( bound != null && selfIsComponentType( bound ) )
      {
        return true;
      }
    }
    else if( type instanceof PsiCapturedWildcardType )
    {
      return selfIsComponentType( ((PsiCapturedWildcardType)type).getWildcard() );
    }

    return false;
  }

}

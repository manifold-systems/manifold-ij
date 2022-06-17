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

package manifold.ij.core;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import manifold.rt.api.util.Stack;
import org.jetbrains.annotations.NotNull;

public class RecursiveTypeVarEraser extends PsiTypeMapper
{
  private final Stack<PsiSubstitutor> _substitutors;
  private final PsiElement _context;

  public static PsiType eraseTypeVars( PsiType type, PsiElement context )
  {
    return new RecursiveTypeVarEraser( context ).mapType( type );
  }

  private RecursiveTypeVarEraser( PsiElement context )
  {
    _context = context;
    _substitutors = new Stack<>();
  }

  @Override
  public PsiType visitClassType( PsiClassType t )
  {
    final PsiClassType.ClassResolveResult resolveResult = t.resolveGenerics();
    final PsiClass psiClass = resolveResult.getElement();
    if( psiClass == null )
    {
      return t;
    }

    if( psiClass instanceof PsiTypeParameter )
    {
      PsiTypeParameter param = (PsiTypeParameter)psiClass;
      PsiClassType[] types = param.getExtendsList().getReferencedTypes();
      PsiType type = types.isEmpty()
        ? PsiType.getJavaLangObject( _context.getManager(), GlobalSearchScope.allScope( _context.getProject() ) )
        : eraseBound( t, types[0] );
      if( !_substitutors.isEmpty() )
      {
        PsiSubstitutor substitutor = _substitutors.pop();
        substitutor = substitutor.put( param, type );
        _substitutors.push( substitutor );
      }
      return type;
    }

    boolean erase = false;
    PsiType erasure = TypeConversionUtil.erasure( t );
    if( !t.equals( erasure ) )
    {
      erase = true;
    }

    PsiSubstitutor substitutor = EmptySubstitutor.getInstance();
    _substitutors.push( substitutor );

    PsiType @NotNull [] parameters = t.getParameters();
    for( int i = 0; i < parameters.length; i++ )
    {
      PsiType arg = parameters[i];
      PsiTypeParameter typeVar = null;
      if( arg instanceof PsiClassType )
      {
        final PsiClassType.ClassResolveResult paramResolveResult = ((PsiClassType)arg).resolveGenerics();
        final PsiClass paramPsiClass = paramResolveResult.getElement();
        if( paramPsiClass instanceof PsiTypeParameter )
        {
          typeVar = (PsiTypeParameter)paramPsiClass;
        }
      }
      PsiType param = mapType( arg );
      if( typeVar != null && param != null )
      {
        // TypeVar args must be replaced with Wildcards:
        //
        // public class Foo<T extends CharSequence> {
        // ...
        //   Foo<T> foo = blah;
        //   auto tuple = (foo, bar);
        // }
        //
        // tuple type for foo must be Foo<? extends CharSequence>, not Foo<CharSequence>
        //
        param = PsiWildcardType.createExtends( _context.getManager(), param );
        substitutor = _substitutors.pop();
        substitutor = substitutor.put( typeVar, param );
        _substitutors.push( substitutor );
      }

      if( param != null && !param.equals( arg ) )
      {
        erase = true;
      }
    }

    substitutor = _substitutors.pop();
    if( erase )
    {
      return substitutor.substitute( t );
    }

    return t;
  }

  @Override
  public PsiType visitArrayType( PsiArrayType t )
  {
    PsiType compType = mapType( t.getComponentType() );
    if( compType.equals( t.getComponentType() ) )
    {
      return t;
    }
    return new PsiArrayType( compType );
  }

  @Override
  public PsiType visitCapturedWildcardType( @NotNull final PsiCapturedWildcardType type )
  {
    PsiType bound = type.getWildcard();
    bound = eraseBound( type, bound );
    if( bound.equals( type.getWildcard() ) )
    {
      return type;
    }
    return type.getWildcard().isExtends()
      ? PsiWildcardType.createExtends( _context.getManager(), bound )
      : PsiWildcardType.createSuper( _context.getManager(), bound );
  }

  @Override
  public PsiType visitWildcardType( PsiWildcardType t )
  {
    if( t.getBound() == null )
    {
      return t;
    }
    PsiType bound = eraseBound( t, t.getBound() );
    if( bound.equals( t.getBound() ) )
    {
      return t;
    }
    return t.isExtends()
      ? PsiWildcardType.createExtends( _context.getManager(), bound )
      : PsiWildcardType.createSuper( _context.getManager(), bound );
  }

  @Override
  public PsiType visitType( PsiType t )
  {
    return t;
  }

  private PsiType eraseBound( PsiType t, PsiType bound )
  {
    if( bound == null || bound instanceof Bottom )
    {
      return bound;
    }

    PsiType erasedBound;
    if( t instanceof PsiClassType && isRecursiveType( bound, (PsiClassType)t ) )
    {
      erasedBound = mapType( TypeConversionUtil.erasure( bound ) );
    }
    else
    {
      erasedBound = mapType( bound );
    }
    return erasedBound;
  }

  private boolean isRecursiveType( PsiType type, PsiClassType test )
  {
    return type.accept( new RecursiveTypeDeterminer( test ) );
  }

  private static class RecursiveTypeDeterminer extends PsiTypeVisitor<Boolean>
  {
    private final PsiClass _test;

    RecursiveTypeDeterminer( PsiClassType test )
    {
      _test = test.resolveGenerics().getElement();
    }

    @Override
    public Boolean visitType( @NotNull final PsiType type )
    {
      return false;
    }

    @Override
    public Boolean visitArrayType( @NotNull final PsiArrayType arrayType )
    {
      return arrayType.getComponentType().accept( this );
    }

    @Override
    public Boolean visitClassType( @NotNull final PsiClassType classType )
    {
      PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      final PsiClass psiClass = resolveResult.getElement();
      if( psiClass instanceof PsiTypeParameter )
      {
        if( psiClass.equals( _test ) )
        {
          return true;
        }
      }

      if( psiClass != null )
      {
        PsiSubstitutor substitutor = resolveResult.getSubstitutor();
        for( final PsiTypeParameter parameter : PsiUtil.typeParametersIterable( psiClass ) )
        {
          PsiType psiType = substitutor.substitute( parameter );
          if( psiType != null )
          {
            if( psiType.accept( this ) )
            {
              return true;
            }
          }
        }
      }
      return false;
    }

    @Override
    public Boolean visitWildcardType( @NotNull final PsiWildcardType wildcardType )
    {
      final PsiType bound = wildcardType.getBound();
      if( bound != null )
      {
        return bound.accept( this );
      }
      return false;
    }
  }
}

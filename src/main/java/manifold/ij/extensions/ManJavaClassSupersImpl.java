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

import com.intellij.psi.*;
import com.intellij.psi.impl.JavaClassSupersImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import manifold.ij.util.ManPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Override IJ's JavaClassSupersImpl to support structurally assignable types via @Structural
 */
public class ManJavaClassSupersImpl extends JavaClassSupers
{
  private final JavaClassSupersImpl _delegate;

  public ManJavaClassSupersImpl()
  {
    _delegate = new JavaClassSupersImpl();
  }

  @Override
  public @Nullable PsiSubstitutor getSuperClassSubstitutor( @NotNull PsiClass superClass, @NotNull PsiClass derivedClass, @NotNull GlobalSearchScope resolveScope, @NotNull PsiSubstitutor derivedSubstitutor )
  {
    PsiSubstitutor superClassSubstitutor = _delegate.getSuperClassSubstitutor( superClass, derivedClass, resolveScope, derivedSubstitutor );
    if( superClassSubstitutor == null && ManPsiUtil.isStructuralInterface( superClass ) )
    {
      // if not nominally assignable and superClass is @Structural interface, check if structurally assignable
      return isStructurallyAssignable( superClass, derivedClass ) ? PsiSubstitutor.EMPTY : null;
    }
    return superClassSubstitutor;
  }

  public static boolean isStructurallyAssignable( @NotNull PsiClass superClass, @NotNull PsiClass derivedClass )
  {
    if( !superClass.isInterface() )
    {
      return false;
    }

    // check for structural assignment, return empty substitutor to affirm, otherwise null
    outer: for( PsiMethod m: superClass.getAllMethods() )
    {
      if( m.hasModifierProperty( PsiModifier.PUBLIC ) &&
        m.hasModifierProperty( PsiModifier.ABSTRACT ) &&
        !m.hasModifierProperty( PsiModifier.STATIC ) &&
        !m.hasModifierProperty( PsiModifier.DEFAULT ) )
      {
        for( PsiMethod dm: derivedClass.getAllMethods() )
        {
          if( dm.getName().equals( m.getName() ) &&
            m.hasModifierProperty( PsiModifier.PUBLIC ) &&
            !m.hasModifierProperty( PsiModifier.STATIC ) )
          {
            if( dm.getName().equals( m.getName() ) )
            {
              if( isStructurallyAssignable( dm, m ) )
              {
                continue outer;
              }
            }
          }
        }
        // no structural match found for method
        return false;
      }
    }
    return true;
  }

  public static boolean isStructurallyAssignable( @NotNull PsiMethod from, @NotNull PsiMethod to )
  {
    final int typeParamsLength1 = from.getTypeParameters().length;
    final int typeParamsLength2 = to.getTypeParameters().length;
    if( typeParamsLength1 != typeParamsLength2 && typeParamsLength1 != 0 && typeParamsLength2 != 0 )
    {
      return false;
    }

    PsiType fromReturnType = TypeConversionUtil.erasure( from.getReturnType() );
    PsiType toReturnType = TypeConversionUtil.erasure( to.getReturnType() );
    return toReturnType != null && fromReturnType != null && toReturnType.isAssignableFrom( fromReturnType ) &&
      areErasedStructurallyAssignable( from.getSignature( PsiSubstitutor.EMPTY ), to.getSignature( PsiSubstitutor.EMPTY ) );
  }

  public static boolean areErasedStructurallyAssignable( @NotNull MethodSignature from, @NotNull MethodSignature to )
  {
    PsiType[] erased1 = from instanceof MethodSignatureBase
      ? ((MethodSignatureBase)from).getErasedParameterTypes() : MethodSignatureUtil.calcErasedParameterTypes( from );
    PsiType[] erased2 = to instanceof MethodSignatureBase
      ? ((MethodSignatureBase)to).getErasedParameterTypes() : MethodSignatureUtil.calcErasedParameterTypes( to );
    //noinspection ComparatorMethodParameterNotUsed
    return Arrays.equals( erased1, erased2, (t1, t2) -> t1.isAssignableFrom( t2 ) ? 0 : 1 );
  }

  @Override
  public void reportHierarchyInconsistency( @NotNull PsiClass superClass, @NotNull PsiClass derivedClass )
  {
    _delegate.reportHierarchyInconsistency( superClass, derivedClass );
  }
}

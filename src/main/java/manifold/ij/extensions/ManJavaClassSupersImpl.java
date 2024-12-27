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

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaClassSupersImpl;
import com.intellij.psi.impl.light.LightRecordMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import manifold.ij.core.RecursiveTypeVarEraser;
import manifold.ij.util.ManPsiUtil;
import manifold.rt.api.Null;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
    if( superClassSubstitutor == null )
    {
      if( isNullType( derivedClass) )
      {
        return PsiSubstitutor.EMPTY;
      }

      if( ManPsiUtil.isStructuralInterface( superClass ) )
      {
        // if not nominally assignable and superClass is @Structural interface, check if structurally assignable
        return isStructurallyAssignable( superClass, derivedClass )
          ? JavaPsiFacade.getElementFactory( superClass.getProject() ).createSubstitutor( createSubstitutorMap( superClass ) )
//          ? TypeConversionUtil.getSuperClassSubstitutor( superClass, (PsiClassType)RecursiveTypeVarEraser.eraseTypeVars( PsiTypesUtil.getClassType( superClass ), superClass ) )
          : null;
      }
    }
    return superClassSubstitutor;
  }

  private Map<PsiTypeParameter, PsiType> createSubstitutorMap( PsiClass superClass )
  {
    Map<PsiTypeParameter, PsiType> map = new HashMap<>();
    for( PsiTypeParameter tp: superClass.getTypeParameters() )
    {
      map.put( tp, RecursiveTypeVarEraser.eraseTypeVars( PsiTypesUtil.getClassType( tp ), superClass ) );
    }
    return map;
  }

  private static boolean isNullType( @NotNull PsiClass derivedClass )
  {
    return derivedClass.getQualifiedName() != null && derivedClass.getQualifiedName().equals( Null.class.getTypeName() );
  }

  private static final ThreadLocal<Set<Pair<PsiClass,PsiClass>>> _visited = ThreadLocal.withInitial( () -> new LinkedHashSet<>() );
  public static boolean isStructurallyAssignable( @NotNull PsiClass superClass, @NotNull PsiClass derivedClass )
  {
    Pair<PsiClass, PsiClass> pair = new Pair<>( superClass, derivedClass );
    if( _visited.get().contains( pair ) )
    {
      return false;
    }
    _visited.get().add( pair );

    try
    {
      if( !superClass.isInterface() )
      {
        return false;
      }

      // check for structural assignment, return empty substitutor to affirm, otherwise null
      outer:
      for( PsiMethod sm : superClass.getAllMethods() )
      {
        if( sm.hasModifierProperty( PsiModifier.PUBLIC ) &&
          sm.hasModifierProperty( PsiModifier.ABSTRACT ) &&
          !sm.hasModifierProperty( PsiModifier.STATIC ) &&
          !sm.hasModifierProperty( PsiModifier.DEFAULT ) )
        {
          for( PsiMethod dm : derivedClass.getAllMethods() )
          {
            if( sm.hasModifierProperty( PsiModifier.PUBLIC ) &&
              !sm.hasModifierProperty( PsiModifier.STATIC ) )
            {
              if( dm.getName().equals( sm.getName() ) )
              {
                if( isStructurallyAssignable( dm, sm ) )
                {
                  continue outer;
                }
              }
              else if( dm instanceof LightRecordMethod &&
                isGetterMatch( sm, dm.getName(), dm.getReturnType() ) )
              {
                continue outer;
              }
            }
          }
          
          for( PsiField df : derivedClass.getAllFields() )
          {
            if( df.hasModifierProperty( PsiModifier.PUBLIC ) &&
                !df.hasModifierProperty( PsiModifier.STATIC ) &&
                (isGetterMatch( sm, df.getName(), df.type ) ||
                 (!df.hasModifierProperty( PsiModifier.FINAL ) &&
                  isSetterMatch( sm, df.getName(), df.type ))) )
            {
              continue outer;
            }
          }

          // no structural match found for method
          return false;
        }
      }
      return true;
    }
    finally
    {
      _visited.get().remove( pair );
    }
  }

  static boolean isGetterMatch( PsiMethod sm, String dName, PsiType dType )
  {
    PsiType returnType = sm.getReturnType();
    if( returnType == null || returnType == PsiTypes.voidType() || sm.hasParameters() )
    {
      return false;
    }

    String smName = sm.getName();
    if( smName.length() >= 3 && smName.startsWith( "is" ) && Character.isUpperCase( smName.charAt( 2 ) ) &&
      (returnType == PsiTypes.booleanType() || returnType == PsiTypes.booleanType().getBoxedType( sm )) )
    {
      smName = smName.substring( 2 ).toLowerCase();
    }
    else if( smName.length() >= 4 && smName.startsWith( "get" ) && Character.isUpperCase( smName.charAt( 3 ) ) )
    {
      smName = smName.substring( 3 ).toLowerCase();
    }
    else
    {
      return false;
    }
    return smName.equals( dName ) &&
      RecursiveTypeVarEraser.eraseTypeVars( returnType, sm ).isAssignableFrom( RecursiveTypeVarEraser.eraseTypeVars( dType, sm ) );
  }
  static boolean isSetterMatch( PsiMethod sm, String dName, PsiType dType )
  {
    PsiType returnType = sm.getReturnType();
    if( returnType != PsiTypes.voidType() || sm.getParameterList().getParametersCount() != 1 )
    {
      return false;
    }

    String smName = sm.getName();
    if( smName.length() >= 4 && smName.startsWith( "set" ) && Character.isUpperCase( smName.charAt( 3 ) ) )
    {
      smName = smName.substring( 3 ).toLowerCase();
    }
    else
    {
      return false;
    }
    return smName.equals( dName ) &&
      RecursiveTypeVarEraser.eraseTypeVars( dType, sm ).isAssignableFrom(
        RecursiveTypeVarEraser.eraseTypeVars( sm.getParameterList().getParameters()[0].getType(), sm ) );
  }

  public static boolean isStructurallyAssignable( @NotNull PsiMethod from, @NotNull PsiMethod to )
  {
    PsiType fromReturnType = RecursiveTypeVarEraser.eraseTypeVars( from.getReturnType(), from );
    PsiType toReturnType = RecursiveTypeVarEraser.eraseTypeVars( to.getReturnType(), to );

    if( toReturnType != null && fromReturnType != null && toReturnType.isAssignableFrom( fromReturnType ))
    {
      PsiParameter[] fromParams = from.getParameterList().getParameters();
      PsiParameter[] toParams = to.getParameterList().getParameters();
      if( fromParams.length != toParams.length )
      {
        return false;
      }

      for( int i = 0; i < fromParams.length; i++ )
      {
        PsiType fromParamType = RecursiveTypeVarEraser.eraseTypeVars( fromParams[i].getType(), from );
        PsiType toParamType = RecursiveTypeVarEraser.eraseTypeVars( toParams[i].getType(), to );
        if( !fromParamType.isAssignableFrom( toParamType ) )
        {
          return false;
        }
      }
      return true;
    }
    return false;
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

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

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import manifold.ext.params.rt.params;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.core.ManPsiTupleExpression;
import manifold.ij.core.TupleNamedArgsUtil;
import manifold.ij.util.ManPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static manifold.ext.params.ParamsIssueMsg.MSG_OPT_PARAM_NAME_MISMATCH;
import static manifold.ext.params.ParamsIssueMsg.MSG_OPT_PARAM_OVERRIDABLE_METHOD_OVERLOAD_NOT_ALLOWED;
import static manifold.ij.extensions.ManParamsAugmentProvider.hasInitializer;

/**
 *
 */
public class ParamsAnnotator implements Annotator
{
  @Override
  public void annotate( @NotNull PsiElement element, @NotNull AnnotationHolder holder )
  {
    if( !ManProject.isManifoldInUse( element ) )
    {
      // Manifold jars are not used in the project
      return;
    }

    if( DumbService.getInstance( element.getProject() ).isDumb() )
    {
      // skip processing during index rebuild
      return;
    }

    ManModule module = ManProject.getModule( element );

    errorIfOptionalParam( module, element, holder );

    if( module != null && !module.isParamsEnabled() )
    {
      // project/module not using params
      return;
    }

    checkParams( element, holder );

    if( element instanceof PsiMethod psiMethod )
    {
      findSuperMethod_ParamCheck( psiMethod, holder );
    }
  }

  private void errorIfOptionalParam( ManModule module, @NotNull PsiElement element, @NotNull AnnotationHolder holder )
  {
    if( module != null && !module.isParamsEnabled() && element instanceof PsiParameter )
    {
      PsiParameter param = (PsiParameter)element;
      if( hasInitializer( param ) )
      {
        // manifold-params is not in use from the module at the call-site
        if( param.getNameIdentifier() != null )
        {
          TextRange textRange = param.getNameIdentifier().getTextRange();
          TextRange range = new TextRange( textRange.getStartOffset(), textRange.getEndOffset() );
          holder.newAnnotation( HighlightSeverity.ERROR,
              "<html>Optional parameters are supported with <a href=\"https://github.com/manifold-systems/manifold/blob/master/manifold-deps-parent/manifold-params/README.md\">manifold-params</a></html>" )
            .range( range )
            .create();
        }
      }
    }
  }

  private void checkParams( PsiElement element, AnnotationHolder holder )
  {
    if( element instanceof PsiMethod psiMethod )
    {
      PsiClass containingClass = ManPsiUtil.getContainingClass( element );
      if( containingClass instanceof PsiExtensibleClass psiClass )
      {
        for( PsiParameter param : psiMethod.getParameterList().getParameters() )
        {
          if( hasInitializer( param ) )
          {
            ParamsMaker.checkParamsClass( psiMethod, psiClass, holder );
            ParamsMaker.checkMethod( psiMethod, psiClass, holder );
            break;
          }
        }
      }
    }
    else if( element instanceof ManPsiTupleExpression )
    {
      PsiElement parent = element.getParent();
      if( parent instanceof PsiExpressionList && ((PsiExpressionList)parent).getExpressionCount() == 1 )
      {
        parent = parent.getParent();
        if( parent instanceof PsiCallExpression ||
            parent instanceof PsiAnonymousClass ||
            parent instanceof PsiEnumConstant )
        {
          // calling only to check/add compile errors
          TupleNamedArgsUtil.getNewParamsClassExprType( parent, (ManPsiTupleExpression)element, holder );
        }
      }
    }
  }

  private void checkIllegalOverrides( PsiElement element, AnnotationHolder holder )
  {
    if( !(element instanceof PsiExtensibleClass) )
    {
      return;
    }

    PsiExtensibleClass psiClass = (PsiExtensibleClass) element;
    Map<String, ArrayList<PsiMethod>> methodsByName = new HashMap<>();
    for( PsiMethod def : psiClass.getOwnMethods() )
    {
      methodsByName.computeIfAbsent( def.getName(), __-> new ArrayList<>() )
              .add( def );
    }
    for( Map.Entry<String, ArrayList<PsiMethod>> entry : methodsByName.entrySet() )
    {
      if( entry.getValue().size() > 1 &&
              // two or more method overloads having optional parameters
              entry.getValue().stream()
                      .filter( m -> isOverridable( m ) && m.getParameterList().getParameters().stream()
                              .anyMatch( param -> hasInitializer( param ) ) )
                      .count() > 1 )
      {
        for( PsiMethod m : entry.getValue() )
        {
          if( m.getParameterList().getParameters().stream().anyMatch(
                  param -> hasInitializer( param ) ) || hasOverloadInSuperTypes( psiClass, m, holder ) )
          {

            TextRange textRange = m.getNameIdentifier().getTextRange();
            TextRange range = new TextRange( textRange.getStartOffset(), textRange.getEndOffset() );
            holder.newAnnotation( HighlightSeverity.ERROR,
                            MSG_OPT_PARAM_OVERRIDABLE_METHOD_OVERLOAD_NOT_ALLOWED.get( entry.getKey() ) )
                    .range( range )
                    .create();
          }
        }
      }
    }
  }

  private boolean hasOverloadInSuperTypes( PsiExtensibleClass tree, PsiMethod method, AnnotationHolder holder )
  {
    if( method.isConstructor() )
    {
      return false;
    }

    PsiMethod superMethod = findSuperMethod_ParamCheck( method, holder );
    if( superMethod != null )
    {
      // ignore super methods because the super method will be checked against methods in its class
      return false;
    }

    return tree.getAllMethods().stream()
            .anyMatch( m -> m != method &&
                 !m.isConstructor() &&
                 m.getName().equals( method.getName() ) &&
                 JavaResolveUtil.isAccessible( m, m.getContainingClass(), m.getModifierList(), null, tree, tree.getContainingFile() ) &&
                 m.getParameterList().getParameters().stream().anyMatch( p -> hasInitializer( p ) ) );
  }

//  private Set<PsiClass> getAllSuperTypes( PsiClass psiClass )
//  {
//    return getAllSuperTypes( psiClass, psiClass, new LinkedHashSet<>() );
//  }
//  private Set<PsiClass> getAllSuperTypes( PsiClass origin, PsiClass psiClass, Set<PsiClass> result )
//  {
//    if( result.contains( psiClass ) )
//    {
//      return result;
//    }
//    if( origin != psiClass )
//    {
//      result.add( psiClass );
//    }
//
//    for( PsiClassType superType : psiClass.getSuperTypes() )
//    {
//      getAllSuperTypes( origin, PsiTypesUtil.getPsiClass( superType ), result );
//    }
//    return result;
//  }

  private PsiMethod findSuperMethod_ParamCheck( PsiMethod method, AnnotationHolder holder )
  {
    PsiMethod superMethod = findSuperMethod_NoParamCheck( method );
    if( superMethod == null )
    {
      return null;
    }

    // check that param names match
    checkParamNames( method, superMethod, holder );
    return superMethod;
  }

  private void checkParamNames(PsiMethod method, PsiMethod superMethod, AnnotationHolder holder )
  {
    List<PsiParameter> superParams = Arrays.asList( superMethod.getParameterList().getParameters() );
    for( int i = 0, paramsSize = superParams.size(); i < paramsSize; i++ )
    {
      PsiParameter superParam = superParams.get( i );
      PsiParameter param = method.getParameterList().getParameter( i );
      if( param != null && !superParam.getName().equals( param.getName() ) )
      {
        TextRange textRange = param.getNameIdentifier().getTextRange();
        TextRange range = new TextRange( textRange.getStartOffset(), textRange.getEndOffset() );
        holder.newAnnotation( HighlightSeverity.ERROR,
                        MSG_OPT_PARAM_NAME_MISMATCH.get( param.getName(), superParam.getName() ) )
                .range( range )
                .create();
      }
    }
  }

  private PsiMethod findSuperMethod_NoParamCheck( PsiMethod method )
  {
    if( !couldOverride( method ) )
    {
      return null;
    }

    PsiMethod superMethod = findSuperMethodImpl(method );
    if( superMethod != null )
    {
      // directly overrides super method
      return (PsiMethod)superMethod;
    }
// todo: impl this
//
//    // check for indirect override where overrider has additional optional parameters
//
//    int lastPositional = -1;
//    List<PsiParameter> requiredForOverride = new ArrayList<>();
//    List<PsiParameter> remainingOptional = new ArrayList<>();
//    List<PsiParameter> params = Arrays.asList( method.getParameterList().getParameters() );
//    for( int i = 0; i < params.size(); i++ )
//    {
//      PsiParameter param = params.get( i );
//      if( hasInitializer( param ) )
//      {
//        lastPositional = i;
//      }
//    }
//    for( int i = 0; i <= lastPositional; i++ )
//    {
//      requiredForOverride.add( params.get( i ) );
//    }
//    for( int i = lastPositional + 1; i < params.size(); i++ )
//    {
//      remainingOptional.add( params.get( i ) );
//    }
//
//    for( int i = lastPositional + remainingOptional.size(); i > lastPositional; i-- ) // excludes last param bc we already checked for a direct override up top
//    {
//      List<PsiParameter> l = params.subList( 0, i );
//      List<PsiParameter> superSig = l;
//      // this is from manifold javac plugin, there's no good direct translation to IJ API, but maybe use the generated telescoping methods--find their super methods?
//      PsiMethod methodTypeWithParameters = getTypes().createMethodTypeWithParameters( method.sym.type, superSig );
//      superMethod = findSuperMethod( classDecl().sym, method.name, methodTypeWithParameters );
//      if( superMethod != null )
//      {
//        return (PsiMethod)superMethod;
//      }
//    }
    return null;
  }

  // search the ancestry for the super method, if the direct super method does not have optional parameters, search for
  // its direct super method, and so on until a super method with optional parameters is found, otherwise return null.
  private PsiMethod findSuperMethodImpl(PsiMethod candidate )
  {
    PsiMethod[] superMethods = candidate.findSuperMethods( true );
    if( superMethods.length > 0 )
    {
      PsiMethod sm = superMethods[0];
      if( !hasParamAnnoValue( sm ) )
      {
        return findSuperMethodImpl( sm );
      }
      return sm;
    }
    return null;
  }

  private boolean hasParamAnnoValue( PsiMethod superMethod )
  {
    PsiClass superClass = superMethod.getContainingClass();
    for( PsiMethod m: superClass.getMethods() )
    {
      if( m.getName().equals( superMethod.getName()) )
      {
        @Nullable PsiAnnotation paramsAnno = m.getAnnotation( params.class.getTypeName() );
        if( paramsAnno != null )
        {
          return true;
        }
      }
    }
    return false;
  }

  public boolean isOverridable( PsiMethod m )
  {
    PsiModifierList mods = m.getModifierList();
    return !mods.hasModifierProperty( PsiModifier.FINAL ) &&
           !mods.hasModifierProperty( PsiModifier.STATIC ) &&
           !mods.hasModifierProperty( PsiModifier.PRIVATE ) &&
           !m.isConstructor();
  }

  private boolean couldOverride( PsiMethod m )
  {
    PsiModifierList mods = m.getModifierList();
    return !mods.hasModifierProperty( PsiModifier.STATIC ) &&
           !mods.hasModifierProperty( PsiModifier.PRIVATE ) &&
           !m.isConstructor();
  }
}

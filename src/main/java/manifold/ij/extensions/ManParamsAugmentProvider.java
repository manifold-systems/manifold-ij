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

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.impl.light.LightMethod;
import com.intellij.psi.impl.light.LightParameterListWrapper;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import manifold.ij.core.ManProject;
import manifold.util.ReflectUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * - generate static inner data class aligning with method parameters having one or more default values, and also aligning with potential tuple matching data class<br>
 * - generate forwarding method overload matching above default param method where the parameters are replaced with the static inner data class above
 * - note, the ManPsiTupleExpressionImpl handles the retyping of the tuple as that of a generated `new $foo(...)` expression, where $foo is the static inner data class
 */
public class ManParamsAugmentProvider extends PsiAugmentProvider
{
  static final Key<CachedValue<List<PsiClass>>> KEY_CACHED_PARAM_CLASS_AUGMENTS = new Key<>( "KEY_CACHED_PARAM_CLASS_AUGMENTS" );
  static final Key<CachedValue<List<PsiMethod>>> KEY_CACHED_PARAM_METHOD_AUGMENTS = new Key<>( "KEY_CACHED_PARAM_METHOD_AUGMENTS" );

  @SuppressWarnings( "deprecation" )
  @NotNull
  public <E extends PsiElement> List<E> getAugments( @NotNull PsiElement element, @NotNull Class<E> cls )
  {
    return getAugments( element, cls, null );
  }

  @NotNull
  public <E extends PsiElement> List<E> getAugments( @NotNull PsiElement element, @NotNull Class<E> cls, String nameHint )
  {
    return ApplicationManager.getApplication().runReadAction( (Computable<List<E>>)() -> _getAugments( element, cls ) );
  }

  private <E extends PsiElement> List<E> _getAugments( PsiElement element, Class<E> cls )
  {

    // Module is assigned to user-data via ManTypeFinder, which loads the psiClass (element)
    if( DumbService.getInstance( element.getProject() ).isDumb() )
    {
      // skip processing during index rebuild
      return Collections.emptyList();
    }

    if( !(element instanceof PsiExtensibleClass) || !element.isValid() )
    {
      return Collections.emptyList();
    }

    if( !ManProject.isParamsEnabledInAnyModules( element ) )
    {
      // manifold-param jar is not used in the project
      return Collections.emptyList();
    }

    PsiExtensibleClass psiClass = (PsiExtensibleClass)element;

    if( psiClass.getLanguage() != JavaLanguage.INSTANCE &&
      psiClass.getLanguage().getBaseLanguage() != JavaLanguage.INSTANCE )
    {
      return Collections.emptyList();
    }

    String className = psiClass.getQualifiedName();
    if( className == null )
    {
      return Collections.emptyList();
    }

// Not cached:
//    LinkedHashMap<String, PsiMember> augFeatures = new LinkedHashMap<>();
//
//    if( PsiMethod.class.isAssignableFrom( cls ) )
//    {
//      addMethods( psiClass, augFeatures );
//    }
//    else if( PsiClass.class.isAssignableFrom( cls ) )
//    {
//      addStructuralInterfaces( psiClass, augFeatures );
//    }
//
//    //noinspection unchecked
//    return new ArrayList<>( (Collection<? extends E>)augFeatures.values() );

// Cached:
    ReflectUtil.FieldRef DO_CHECKS = ReflectUtil.field( "com.intellij.util.CachedValueStabilityChecker", "DO_CHECKS" );
    try { if( (boolean)DO_CHECKS.getStatic() ) DO_CHECKS.setStatic( false ); } catch( Throwable ignore ){}
    if( PsiMethod.class.isAssignableFrom( cls ) )
    {
      //noinspection unchecked
      return getCachedAugments( psiClass, (Key)KEY_CACHED_PARAM_METHOD_AUGMENTS,
        augFeatures -> addMethods( psiClass, augFeatures ) );
    }
    else if( PsiClass.class.isAssignableFrom( cls ) )
    {
      //noinspection unchecked
      return getCachedAugments( psiClass, (Key)KEY_CACHED_PARAM_CLASS_AUGMENTS,
        augFeatures -> addParamsClasses( psiClass, augFeatures ) );
    }
    return Collections.emptyList();
  }

  private <E extends PsiElement> List<E> getCachedAugments( PsiExtensibleClass psiClass,
                                                            Key<CachedValue<List<E>>> key,
                                                            Consumer<LinkedHashMap<String, PsiMember>> augmenter )
  {
    return CachedValuesManager.getCachedValue( psiClass, key, new MyCachedValueProvider<>( psiClass, augmenter ) );
  }

  private static class MyCachedValueProvider<X extends PsiElement> implements CachedValueProvider<List<X>>
  {
    private final SmartPsiElementPointer<PsiExtensibleClass> _psiClassPointer;
    private final Consumer<LinkedHashMap<String, PsiMember>> _augmenter;

    public MyCachedValueProvider( PsiExtensibleClass psiClass, Consumer<LinkedHashMap<String, PsiMember>> augmenter )
    {
      _psiClassPointer = SmartPointerManager.createPointer( psiClass );
      _augmenter = augmenter;
    }

    @Override
    public @Nullable Result<List<X>> compute()
    {
      LinkedHashMap<String, PsiMember> augFeatures = new LinkedHashMap<>();
      _augmenter.accept( augFeatures );
      Set<PsiElement> hierarchy = new LinkedHashSet<>();
      PsiExtensibleClass psiClass = _psiClassPointer.getElement();
      if( psiClass == null )
      {
        return new Result<>( Collections.emptyList() );
      }

      hierarchy.add( psiClass );
      hierarchy.addAll( Arrays.asList( psiClass.getSupers() ) );
      //noinspection unchecked
      return new Result<>(
        new ArrayList<>( (Collection<X>)augFeatures.values() ), hierarchy.toArray() );
    }

    @Override
    public int hashCode()
    {
      return Objects.hashCode( _psiClassPointer.getElement() );
    }

    @Override
    public boolean equals( Object obj )
    {
      if( obj instanceof MyCachedValueProvider<?> )
      {
        PsiExtensibleClass thisElem = _psiClassPointer.getElement();
        PsiExtensibleClass thatElem = ((MyCachedValueProvider<?>)obj)._psiClassPointer.getElement();
        return Objects.equals( thatElem, thisElem ) &&
          (thisElem == null || thisElem instanceof PsiCompiledElement || // .class files don't change
            Objects.equals( thisElem.getTextRange(), thatElem.getTextRange() ));
      }
      return false;
    }
  }

  private void addParamsClasses( PsiExtensibleClass psiClass, LinkedHashMap<String, PsiMember> augFeatures )
  {
    if( psiClass instanceof ClsClassImpl )
    {
      // .class files already have it
      return;
    }

    handleRecord_class( psiClass, augFeatures );

    for( PsiMethod method : psiClass.getOwnMethods() )
    {
      for( PsiParameter param : method.getParameterList().getParameters() )
      {
        if( hasInitializer( param ) )
        {
          ParamsMaker.generateParamsClass( method, psiClass, augFeatures );
          break;
        }
      }
    }
  }

  private static void handleRecord_class( PsiExtensibleClass psiClass, LinkedHashMap<String, PsiMember> augFeatures )
  {
    if( psiClass.isRecord() )
    {
      for( PsiRecordComponent rc : psiClass.getRecordComponents() )
      {
        if( hasInitializer( rc ) )
        {
          LightMethod psiCtor = makeDisconnectedRecordCtor( psiClass );
          ParamsMaker.generateParamsClass( psiCtor, psiClass, augFeatures );
          break;
        }
      }
    }
  }

  private static @NotNull LightMethod makeDisconnectedRecordCtor( PsiExtensibleClass psiClass )
  {
    String params = psiClass.getRecordComponents().stream().map( c -> c.getText() ).collect( Collectors.joining( ", " ) );
    String ctor = "public ${psiClass.getName()}($params){}";
    PsiMethod psiDummyCtor = JavaPsiFacade.getElementFactory( psiClass.getProject() ).createMethodFromText( ctor, psiClass );
    LightMethod psiCtor = new LightMethod( psiClass.getManager(), psiDummyCtor, psiClass );
    psiCtor.setNavigationElement( psiClass );
    return psiCtor;
  }

  private void addMethods( PsiExtensibleClass psiClass, LinkedHashMap<String, PsiMember> augFeatures )
  {
    if( psiClass instanceof ClsClassImpl )
    {
      // .class files already have it
      return;
    }

    handleRecord_methods( psiClass, augFeatures );

    for( PsiMethod method : psiClass.getOwnMethods() )
    {
      for( PsiParameter param : method.getParameterList().getParameters() )
      {
        if( hasInitializer( param ) )
        {
          ParamsMaker.generateMethod( method, psiClass, augFeatures );
          break;
        }
      }
    }
  }

  private static void handleRecord_methods( PsiExtensibleClass psiClass, LinkedHashMap<String, PsiMember> augFeatures )
  {
    if( psiClass.isRecord() )
    {
      for( PsiRecordComponent rc : psiClass.getRecordComponents() )
      {
        if( hasInitializer( rc ) )
        {
          LightMethod psiCtor = makeDisconnectedRecordCtor( psiClass );
          ParamsMaker.generateMethod( psiCtor, psiClass, augFeatures );
          break;
        }
      }
    }
  }

  static boolean hasOptionalParam( PsiParameterList paramList )
  {
    if( paramList instanceof LightParameterListWrapper lightParamListWrapper )
    {
      // record param list
      paramList = lightParamListWrapper.jailbreak().myDelegate;
    }

    for( PsiParameter param : paramList.getParameters() )
    {
      if( hasInitializer( param ) )
      {
        return true;
      }
    }
    return false;
  }

  static boolean hasInitializer( PsiVariable param )
  {
    PsiElement idElem = param.getIdentifyingElement();
    if( idElem == null )
    {
      return false;
    }
    int startOffsetInParent = idElem.getStartOffsetInParent();
    String paramText = param.getText();
    if( paramText == null )
    {
      return false;
    }
    int iEq = paramText.indexOf( '=', startOffsetInParent + idElem.getTextLength() );
    return iEq >= 0;
  }
}

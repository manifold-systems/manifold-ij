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
import com.intellij.lang.jvm.annotation.JvmAnnotationArrayValue;
import com.intellij.lang.jvm.annotation.JvmAnnotationAttribute;
import com.intellij.lang.jvm.annotation.JvmAnnotationAttributeValue;
import com.intellij.lang.jvm.annotation.JvmNestedAnnotationValue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import manifold.ij.core.ManProject;
import manifold.ij.psi.ManLightFieldBuilder;
import manifold.ij.psi.ManLightModifierListImpl;
import manifold.ij.psi.ManPsiElementFactory;
import manifold.util.ReflectUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

import static manifold.ij.extensions.DelegationMaker.generateMethods;

/**
 * - Generate stubbed methods for linked interfaces
 */
public class ManDelegationAugmentProvider extends PsiAugmentProvider
{
  static final Key<CachedValue<List<PsiMethod>>> KEY_CACHED_DELEGATION_AUGMENTS = new Key<>( "KEY_CACHED_DELEGATION_AUGMENTS" );

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

    if( !ManProject.isDelegationEnabledInAnyModules( element ) )
    {
      // Manifold jars are not used in the project
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
//    LinkedHashSet<PsiMember> augFeatures = new LinkedHashSet<>();
//
//    if( PsiMethod.class.isAssignableFrom( cls ) )
//    {
//      addMethods( psiClass, augFeatures );
//    }
//
//    //noinspection unchecked
//    return new ArrayList<>( (Collection<? extends E>)augFeatures );

// Cached:
    ReflectUtil.FieldRef DO_CHECKS = ReflectUtil.field( "com.intellij.util.CachedValueStabilityChecker", "DO_CHECKS" );
    try { if( (boolean)DO_CHECKS.getStatic() ) DO_CHECKS.setStatic( false ); } catch( Throwable ignore ){}
    if( PsiMethod.class.isAssignableFrom( cls ) )
    {
      //noinspection unchecked
      return getCachedAugments( psiClass, (Key)KEY_CACHED_DELEGATION_AUGMENTS,
        augFeatures -> addMethods( psiClass, augFeatures ) );
    }
    return Collections.emptyList();
  }

  private <E extends PsiElement> List<E> getCachedAugments( PsiExtensibleClass psiClass,
                                                            Key<CachedValue<List<E>>> key,
                                                            Consumer<LinkedHashSet<PsiMember>> augmenter )
  {
    return CachedValuesManager.getCachedValue( psiClass, key, new MyCachedValueProvider<E>( psiClass, augmenter ) );
  }

  private static class MyCachedValueProvider<X extends PsiElement> implements CachedValueProvider<List<X>>
  {
    private final SmartPsiElementPointer<PsiExtensibleClass> _psiClassPointer;
    private final Consumer<LinkedHashSet<PsiMember>> _augmenter;

    public MyCachedValueProvider( PsiExtensibleClass psiClass, Consumer<LinkedHashSet<PsiMember>> augmenter )
    {
      _psiClassPointer = SmartPointerManager.createPointer( psiClass );
      _augmenter = augmenter;
    }

    @Override
    public @Nullable Result<List<X>> compute()
    {
      LinkedHashSet<PsiMember> augFeatures = new LinkedHashSet<>();
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
        new ArrayList<X>( (Collection<X>)augFeatures ), hierarchy.toArray() );
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
        return Objects.equals( ((MyCachedValueProvider<?>)obj)._psiClassPointer.getElement(), _psiClassPointer.getElement() );
      }
      return false;
    }
  }

  private void addMethods( PsiExtensibleClass psiClass, LinkedHashSet<PsiMember> augFeatures )
  {
    if( psiClass instanceof ClsClassImpl )
    {
      return;
    }

    DelegationMaker.generateMethods( psiClass, augFeatures );
  }
}

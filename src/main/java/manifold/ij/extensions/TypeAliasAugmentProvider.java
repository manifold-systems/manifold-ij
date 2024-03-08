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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import manifold.ij.core.ManProject;
import manifold.util.ReflectUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * - Generate stubbed methods for type aliased class
 */
public class TypeAliasAugmentProvider extends PsiAugmentProvider {

  static final Key<CachedValue<Collection<PsiMember>>> KEY_CACHED_TYPE_ALIAS_AUGMENTS = new Key<>("KEY_CACHED_TYPE_ALIAS_AUGMENTS");

  @SuppressWarnings("deprecation")
  @NotNull
  public <E extends PsiElement> List<E> getAugments( @NotNull PsiElement element, @NotNull Class<E> cls )
  {
    return getAugments( element, cls, null );
  }

  @NotNull
  public <E extends PsiElement> List<E> getAugments( @NotNull PsiElement element, @NotNull Class<E> cls, String nameHint )
  {
    return ApplicationManager.getApplication().runReadAction( (Computable<List<E>>) () -> _getAugments( element, cls ) );
  }

  private <E extends PsiElement> List<E> _getAugments( PsiElement element, Class<E> cls )
  {
    // first search all element in the element.
    Collection<PsiMember> allElements = _getExtendedMembers( element );
    if( allElements.isEmpty() )
    {
      return Collections.emptyList();
    }
    // filter specified element and the cast to target class.
    ArrayList<E> results = new ArrayList<>( allElements.size() );
    for( PsiElement psiMemberElement : allElements )
    {
      if( cls.isInstance( psiMemberElement ) )
      {
        results.add( cls.cast( psiMemberElement ) );
      }
    }
    return results;
  }

  private Collection<PsiMember> _getExtendedMembers(PsiElement element )
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

    if( !ManProject.isTypeAliasEnabledInAnyModules( element ) )
    {
      // Manifold jars are not used in the project
      return Collections.emptyList();
    }

    PsiExtensibleClass psiClass = (PsiExtensibleClass) element;

    if( psiClass.getLanguage() != JavaLanguage.INSTANCE &&
            psiClass.getLanguage().getBaseLanguage() != JavaLanguage.INSTANCE )
    {
      return Collections.emptyList();
    }

    PsiClass newPsiClass = TypeAliasMaker.getAliasedType( psiClass );
    if( newPsiClass == null )
    {
      return Collections.emptyList();
    }
//    System.out.println("get all from: " + psiClass + "(" + newPsiClass + ")" );

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
    ReflectUtil.FieldRef DO_CHECKS = ReflectUtil.field("com.intellij.util.CachedValueStabilityChecker", "DO_CHECKS");
    try { if ((boolean) DO_CHECKS.getStatic()) { DO_CHECKS.setStatic(false); } } catch (Throwable ignore) { }
    return CachedValuesManager.getCachedValue( psiClass, KEY_CACHED_TYPE_ALIAS_AUGMENTS, new MyCachedValueProvider( psiClass ) );
  }

  private static class MyCachedValueProvider implements CachedValueProvider<Collection<PsiMember>>
  {
    private final SmartPsiElementPointer<PsiExtensibleClass> _psiClassPointer;

    public MyCachedValueProvider( PsiExtensibleClass psiClass )
    {
      _psiClassPointer = SmartPointerManager.createPointer( psiClass );
    }

    @Nullable
    @Override
    public Result<Collection<PsiMember>> compute()
    {
      PsiExtensibleClass psiClass = _psiClassPointer.getElement();
      PsiClass newPsiClass = TypeAliasMaker.getAliasedType( psiClass );
      if( psiClass == null || newPsiClass == null )
      {
        return Result.create( Collections.emptyList() );
      }
      LinkedHashSet<PsiMember> features = new LinkedHashSet<>();
      Set<PsiElement> dependencies = new LinkedHashSet<>();
      TypeAliasMaker.generateMembers( psiClass, newPsiClass, features );
      dependencies.add( psiClass );
      dependencies.add( newPsiClass );
      dependencies.add( TypeAliasMaker.getAnnotation( psiClass ) );
      return Result.create( features, dependencies.toArray() );
    }

    @Override
    public int hashCode()
    {
      return Objects.hash( _psiClassPointer.getElement() );
    }

    @Override
    public boolean equals( Object obj )
    {
      if( obj instanceof MyCachedValueProvider other )
      {
        return Objects.equals( _psiClassPointer.getElement(), other._psiClassPointer.getElement() );
      }
      return false;
    }
  }
}

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
import manifold.ext.props.rt.api.propgen;
import manifold.ij.core.ManProject;
import manifold.ij.psi.ManLightFieldBuilder;
import manifold.ij.psi.ManLightModifierListImpl;
import manifold.ij.psi.ManPsiElementFactory;
import manifold.util.ReflectUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

import static manifold.ij.extensions.PropertyInference.GETTER_TAG;
import static manifold.ij.extensions.PropertyInference.SETTER_TAG;
import static manifold.ij.extensions.PropertyMaker.generateAccessors;

/**
 * - Re-create non-backing property fields (from .class files compiled with declared properties).
 * - Generate getter/setter methods (for source files with declared properties)
 * - Create inferred property fields (for both .class and source files)
 */
public class ManPropertiesAugmentProvider extends PsiAugmentProvider
{
  static final Key<CachedValue<List<PsiField>>> KEY_CACHED_PROP_FIELD_AUGMENTS = new Key<>( "KEY_CACHED_PROP_FIELD_AUGMENTS" );
  static final Key<CachedValue<List<PsiMethod>>> KEY_CACHED_PROP_METHOD_AUGMENTS = new Key<>( "KEY_CACHED_PROP_METHOD_AUGMENTS" );

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

  @Override
  protected @NotNull Set<String> transformModifiers( @NotNull PsiModifierList modifierList, @NotNull Set<String> modifiers )
  {
    if( !ManProject.isPropertiesEnabledInAnyModules( modifierList ) )
    {
      // manifold-props jars are not used in the project
      return modifiers;
    }

    final PsiElement parent = modifierList.getParent();
    if( parent instanceof PsiField && PropertyInference.isPropertyField( (PsiField)parent ) )
    {
      if( !modifierList.hasExplicitModifier( PsiModifier.STATIC ) )
      {
        modifiers = new HashSet<>( modifiers );
        modifiers.remove( PsiModifier.STATIC );
      }
    }
    return modifiers;
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

    if( !ManProject.isPropertiesEnabledInAnyModules( element ) )
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
//    LinkedHashMap<String, PsiMember> augFeatures = new LinkedHashMap<>();
//
//    if( PsiMethod.class.isAssignableFrom( cls ) )
//    {
//      addMethods( psiClass, augFeatures );
//    }
//    else if( PsiField.class.isAssignableFrom( cls ) )
//    {
//      recreateNonbackingPropertyFields( psiClass, augFeatures );
//      inferPropertyFieldsFromAccessors( psiClass, augFeatures );
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
      return getCachedAugments( psiClass, (Key)KEY_CACHED_PROP_METHOD_AUGMENTS,
        augFeatures -> addMethods( psiClass, augFeatures ) );
    }
    else if( PsiField.class.isAssignableFrom( cls ) )
    {
      //noinspection unchecked
      return getCachedAugments( psiClass, (Key)KEY_CACHED_PROP_FIELD_AUGMENTS, augFeatures -> {
        recreateNonbackingPropertyFields( psiClass, augFeatures );
        inferPropertyFieldsFromAccessors( psiClass, augFeatures );
      } );
    }
    return Collections.emptyList();
  }

  private <E extends PsiElement> List<E> getCachedAugments( PsiExtensibleClass psiClass,
                                                            Key<CachedValue<List<E>>> key,
                                                            Consumer<LinkedHashMap<String, PsiMember>> augmenter )
  {
    return CachedValuesManager.getCachedValue( psiClass, key, new MyCachedValueProvider<E>( psiClass, augmenter ) );
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
        return new CachedValueProvider.Result<>( Collections.emptyList() );
      }

      hierarchy.add( psiClass );
      hierarchy.addAll( Arrays.asList( psiClass.getSupers() ) );
      addFilesOfNavigationElements( augFeatures, hierarchy );
      //noinspection unchecked
      return new CachedValueProvider.Result<>(
        new ArrayList<X>( (Collection<X>)augFeatures.values() ), hierarchy.toArray() );
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
          (thisElem == null ||
          Objects.equals( thisElem.getTextRange(), thatElem.getTextRange() ));
      }
      return false;
    }
  }

  /**
   * Add the containing PsiFiles for the properties. This is necessary when inferring properties for a projected class
   * from a type manifold; changes to the corresponding resource files should invalidate the cache.
   */
  private static void addFilesOfNavigationElements( LinkedHashMap<String, PsiMember> augFeatures, Set<PsiElement> hierarchy )
  {
    augFeatures.values().forEach( feature -> {
      PsiElement navigationElement = feature.getNavigationElement();
      if( navigationElement != null && navigationElement != feature )
      {
        PsiFile containingFile = navigationElement.getContainingFile();
        if( containingFile != null )
        {
          hierarchy.add( containingFile );
        }
      }
    } );
  }

  private void inferPropertyFieldsFromAccessors( PsiExtensibleClass psiClass, LinkedHashMap<String, PsiMember> augFeatures )
  {
    forceAncestryToAugmentFields( psiClass, psiClass );
    PropertyInference.inferPropertyFields( psiClass, augFeatures );
  }

  private static final Key<Boolean> forceAncestryToAugmentFields_KEY = Key.create( "forceAncestryToAugment_TAG" );
  private void forceAncestryToAugmentFields( PsiClass psiClass, PsiClass origin )
  {
    if( !(psiClass instanceof PsiExtensibleClass) ||
      psiClass.getUserData( forceAncestryToAugmentFields_KEY ) != null ||
      psiClass.getUserData( KEY_CACHED_PROP_FIELD_AUGMENTS ) != null )
    {
      return;
    }

    psiClass.putUserData( forceAncestryToAugmentFields_KEY, true );
    try
    {
      PsiClass st = psiClass.getSuperClass();
      forceAncestryToAugmentFields( st, origin );
      for( PsiClass iface : psiClass.getInterfaces() )
      {
        forceAncestryToAugmentFields( iface, origin );
      }
      if( psiClass != origin )
      {
        // force augments to load on fields, for the side effect of adding VAR_TAG etc. to existing fields
        PsiAugmentProvider.collectAugments( psiClass, PsiField.class, null );
      }
    }
    finally
    {
      psiClass.putUserData( forceAncestryToAugmentFields_KEY, null );
    }
  }

  private void recreateNonbackingPropertyFields( PsiExtensibleClass psiClass, LinkedHashMap<String, PsiMember> augFeatures )
  {
    if( !(psiClass instanceof ClsClassImpl) )
    {
      return;
    }

    ClsClassImpl clsClass = (ClsClassImpl)psiClass;

    // Recreate non-backing property fields based on @propgen annotations on corresponding getter/setter
    //
    for( PsiMethod m : clsClass.getOwnMethods() )
    {
      PsiAnnotation propgenAnno = m.getAnnotation( propgen.class.getTypeName() );
      if( propgenAnno != null )
      {
        //noinspection ConstantConditions
        String fieldName = (String)((PsiLiteralValue)propgenAnno.findAttributeValue( "name" )).getValue();
        if( fieldName != null )
        {
          PsiMember field = augFeatures.get( fieldName );
          if( !(field instanceof PsiField) )
          {
            field = clsClass.getOwnFields().stream()
              .filter( f -> fieldName.equals( f.getName() ) )
              .findFirst().orElse( null );
          }
          if( field instanceof PsiField )
          {
            // prop field already exists
            addGetterSetterTag( (PsiField)field, m );
            continue;
          }

          ManLightFieldBuilder propField = addPropField( psiClass, m, propgenAnno, fieldName );
          augFeatures.put( fieldName, propField );
        }
      }
    }
  }

  private ManLightFieldBuilder addPropField( PsiExtensibleClass psiClass, PsiMethod accessor, PsiAnnotation propgenAnno, String fieldName )
  {
    @NotNull PsiParameter[] parameters = accessor.getParameterList().getParameters();
    PsiType type = parameters.length == 0 ? accessor.getReturnType() : parameters[0].getType();

    ManPsiElementFactory factory = ManPsiElementFactory.instance();
    ManLightFieldBuilder propField = factory.createLightField( psiClass.getManager(), fieldName, type, true )
      .withContainingClass( psiClass )
      .withNavigationElement( accessor );

    //noinspection ConstantConditions
    long flags = (long)((PsiLiteralValue)propgenAnno.findAttributeValue( "flags" )).getValue();
    List<String> modifiers = new ArrayList<>();
    for( ModifierMap modifier : ModifierMap.values() )
    {
      if( (flags & modifier.getMod()) != 0 )
      {
        modifiers.add( modifier.getName() );
      }
    }
    ManLightModifierListImpl modifierList = new ManLightModifierListImpl( psiClass.getManager(), JavaLanguage.INSTANCE,
      modifiers.toArray( new String[0] ) );
    propField.withModifierList( modifierList );

    addGetterSetterTag( propField, accessor );

    // add the @var, @val, @get, @set, etc. annotations
    for( JvmAnnotationAttribute attr : propgenAnno.getAttributes() )
    {
      JvmAnnotationAttributeValue value = attr.getAttributeValue();
      if( value instanceof JvmAnnotationArrayValue )
      {
        List<JvmAnnotationAttributeValue> values = ((JvmAnnotationArrayValue)value).getValues();
        if( !values.isEmpty() )
        {
          //noinspection ConstantConditions
          String anno = ((PsiNameValuePair)attr).getValue().getText();
          anno = anno.substring( 1, anno.length() - 1 );
          //noinspection UnstableApiUsage
          String fqn = ((JvmNestedAnnotationValue)values.get( 0 )).getValue().getQualifiedName();
          modifierList.addRawAnnotation( fqn, anno );
        }
      }
    }
    return propField;
  }

  private void addGetterSetterTag( PsiField propField, PsiMethod accessor )
  {
    propField.putCopyableUserData( accessor.getParameterList().getParametersCount() == 0 ? GETTER_TAG : SETTER_TAG,
      SmartPointerManager.createPointer( accessor ) );
  }

  private void addMethods( PsiExtensibleClass psiClass, LinkedHashMap<String, PsiMember> augFeatures )
  {
    if( psiClass instanceof ClsClassImpl )
    {
      // .class files already have getter/setter methods
      return;
    }

    for( PsiField field : psiClass.getOwnFields() )
    {
      generateAccessors( field, psiClass, augFeatures );
    }
  }
}

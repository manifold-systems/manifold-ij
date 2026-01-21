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

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.jvm.annotation.JvmAnnotationAttribute;
import com.intellij.lang.jvm.annotation.JvmAnnotationAttributeValue;
import com.intellij.lang.jvm.annotation.JvmAnnotationEnumFieldValue;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsModifierListImpl;
import com.intellij.psi.impl.java.stubs.PsiModifierListStub;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.impl.source.PsiModifierListImpl;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.sun.tools.javac.code.Flags;
import manifold.ext.props.PropIssueMsg;
import manifold.ext.props.rt.api.*;
import manifold.ij.core.ManProject;
import manifold.ij.psi.ManLightMethodBuilder;
import manifold.ij.psi.ManLightModifierListImpl;
import manifold.ij.psi.ManPsiElementFactory;
import manifold.rt.api.util.ManStringUtil;
import manifold.util.ReflectUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static java.lang.reflect.Modifier.*;
import static manifold.ext.props.PropIssueMsg.*;

class PropertyMaker
{
  private final AnnotationHolder _holder;
  private final LinkedHashMap<String, PsiMember> _augFeatures;
  private final PsiField _field;
  private final PsiExtensibleClass _psiClass;

  static void checkProperty( PsiField field, PsiExtensibleClass psiClass, AnnotationHolder holder )
  {
    new PropertyMaker( field, psiClass, holder ).generateOrCheck();
  }
  static void generateAccessors( PsiField field, PsiExtensibleClass psiClass, LinkedHashMap<String, PsiMember> augFeatures )
  {
    new PropertyMaker( field, psiClass, augFeatures ).generateOrCheck();
  }

  private PropertyMaker( PsiField field, PsiExtensibleClass psiClass, AnnotationHolder holder )
  {
    this( field, psiClass, holder, null );
  }

  private PropertyMaker( PsiField field, PsiExtensibleClass psiClass, LinkedHashMap<String, PsiMember> augFeatures )
  {
    this( field, psiClass, null, augFeatures );
  }

  private PropertyMaker( PsiField field, PsiExtensibleClass psiClass, AnnotationHolder holder, LinkedHashMap<String, PsiMember> augFeatures )
  {
    _field = field;
    _psiClass = psiClass;
    _holder = holder;
    _augFeatures = augFeatures;
  }

  private void generateOrCheck()
  {
    PsiAnnotation var = _field.getAnnotation( var.class.getTypeName() );
    PsiAnnotation val = _field.getAnnotation( val.class.getTypeName() );
    PsiAnnotation get = _field.getAnnotation( get.class.getTypeName() );
    PsiAnnotation set = _field.getAnnotation( set.class.getTypeName() );

    if( var == null && val == null && get == null && set == null )
    {
      // not a property field
      return;
    }

    if( _augFeatures != null )
    {
      removeOldTags( _field );
    }

    PsiModifierList modifiers = _field.getModifierList();

    boolean isAbstract = _field.getAnnotation( Abstract.class.getTypeName() ) != null;
    boolean isFinal = _field.getAnnotation( Final.class.getTypeName() ) != null;

    //noinspection ConstantConditions
    if( modifiers.hasModifierProperty( PsiModifier.ABSTRACT ) )
    {
      isAbstract = true;
    }
    if( modifiers.hasExplicitModifier( PsiModifier.FINAL ) )
    {
      isFinal = true;
    }

    //noinspection ConstantConditions
    if( isAbstract && !_psiClass.isInterface() && !_psiClass.getModifierList().hasModifierProperty( PsiModifier.ABSTRACT ) )
    {
      reportError( _field, PropIssueMsg.MSG_ABSTRACT_PROPERTY_IN_NONABSTRACT_CLASS.get() );
      return;
    }

    if( isFinal && isAbstract )
    {
      reportError( _field, MSG_FINAL_NOT_ALLOWED_ON_ABSTRACT.get() );
      return;
    }
    else if( isFinal && modifiers.hasExplicitModifier( PsiModifier.STATIC ) )
    {
      reportError( _field, MSG_FINAL_NOT_ALLOWED_ON_STATIC.get() );
      return;
    }

    // add getter and/or setter
    // or, if a PRIVATE property and no user-defined accessors exist, no benefit from property, so treat as field

    ManLightMethodBuilder generatedGetter = null;
    ManLightMethodBuilder generatedSetter = null;
    boolean shouldMakeProperty = !_field.getModifierList().hasModifierProperty( PsiModifier.PRIVATE );

    if( var != null || val != null || get != null )
    {
      List<JvmAnnotationAttribute> args = get == null ? val == null ? var.getAttributes() : val.getAttributes() : get.getAttributes();
      boolean getAbstract = isAbstract() || hasOption( args, PropOption.Abstract );
      boolean getFinal = isFinal || hasOption( args, PropOption.Final );
      PropOption getAccess = getAccess( args );

      if( shouldCheck() && !_psiClass.isInterface() && isWeakerAccess( getAccess, getAccess( modifiers ) ) )
      {
        reportError( var != null ? var : val != null ? val : get, MSG_ACCESSOR_WEAKER.get( "get",
          PropOption.fromModifier( getAccess( modifiers ) ).name().toLowerCase() ) );
      }

      if( shouldCheck() && var == null && !isStatic( _field ) )
      {
        PsiMethod superWritable = getSuperWritable();
        if( superWritable != null && !isStatic( superWritable ) )
        {
          reportError( val != null ? val : get, MSG_READONLY_CANNOT_OVERRIDE_WRITABLE.get( _field.getName(), _field.getName() ) );
        }
      }

      if( getFinal && getAbstract )
      {
        reportError( _field, MSG_FINAL_NOT_ALLOWED_ON_ABSTRACT.get() );
      }
      else if( getFinal && modifiers.hasExplicitModifier( PsiModifier.STATIC ) )
      {
        reportError( _field, MSG_FINAL_NOT_ALLOWED_ON_STATIC.get() );
      }
      else //noinspection ConstantConditions
        if( getAbstract && !_psiClass.isInterface() && !_psiClass.getModifierList().hasModifierProperty( PsiModifier.ABSTRACT ) )
      {
        reportError( _field, MSG_ABSTRACT_PROPERTY_IN_NONABSTRACT_CLASS.get() );
      }
      else
      {
//        PsiAnnotation anno = get == null ? val == null ? var : val : get;
        generatedGetter = addGetter( getAbstract, getFinal, getAccess );
        if( generatedGetter == null )
        {
          shouldMakeProperty = true;
        }
      }
    }
    else if( shouldCheck() )
    {
      PsiMethod existingGetter = findExistingAccessor( makeGetter( false, false, PropOption.Public ) );
      if( existingGetter != null )
      {
        reportError( existingGetter, MSG_GETTER_DEFINED_FOR_WRITEONLY.get( existingGetter.getName(), _field.getName() ) );
      }
    }

    if( var != null || set != null )
    {
      List<JvmAnnotationAttribute> args = set == null ? var.getAttributes() : set.getAttributes();
      boolean setAbstract = isAbstract() || _psiClass.isInterface() || hasOption( args, PropOption.Abstract );
      boolean setFinal = isFinal || hasOption( args, PropOption.Final );
      PropOption setAccess = getAccess( args );

      if( shouldCheck() && _field.hasInitializer() && setAbstract && !_psiClass.isInterface() )
      {
        reportError( _field.getInitializer(),
          MSG_WRITABLE_ABSTRACT_PROPERTY_CANNOT_HAVE_INITIALIZER.get( _field.getName() ) );
      }

      if( shouldCheck() && !_psiClass.isInterface() && isWeakerAccess( setAccess, getAccess( modifiers ) ) )
      {
        reportError( var != null ? var : set, MSG_ACCESSOR_WEAKER.get( "set",
          PropOption.fromModifier( getAccess( modifiers ) ).name().toLowerCase() ) );
      }

      if( shouldCheck() && var == null )
      {
        PsiMethod superReadable = getSuperReadable();
        if( superReadable != null && !isStatic( superReadable ) )
        {
          reportError( val != null ? val : get, MSG_WRITEONLY_CANNOT_OVERRIDE_READABLE.get(
            _field.getName(), _field.getName() ) );
        }
      }

      if( setFinal && setAbstract )
      {
        reportError( _field, MSG_FINAL_NOT_ALLOWED_ON_ABSTRACT.get() );
      }
      else if( setFinal && modifiers.hasExplicitModifier( PsiModifier.STATIC ) )
      {
        reportError( _field, MSG_FINAL_NOT_ALLOWED_ON_STATIC.get() );
      }
      else //noinspection ConstantConditions
        if( setAbstract && !_psiClass.isInterface() && !_psiClass.getModifierList().hasModifierProperty( PsiModifier.ABSTRACT ) )
      {
        reportError( _field, MSG_ABSTRACT_PROPERTY_IN_NONABSTRACT_CLASS.get() );
      }
      else
      {
        generatedSetter = addSetter( setAbstract, setFinal, setAccess );
        if( generatedSetter == null )
        {
          shouldMakeProperty = true;
        }
      }
    }
    else if( shouldCheck() )
    {
      PsiMethod existingSetter = findExistingAccessor( makeSetter( false, false, PropOption.Public ) );
      if( existingSetter != null )
      {
        reportError( _field, MSG_SETTER_DEFINED_FOR_READONLY.get(
          existingSetter.getName(), _field.getName() ) );
      }
    }

    verifyPropertyOverride( var, val, get, set );

    if( shouldMakeProperty )
    {
      if( (generatedGetter != null || generatedSetter != null) &&
        _psiClass.isInterface() && _field.getModifierList().hasExplicitModifier( PsiModifier.STATIC ) )
      {
        reportError( _field, MSG_INTERFACE_FIELD_BACKED_PROPERTY_NOT_SUPPORTED.get() );
      }
      else if( _augFeatures != null )
      {
        // add the generated accessors

        if( generatedGetter != null )
        {
          _augFeatures.put( generatedGetter.getName(), generatedGetter );
        }
        if( generatedSetter != null )
        {
          _augFeatures.put( generatedSetter.getName(), generatedSetter );
        }
      }
    }
  }

  private boolean shouldCheck()
  {
    return _holder != null;
  }

  // remove old tags that may stick around after changing a field
  static void removeOldTags( PsiField field )
  {
    field.putCopyableUserData( PropertyInference.VAR_TAG, null );
    removeAccessorTag( field, PropertyInference.GETTER_TAG );
    removeAccessorTag( field, PropertyInference.SETTER_TAG );
  }
  static void removeAccessorTag( PsiField field, Key<SmartPsiElementPointer<PsiMethod>> tag )
  {
    SmartPsiElementPointer<PsiMethod> accessor = field.getCopyableUserData( tag );
    field.putCopyableUserData( tag, null );
    if( accessor != null )
    {
      PsiMethod method = accessor.getElement();
      if( method != null )
      {
        method.putCopyableUserData( PropertyInference.FIELD_TAG, null );
      }
    }
  }

  private ManLightMethodBuilder addGetter( boolean propAbstract, boolean propFinal, PropOption propAccess )
  {
    ManLightMethodBuilder getter = makeGetter( propAbstract, propFinal, propAccess );
    getter.putCopyableUserData( PropertyInference.FIELD_TAG, SmartPointerManager.createPointer( _field ) );

    PsiMethod existingGetter = findExistingAccessor( getter );
    if( existingGetter != null )
    {
      _field.putCopyableUserData( PropertyInference.GETTER_TAG, SmartPointerManager.createPointer( existingGetter ) );
      existingGetter.putCopyableUserData( PropertyInference.FIELD_TAG, SmartPointerManager.createPointer( _field ) );

      verifyAccessor( propAbstract, propFinal, propAccess, existingGetter );

      return null;
    }
    else if( shouldCheck() && _psiClass.isInterface() &&
      _field.getModifierList() != null && _field.getModifierList().hasExplicitModifier( PsiModifier.STATIC ) &&
      !_field.hasInitializer() )
    {
      // interface: static non-initialized property MUST provide user-defined getter
      reportError( _field, MSG_MISSING_INTERFACE_STATIC_PROPERTY_ACCESSOR.get(
        _psiClass.getName(), getGetterName( true ) + "() : " + _field.getType().getCanonicalText(), _field.getName() ) );
    }

    return getter;
  }

  private ManLightMethodBuilder makeGetter( boolean propAbstract, boolean propFinal, PropOption propAccess )
  {
    //noinspection ConstantConditions
    ManLightModifierListImpl modifierList = getGetterSetterModifiers( _field.getModifierList(), _psiClass.isInterface(), propAbstract, propFinal,
      _field.getModifierList().hasExplicitModifier( PsiModifier.STATIC ), propAccess );
    ManPsiElementFactory factory = ManPsiElementFactory.instance();
    String methodName = getGetterName( true );
    return factory.createLightMethod( ManProject.getModule( _psiClass ), _psiClass.getManager(), methodName, modifierList )
      .withMethodReturnType( _field.getType() )
      .withContainingClass( _psiClass )
      .withNavigationElement( _field );
  }

  private ManLightMethodBuilder addSetter( boolean propAbstract, boolean propFinal, PropOption propAccess )
  {
    ManLightMethodBuilder setter = makeSetter( propAbstract, propFinal, propAccess );
    setter.putCopyableUserData( PropertyInference.FIELD_TAG, SmartPointerManager.createPointer( _field ) );

    PsiMethod existingSetter = findExistingAccessor( setter );
    if( existingSetter != null )
    {
      _field.putCopyableUserData( PropertyInference.SETTER_TAG, SmartPointerManager.createPointer( existingSetter ) );
      existingSetter.putCopyableUserData( PropertyInference.FIELD_TAG, SmartPointerManager.createPointer( _field ) );

      verifyAccessor( propAbstract, propFinal, propAccess, existingSetter );

      return null;
    }
    else if( shouldCheck() && _psiClass.isInterface() &&
      _field.getModifierList() != null && _field.getModifierList().hasExplicitModifier( PsiModifier.STATIC ) &&
      !_field.hasInitializer() )
    {
      // interface: static non-initialized property MUST provide user-defined setter
      reportError( _field, MSG_MISSING_INTERFACE_STATIC_PROPERTY_ACCESSOR.get(
        _psiClass.getName(), getSetterName() + "(" + _field.getType().getCanonicalText() + ")", _field.getName() ) );
    }
    return setter;
  }

  private ManLightMethodBuilder makeSetter( boolean propAbstract, boolean propFinal, PropOption propAccess )
  {
    //noinspection ConstantConditions
    ManLightModifierListImpl modifierList = getGetterSetterModifiers( _field.getModifierList(), _psiClass.isInterface(), propAbstract, propFinal,
      _field.getModifierList().hasExplicitModifier( PsiModifier.STATIC ), propAccess );
    ManPsiElementFactory factory = ManPsiElementFactory.instance();
    String methodName = getSetterName();
    return factory.createLightMethod( ManProject.getModule( _psiClass ), _psiClass.getManager(), methodName, modifierList )
      .withParameter( "value", _field.getType() )
      .withMethodReturnType( PsiTypes.voidType() )
      .withContainingClass( _psiClass )
      .withNavigationElement( _field );
  }

  private void verifyAccessor( boolean propAbstract, boolean propFinal, PropOption propAccess, PsiMethod existingAccessor )
  {
    if( !shouldCheck() )
    {
      return;
    }

    if( propAbstract != existingAccessor.getModifierList().hasModifierProperty( PsiModifier.ABSTRACT ) )
    {
      if( !_psiClass.isInterface() )
      {
        reportError( _field, MSG_PROPERTY_METHOD_CONFLICT.get( _field.getName(), existingAccessor.getName(), "abstract" ) );
      }
    }

    if( propFinal != existingAccessor.getModifierList().hasModifierProperty( PsiModifier.FINAL ) )
    {
      reportError( _field, MSG_PROPERTY_METHOD_CONFLICT.get( _field.getName(), existingAccessor.getName(), "final" ) );
    }

    int accessModifier = propAccess == null
      ? getAccess( _field.getModifierList() )
      : propAccess.getModifier();

    //noinspection MagicConstant
    if( !existingAccessor.getModifierList().hasModifierProperty( ModifierMap.byValue( accessModifier ).getName() ) )
    {
      reportError( _field, MSG_PROPERTY_METHOD_CONFLICT.get( _field.getName(), existingAccessor.getName(),
        PropOption.fromModifier( accessModifier ).name() ) );
    }

    checkStatic( existingAccessor );
  }

  private PsiMethod findExistingAccessor( PsiMethod accessor )
  {
    outer:
    for( PsiMethod m : _psiClass.getOwnMethods() )
    {
      if( accessor.getName().equals( m.getName() ) &&
        accessor.getParameterList().getParametersCount() == m.getParameterList().getParametersCount() )
      {
        PsiParameterList accessorParams = accessor.getParameterList();
        PsiParameterList mParams = m.getParameterList();
        for( int i = 0; i < accessorParams.getParametersCount(); i++ )
        {
          PsiParameter accessorParam = accessorParams.getParameter( i );
          PsiParameter treeParam = mParams.getParameter( i );
          if( accessorParam == null || treeParam == null ||
            !isSameType( m, _field.getName(), accessorParam.getType(), treeParam.getType() ) )
          {
            continue outer;
          }
        }
        // method already exists
        return m;
      }
    }
    return null;
  }

  private PsiMethod checkStatic( PsiMethod accessor )
  {
    if( accessor == null )
    {
      return null;
    }
    PsiModifierList fieldMods = _field.getModifierList();
    if( fieldMods == null )
    {
      return accessor;
    }
    boolean isPropStatic = fieldMods.hasExplicitModifier( PsiModifier.STATIC );
    boolean isMethodStatic = accessor.getModifierList().hasExplicitModifier( PsiModifier.STATIC );
    if( isPropStatic != isMethodStatic )
    {
      reportError( _field,
        (isMethodStatic ? MSG_STATIC_MISMATCH : MSG_NONSTATIC_MISMATCH).get( accessor.getName(), _field.getName() ) );
      accessor = null;
    }
    return accessor;
  }

  private void verifyPropertyOverride( PsiAnnotation var, PsiAnnotation val, PsiAnnotation get, PsiAnnotation set )
  {
    if( !shouldCheck() )
    {
      return;
    }

    boolean readableProperty = var != null || val != null || get != null;
    boolean writableProperty = var != null || set != null;

    boolean isOverride = _field.getAnnotation( override.class.getTypeName() ) != null;
    if( isOverride )
    {
      if( isStatic( _field ) )
      {
        reportError( _field, PropIssueMsg.MSG_DOES_NOT_OVERRIDE_ANYTHING.get( _field.getName() ) );
      }
      else if( readableProperty )
      {
        PsiMethod superReadable = getSuperReadable();
        if( superReadable == null )
        {
          if( !writableProperty )
          {
            reportError( _field, PropIssueMsg.MSG_DOES_NOT_OVERRIDE_ANYTHING.get( _field.getName() ) );
          }
          else
          {
            PsiMethod superWritable = getSuperWritable();
            if( superWritable == null )
            {
              reportError( _field, PropIssueMsg.MSG_DOES_NOT_OVERRIDE_ANYTHING.get( _field.getName() ) );
            }
            else if( isStatic( superWritable ) )
            {
              reportError( _field, MSG_CANNOT_OVERRIDE_STATIC.get( _field.getName(), superWritable.getName() ) );
            }
          }
        }
        else if( isStatic( superReadable ) )
        {
          reportError( _field, MSG_CANNOT_OVERRIDE_STATIC.get( _field.getName(), superReadable.getName() ) );
        }
        else
        {
          //todo: using erasure here is ghetto, what is needed is to find the retType as seen by _psiClass e.g., like with javac's Types.memberType() method
          PsiType retType = superReadable.getReturnType();
          if( retType == null ||
            !TypeConversionUtil.erasure( retType ).isAssignableFrom( TypeConversionUtil.erasure( _field.getType() ) ) )
          {
            reportError( _field.getTypeElement(), PropIssueMsg.MSG_PROPERTY_CLASH_RETURN.get( _field.getName(), _psiClass.getName(), superReadable.getContainingClass().getName() ) );
          }
        }
      }
      else if( writableProperty )
      {
        PsiMethod superWritable = getSuperWritable();
        if( superWritable == null )
        {
          reportError( _field, PropIssueMsg.MSG_DOES_NOT_OVERRIDE_ANYTHING.get( _field.getName() ) );
        }
        else if( isStatic( superWritable ) )
        {
          reportError( _field, MSG_CANNOT_OVERRIDE_STATIC.get( _field.getName(), superWritable.getName() ) );
        }
      }
    }
    else if( !isStatic( _field ) )
    {
      if( readableProperty )
      {
        PsiMethod superReadable = getSuperReadable();
        if( superReadable != null )
        {
          if( isStatic( superReadable ) )
          {
            reportError( _field, MSG_CANNOT_OVERRIDE_STATIC.get( _field.getName(), superReadable.getName() ) );
          }
          else
          {
            reportError( _field, PropIssueMsg.MSG_MISSING_OVERRIDE.get( _field.getName() ) );
          }
          return;
        }
      }

      if( writableProperty )
      {
        PsiMethod superWritable = getSuperWritable();
        if( superWritable != null )
        {
          if( isStatic( superWritable ) )
          {
            reportError( _field, MSG_CANNOT_OVERRIDE_STATIC.get( _field.getName(), superWritable.getName() ) );
          }
          else
          {
            reportError( _field, PropIssueMsg.MSG_MISSING_OVERRIDE.get( _field.getName() ) );
          }
        }
      }
    }
  }

  private boolean isStatic( PsiModifierListOwner elem )
  {
    return elem.getModifierList() != null && elem.getModifierList().hasModifierProperty( PsiModifier.STATIC );
  }

  private PsiMethod getSuperReadable()
  {
    ManLightMethodBuilder getter = makeGetter( false, false, PropOption.Public );
    MethodSignature sig = MethodSignatureUtil.createMethodSignature( getter.getName(), getter.getParameterList(), getter.getTypeParameterList(), PsiSubstitutor.EMPTY );
    return getSuperMethod( sig, _psiClass );
  }

  private PsiMethod getSuperWritable()
  {
    ManLightMethodBuilder setter = makeSetter( false, false, PropOption.Public );
    MethodSignature sig = MethodSignatureUtil.createMethodSignature( setter.getName(), setter.getParameterList(), setter.getTypeParameterList(), PsiSubstitutor.EMPTY );
    return getSuperMethod( sig, _psiClass );
  }

  private PsiMethod getSuperMethod( MethodSignature sig, PsiClass psiClass )
  {
    for( PsiClass iface : psiClass.getInterfaces() )
    {
      PsiMethod superSig = MethodSignatureUtil.findMethodInSuperClassBySignatureInDerived( _psiClass, iface, sig, true );
      if( superSig != null && !superSig.getModifierList().hasModifierProperty( PsiModifier.STATIC ) )
      {
        return superSig;
      }

      superSig = getSuperMethod( sig, iface );
      if( superSig != null )
      {
        return superSig;
      }
    }

    PsiClass superClass = psiClass.getSuperClass();
    if( superClass != null )
    {
      PsiMethod superSig = MethodSignatureUtil.findMethodInSuperClassBySignatureInDerived( _psiClass, superClass, sig, false );
      if( superSig != null )
      {
        return superSig;
      }
      return getSuperMethod( sig, superClass );
    }

    return null;
  }

  private boolean isSameType( PsiMethod m, String name, PsiType expected, PsiType found )
  {
    if( expected.equals( found ) )
    {
      return true;
    }
    else if( TypeConversionUtil.erasure( expected ).equals( TypeConversionUtil.erasure( found ) ) )
    {
      // We have to match the erasure of the setter parameter due to Java's generic type erasure, so warn about that
      reportWarning( m, MSG_SETTER_TYPE_CONFLICT.get( found, name, expected ) );
      return true;
    }
    return false;
  }

  private ManLightModifierListImpl getGetterSetterModifiers( @Nullable PsiModifierList modifierList, boolean isInterface,
                                                             boolean propAbstract, boolean propFinal, boolean propStatic,
                                                             PropOption propAccess )
  {
    //noinspection ConstantConditions
    long access = propAccess == null
      ? modifierList.hasModifierProperty( PsiModifier.PUBLIC ) ? PUBLIC : modifierList.hasModifierProperty( PsiModifier.PROTECTED ) ? PROTECTED : modifierList.hasModifierProperty( PsiModifier.PRIVATE ) ? PRIVATE : PUBLIC
      : propAccess.getModifier();
    if( isInterface && !propAbstract && !propStatic )
    {
      access |= Flags.DEFAULT;
    }
    access |= (propAbstract ? ABSTRACT : 0);
    access |= (propFinal ? FINAL : 0);
    access |= (propStatic ? STATIC : 0);
    return new ManLightModifierListImpl( _psiClass.getManager(), JavaLanguage.INSTANCE,
      ModifierMap.fromBits( access ).toArray( new String[0] ) );
  }

  private void reportError( PsiElement elem, String msg )
  {
    reportIssue( elem, HighlightSeverity.ERROR, msg );
  }

  private void reportWarning( PsiElement elem, String msg )
  {
    reportIssue( elem, HighlightSeverity.WARNING, msg );
  }

  private void reportIssue( PsiElement elem, HighlightSeverity severity, String msg )
  {
    if( !shouldCheck() )
    {
      return;
    }

    TextRange range = new TextRange( elem.getTextRange().getStartOffset(),
      elem.getTextRange().getEndOffset() );
    _holder.newAnnotation( severity, msg )
      .range( range )
      .create();
  }

  // does the accessor method have weaker access than the var field?
  private boolean isWeakerAccess( PropOption accessorOpt, int propAccess )
  {
    if( accessorOpt == null )
    {
      return false;
    }
    int accessorAccess = accessorOpt.getModifier();
    return accessorAccess == PUBLIC && propAccess != PUBLIC ||
      accessorAccess == PROTECTED && (propAccess == 0 || propAccess == PRIVATE) ||
      accessorAccess == 0 && propAccess == PRIVATE;
  }

  private boolean hasOption( List<JvmAnnotationAttribute> args, PropOption option )
  {
    if( args == null )
    {
      return false;
    }
    return args.stream().anyMatch( e -> isOption( option, e ) );
  }

  private PropOption getAccess( List<JvmAnnotationAttribute> args )
  {
    if( _psiClass.isInterface() )
    {
      // generated methods are always public in interfaces
      return PropOption.Public;
    }
    return hasOption( args, PropOption.Public )
      ? PropOption.Public
      : hasOption( args, PropOption.Protected )
      ? PropOption.Protected
      : hasOption( args, PropOption.Package )
      ? PropOption.Package
      : hasOption( args, PropOption.Private )
      ? PropOption.Private
      : null;
  }

  static int getAccess( PsiModifierList modifierList )
  {
    return getAccess( modifierList, true );
  }
  static int getAccess( PsiModifierList modifierList, boolean publicDefault )
  {
    if( modifierList.hasModifierProperty( PsiModifier.PUBLIC ) )
    {
      return PUBLIC;
    }
    if( modifierList.hasModifierProperty( PsiModifier.PROTECTED ) )
    {
      return PROTECTED;
    }
    if( modifierList.hasModifierProperty( PsiModifier.PRIVATE ) )
    {
      return PRIVATE;
    }
    return publicDefault ? PUBLIC : 0;
  }

  private boolean isOption( PropOption option, JvmAnnotationAttribute e )
  {
    if( e instanceof PsiNameValuePair )
    {
      JvmAnnotationAttributeValue value = e.getAttributeValue();
      if( value instanceof JvmAnnotationEnumFieldValue )
      {
        return Objects.equals( ((JvmAnnotationEnumFieldValue)value).getFieldName(), option.name() );
      }
    }
    return false;
  }

  private boolean isAbstract()
  {
    //noinspection ConstantConditions
    if( _psiClass.isInterface() &&
      !_field.getModifierList().hasExplicitModifier( PsiModifier.STATIC ) &&
      !_field.hasInitializer() )
    {
      // non-static, non-default method is abstract in interface
      return true;
    }
    else
    {
      // abstract class can have abstract methods
      //noinspection ConstantConditions
      return _field.getModifierList().hasModifierProperty( PsiModifier.ABSTRACT );
    }
  }

  private String getGetterName( @SuppressWarnings( "SameParameterValue" ) boolean isOk )
  {
    String name = _field.getName();
    if( isOk && PsiTypes.booleanType().equals( _field.getType() ) )
    {
      if( startsWithIs( name ) )
      {
        return name;
      }
      return "is" + ManStringUtil.capitalize( name );
    }
    return "get" + ManStringUtil.capitalize( name );
  }

  private String getSetterName()
  {
    return "set" + ManStringUtil.capitalize( _field.getName() );
  }

  private boolean startsWithIs( String name )
  {
    return name.length() > 2 && name.startsWith( "is" ) && Character.isUpperCase( name.charAt( 2 ) );
  }
}

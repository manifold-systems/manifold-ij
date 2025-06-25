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
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import manifold.api.gen.*;
import manifold.ext.params.ParamsIssueMsg;
import manifold.ext.params.rt.params;
import manifold.ij.core.ManProject;
import manifold.ij.psi.ManExtensionMethodBuilder;
import manifold.ij.psi.ManLightParameterImpl;
import manifold.rt.api.util.ManStringUtil;
import manifold.util.ReflectUtil;
import org.jetbrains.annotations.NotNull;

import javax.lang.model.type.NullType;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static manifold.ij.extensions.ManParamsAugmentProvider.hasInitializer;
import static manifold.ij.util.ManPsiGenerationUtil.makePsiMethod;
import static manifold.ij.util.ManPsiGenerationUtil.plantMethodInPsiClass;
import static manifold.ij.util.ManPsiGenerationUtil.plantInnerClassInPsiClass;

class ParamsMaker
{
  private final AnnotationHolder _holder;
  private final LinkedHashMap<String, PsiMember> _augFeatures;
  private final PsiMethod _psiMethod;
  private final PsiExtensibleClass _psiClass;

  static void generateMethod( PsiMethod method, PsiExtensibleClass psiClass, LinkedHashMap<String, PsiMember> augFeatures )
  {
    new ParamsMaker( method, psiClass, null, augFeatures ).generateOrCheckMethod();
  }

  static void checkMethod( PsiMethod method, PsiExtensibleClass psiClass, AnnotationHolder holder  )
  {
    new ParamsMaker( method, psiClass, holder, null ).generateOrCheckMethod();
  }

  static void generateParamsClass( PsiMethod method, PsiExtensibleClass psiClass, LinkedHashMap<String, PsiMember> augFeatures )
  {
    new ParamsMaker( method, psiClass, null, augFeatures ).generateOrCheckParamsClass();
  }

  static void checkParamsClass( PsiMethod method, PsiExtensibleClass psiClass, AnnotationHolder holder )
  {
    new ParamsMaker( method, psiClass, holder, null ).generateOrCheckParamsClass();
  }

  private ParamsMaker( PsiMethod method, PsiExtensibleClass psiClass, LinkedHashMap<String, PsiMember> augFeatures )
  {
    this( method, psiClass, null, augFeatures );
  }

  private ParamsMaker( PsiMethod method, PsiExtensibleClass psiClass, AnnotationHolder holder, LinkedHashMap<String, PsiMember> augFeatures )
  {
    _psiMethod = method;
    _psiClass = psiClass;
    _holder = holder;
    _augFeatures = augFeatures;
  }

  private void generateOrCheckParamsClass()
  {
    PsiClass paramsClass = makeParamsClass();
    if( paramsClass == null )
    {
      return;
    }
    if( _augFeatures != null )
    {
      _augFeatures.put( paramsClass.getName(), paramsClass );
    }
  }

  private void generateOrCheckMethod()
  {
    PsiClass paramsClass = null;
    String typeName = getTypeName();
    for( PsiClass innerClass : _psiClass.getInnerClasses() )
    {
      if( innerClass.getName() != null && innerClass.getName().equals( typeName ) )
      {
        paramsClass = innerClass;
        break;
      }
    }

    if( !hasOptionalParams() )
    {
      // the method doesn't have optional parameters
      return;
    }

    if( paramsClass != null )
    {
      PsiMethod forwardingMeth = makeParamsMethod( paramsClass );
      if( _augFeatures != null )
      {
        _augFeatures.put( typeName + "_1", forwardingMeth );
      }
    }

    // always generate telescoping methods when at least one optional param exists
    List<PsiMethod> telescopeMethods = makeTelescopeMethods();
    if( _augFeatures != null )
    {
      for( int i = 0; i < telescopeMethods.size(); i++ )
      {
        _augFeatures.put( typeName + "_1_" + (i + 1), telescopeMethods[i] );
      }
    }

    checkInitializers();
  }

  private boolean hasOptionalParams()
  {
    return hasOptionalParams( _psiMethod );
  }
  public static boolean hasOptionalParams( PsiMethod psiMethod )
  {
    for( PsiParameter param : psiMethod.getParameterList().getParameters() )
    {
      if( hasInitializer( param ) )
      {
        return true;
      }
    }
    return false;
  }

  public static Key<SmartPsiElementPointer<PsiParameter>> KEY_EXPR_PARAM_PARENT = Key.create( "KEY_EXPR_PARAM_PARENT" );
  private void checkInitializers()
  {
    if( !shouldCheck() )
    {
      return;
    }

    for( PsiParameter param: _psiMethod.getParameterList().getParameters() )
    {
      String initializer = getInitializer( param );
      if( initializer != null && !initializer.isEmpty() && param.getTypeElement() != null )
      {
        PsiType paramType = param.getType();
        if( paramType.isValid() )
        {
          PsiExpression expr = JavaPsiFacade.getElementFactory( _psiClass.getProject() ).createExpressionFromText( initializer, param );
          expr.putCopyableUserData( KEY_EXPR_PARAM_PARENT, SmartPointerManager.createPointer( param ) );
          PsiType initType = expr.getType();
          if( initType != null && initType.isValid() && !TypeConversionUtil.isAssignable( paramType, initType ) )
          {
            reportIssue( param, HighlightSeverity.ERROR, "Incompatible types: '" + initType.getPresentableText() +
              "' cannot be converted to '" + paramType.getPresentableText() + "'", getInitializerRange( param ) );;
          }
        }
      }
    }
  }

  private @NotNull String getTypeName()
  {
    return getTypeName( _psiMethod );
  }

  public static String getTypeName( PsiMethod originalMethod )
  {
    String typeName = originalMethod.isConstructor() ? "constructor" : originalMethod.getName();
    return "$" + typeName + "_" + Arrays.stream( originalMethod.getParameterList().getParameters() )
      .filter( e -> getInitializer( e ) == null ).map( e -> "_" + e.getName() ).reduce( "", ( a, b ) -> a + b );
  }

  // Make a method to forward the passed in tuple values
  private PsiMethod makeParamsMethod( PsiClass paramsClass )
  {
    SrcClass srcClass = new SrcClass( _psiClass.getQualifiedName(), _psiClass.isInterface ? AbstractSrcClass.Kind.Interface : AbstractSrcClass.Kind.Class );
    StubBuilder stubBuilder = new StubBuilder();
    SrcMethod srcMethod = stubBuilder.makeMethod( srcClass, _psiMethod );

    // mark with @params to facilitate excluding this method from code completion menus
    srcMethod.addAnnotation( params.class.getTypeName() );

    // replace target method's params with paramsClass type
    srcMethod.getParameters().clear();
    SrcType paramType = stubBuilder.makeSrcType( JavaPsiFacade.getElementFactory( _psiClass.getProject() ).createType( paramsClass, PsiSubstitutor.EMPTY ) );
    srcMethod.addParam( "args", paramType );
    SrcStatementBlock block = new SrcStatementBlock();
    if( _psiMethod.isConstructor() )
    {
      // add a throws statement to prevent "might not be initialized" errors relating to any final fields in the class
      for( PsiField field : _psiClass.getFields() )
      {
        if( field.getInitializer() == null && field.getModifierList() != null &&
          field.getModifierList().hasExplicitModifier( PsiModifier.FINAL ) )
        {
          block.addStatement( new SrcRawStatement( block )
            .rawText( "throw new RuntimeException();" ) );
          break;
        }
      }
    }
    srcMethod.body( block );

    PsiMethod paramsMethod = makePsiMethod( srcMethod, _psiClass );
    //noinspection UnnecessaryLocalVariable
    PsiMethod plantedMethod = plantMethodInPsiClass( ManProject.getModule( _psiClass ), paramsMethod, _psiClass, _psiMethod, _psiMethod.isConstructor() );
    return plantedMethod;
  }

  //
  private List<PsiMethod> makeTelescopeMethods( )
  {
    List<PsiParameter> reqParams = new ArrayList<>();
    List<PsiParameter> optParams = new ArrayList<>();
    for( PsiParameter p: _psiMethod.getParameterList().getParameters() )
    {
      if( getInitializer( p ) == null )
      {
        reqParams.add( p );
      }
      else
      {
        optParams.add( p );
      }
    }

    // start with a method having all the required params and forwarding all default param values,
    // end with having all the optional params but the last one as required params (since the original method has all the params as required)
    List<PsiMethod> result = new ArrayList<>();
    for( int i = 0; i < optParams.size(); i++ )
    {
      PsiMethod telescopeMethod = makeTelescopeMethod( reqParams, optParams, i );
      if( telescopeMethod != null && !methodExists( telescopeMethod ) )
      {
        result.add( telescopeMethod );
      }
    }
    return result;
  }

  private boolean methodExists( PsiMethod patternMethod )
  {
    List<PsiMethod> methodsByName = _psiClass.getOwnMethods().stream()
      .filter( m -> m.getName().equals( patternMethod.getName() ) )
      .toList();
    if( methodsByName.isEmpty() )
    {
      return false;
    }
    for( PsiMethod method : methodsByName )
    {
      if( MethodSignatureUtil.areParametersErasureEqual( patternMethod, method ) )
      {
        return true;
      }
    }
    return false;
  }

  private PsiMethod makeTelescopeMethod( List<PsiParameter> reqParams, List<PsiParameter> optParams, int optParamsInSig )
  {
    SrcClass srcClass = new SrcClass( _psiClass.getQualifiedName(), _psiClass.isInterface ? AbstractSrcClass.Kind.Interface : AbstractSrcClass.Kind.Class );
    StubBuilder stubBuilder = new StubBuilder();

    SrcMethod srcMethod = stubBuilder.makeMethod( srcClass, _psiMethod );

    // mark with @params to facilitate excluding this method from code completion menus
    srcMethod.addAnnotation( params.class.getTypeName() );

    srcMethod.getParameters().clear();
    for( PsiParameter reqParam: reqParams )
    {
      srcMethod.addParam( reqParam.getName(), new StubBuilder().makeSrcType( reqParam.getType() ) );
    }

    for( int i = 0; i < optParamsInSig; i++ )
    {
      PsiParameter optParam = optParams.get( i );
      PsiParameter @NotNull [] parameters = _psiMethod.getParameterList().getParameters();
      for( int j = 0; j < parameters.length; j++ )
      {
        PsiParameter parameter = parameters[j];
        if( parameter == optParam )
        {
          srcMethod.insertParam( optParam.getName(), new StubBuilder().makeSrcType( optParam.getType() ), j );
        }
      }
    }

    SrcStatementBlock block = new SrcStatementBlock();
    if( _psiMethod.isConstructor() )
    {
      block.addStatement( new SrcRawStatement( block ).rawText( "throw new RuntimeException();" ) );
    }
    srcMethod.body( block );

    PsiMethod paramsMethod = makePsiMethod( srcMethod, _psiClass );
    ManExtensionMethodBuilder plantedMethod = plantMethodInPsiClass( ManProject.getModule( _psiClass ), paramsMethod, _psiClass, _psiMethod, _psiMethod.isConstructor() );
    checkDuplication( plantedMethod );
    return plantedMethod;
  }

  private void checkDuplication( ManExtensionMethodBuilder plantedMethod )
  {
    if( !shouldCheck() )
    {
      return;
    }

    PsiMethod[] methods = _psiClass.findMethodsByName( plantedMethod.getName(), true );
    for( PsiMethod m : methods )
    {
      if( notFromSameMethod( m ) && MethodSignatureUtil.areParametersErasureEqual( m, plantedMethod ) )
      {
        ItemPresentation pres = _psiMethod.getPresentation();
        ItemPresentation otherPres = m.getPresentation();

        String paramsMethodDisplay = pres == null ? m.getName() : pres.getPresentableText();
        String otherDisplay = otherPres == null ? m.getName() : otherPres.getPresentableText();

        if( m.getContainingClass() == _psiClass )
        {
          if( m instanceof ManExtensionMethodBuilder )
          {
            // two optional params methods generate clashing telescoping methods (report that the two optional params method clash indirectly bc of this)

            PsiType[] erasedSig = MethodSignatureUtil.calcErasedParameterTypes( m.getSignature( PsiSubstitutor.EMPTY ) );
            m = ((ManExtensionMethodBuilder)m).getTargetMethod();
            otherPres = m.getPresentation();
            otherDisplay = otherPres == null ? m.getName() : otherPres.getPresentableText();
            String subSignature = "'(" + Arrays.stream( erasedSig ).map( p -> p.getPresentableText() ).collect( Collectors.joining( ", " ) ) + ")'";
            reportError( _psiMethod.getNameIdentifier(), ParamsIssueMsg.MSG_OPT_PARAM_METHOD_CLASHES_WITH_SUBSIG.get( paramsMethodDisplay, otherDisplay, subSignature ) );
          }
          else
          {
            // a telescoping method clashes with a physical method (report that the corresponding optional params method interferes)

            reportError( _psiMethod.getNameIdentifier(), ParamsIssueMsg.MSG_OPT_PARAM_METHOD_INDIRECTLY_CLASHES.get( paramsMethodDisplay, otherDisplay ) );
          }
        }
        else if( !m.isConstructor() &&
          !m.getModifierList().hasModifierProperty( PsiModifier.STATIC ) &&
          !m.getModifierList().hasModifierProperty( PsiModifier.PRIVATE ) &&
          !m.hasAnnotation( params.class.getTypeName() ) &&
          !plantedMethod.hasAnnotation( Override.class.getTypeName() ) )
        {
          // a telescoping method overrides a physical method in the super class (report that the corresponding optional params method interferes)

          reportWarning( _psiMethod.getNameIdentifier(), ParamsIssueMsg.MSG_OPT_PARAM_METHOD_INDIRECTLY_OVERRIDES
                  .get( paramsMethodDisplay, otherDisplay, (m.getContainingClass() == null ? "<unknown>": m.getContainingClass().getName() ) ) );
        }
      }
    }
  }

  private boolean notFromSameMethod( PsiMethod m )
  {
    return !(m instanceof ManExtensionMethodBuilder) || ((ManExtensionMethodBuilder)m).getTargetMethod() != _psiMethod;
  }

  private String makeTypeParamString( PsiTypeParameter[] typeParameters )
  {
    StringBuilder sb = new StringBuilder();
    for( PsiTypeParameter tp : typeParameters )
    {
      if( !sb.isEmpty() )
      {
        sb.append( "," );
      }
      sb.append( tp.getName() );
    }
    sb.insert( 0, '<' ).append( '>' );
    return sb.toString();
  }

  // Make a static inner class reflecting the parameters of the method having at least one default value,
  // this is the parameter type of the generated forwarding method,
  // which will also be the type of the tuple expression.
  //
  // // method having at least one default param value
  // String foo(String name, int age = 100) {...}
  //
  // // generated forwarding method, does nothing (throws) in IntelliJ, but forwards to above method when compiled
  // String foo($foo_name_opt$age<T> args) { throw new RuntimeException(); }
  //...
  // // generated paramsClass. note, this class is stubbed in IntelliJ, but retains param values when compiled
  // static class $foo_name_opt$age<T> {
  //   $foo(EncClass<T> foo, String name,  boolean isAge,int age) {
  //   }
  // }
  private static final Key<PsiType> PARAM_TYPE_KEY = Key.create( "manifold_params_type" );
  private PsiClass makeParamsClass()
  {
    String name = getTypeName();

    SrcClass srcParent = new SrcClass( _psiClass.getQualifiedName(), _psiClass.isInterface ? AbstractSrcClass.Kind.Interface : AbstractSrcClass.Kind.Class );
    SrcClass srcClass = new SrcClass( name, srcParent, AbstractSrcClass.Kind.Class )
      .name( name )
      .modifiers( Modifier.PUBLIC | Modifier.STATIC )
      .addAnnotation( new SrcAnnotationExpression( params.class )
        .addArgument( new SrcArgument( new SrcRawExpression( "\"" +
          Arrays.stream( _psiMethod.getParameterList().getParameters() )
            .map( e -> "_" + (getInitializer( e ) == null ? "" : "opt$") + e.getName() )
            .reduce( "", (a,b) -> a+b ) + "\"" ) ) ) );
    addTypeParams( srcClass );

    SrcConstructor srcCtor = new SrcConstructor( srcClass )
      .modifiers( Modifier.PUBLIC );
    if( !_psiMethod.getModifierList().hasModifierProperty( PsiModifier.STATIC ) && !_psiMethod.isConstructor() )
    {
      // add Foo<T> parameter for context to solve owning type's type vars, so we can new up $foo type with diamond syntax
      String genParamType = _psiClass.getTypeParameters().length == 0
        ? _psiClass.getQualifiedName()
        : _psiClass.getQualifiedName() + makeTypeParamString( _psiClass.getTypeParameters() );

      if( genParamType == null )
      {
        return null;
      }
      srcCtor.addParam( "$" + ManStringUtil.uncapitalize( _psiClass.getName() ), new SrcType( genParamType ) );
    }
    for( PsiParameter param: _psiMethod.getParameterList().getParameters() )
    {
      if( getInitializer( param ) != null )
      {
        String isXxx = "\$is" + ManStringUtil.capitalize( param.getName() );
        srcCtor.addParam( isXxx, PsiTypes.booleanType().getName() );
      }

      PsiTypeElement typeElement = param.getTypeElement();
      try
      {
        String type = typeElement == null ? null : typeElement.getText() == null ? null : typeElement.getText();
        if( type == null || type.isEmpty() )
        {
          return null;
        }
        srcCtor.addParam( param.getName(), type);
      }
      catch( TypeNameParserException tnpe )
      {
        return null;
      }
    }
    srcCtor.body( "" );
    srcClass.addConstructor( srcCtor );

    StringBuilder sb = new StringBuilder();
    srcClass.render( sb, 0 );
    PsiClass classFromText = JavaPsiFacade.getElementFactory( _psiClass.getProject() )
      .createClassFromText( sb.toString(), _psiMethod );
    PsiClass innerPsiClass = classFromText.getInnerClasses()[0];

    PsiClass paramsClass = plantInnerClassInPsiClass( ManProject.getModule( _psiClass ), innerPsiClass, _psiClass, _psiMethod );
    setLazyParameterTypes( paramsClass, innerPsiClass );
    return paramsClass;
  }

  // the ctor param types won't resolve unqualified type names bc the params class does have access to the parent class' imports,
  // we can't resolve the type and use the resulting text bc resolving the type now will cause a stackoveflow exception wrt adding augments,
  // therefore we need to resolve the type lazily
  private void setLazyParameterTypes( PsiClass paramsClass, PsiClass innerPsiClass )
  {
    PsiMethod paramsCtor = paramsClass.getConstructors()[0];
    PsiParameter[] parameters = paramsCtor.getParameterList().getParameters();
    for( PsiParameter psiParam: _psiMethod.getParameterList().getParameters() )
    {
      PsiType psiParamType = psiParam.getType();
      if( psiParamType instanceof PsiPrimitiveType )
      {
        continue;
      }

      for( PsiParameter ctorParam : parameters )
      {
        if( ctorParam.getName().equals( psiParam.getName() ) )
        {
          ((ManLightParameterImpl)ctorParam).setTypeSupplier( () -> {
            PsiType cachedType = ctorParam.getUserData( PARAM_TYPE_KEY );
            if( cachedType == null )
            {
              cachedType = JavaPsiFacade.getElementFactory( _psiClass.getProject() )
                .createTypeFromText( psiParamType.getCanonicalText(), innerPsiClass );
              ctorParam.putUserData( PARAM_TYPE_KEY, cachedType );
            }
            return cachedType;
          } );

          break;
        }
      }
    }
  }

//  private PsiType useClassTypeParams( PsiClass paramsClass, PsiType type )
//  {
//    List<PsiTypeParameter> fromTypeParams = new ArrayList<>();
//    fromTypeParams.addAll( Arrays.asList( _psiMethod.getTypeParameters() ) );
//    fromTypeParams.addAll( Arrays.asList( _psiClass.getTypeParameters() ) );
//    PsiSubstitutor substitutor = JavaPsiFacade.getElementFactory( _psiClass.getProject() )
//      .createSubstitutor( createSubstitutorMap( fromTypeParams.toArray( new PsiTypeParameter[0] ), paramsClass.getTypeParameters() ) );
//    //noinspection UnnecessaryLocalVariable
//    PsiType substitutedType = substitutor.substitute( type );
//    return substitutedType;
//  }

  private void addTypeParams( SrcClass paramsClass )
  {
    PsiTypeParameter[] typeParameters = _psiMethod.getTypeParameters();
    for( PsiTypeParameter tp: typeParameters )
    {
      String tpText = tp.getText();
      if( tpText != null )
      {
        paramsClass.addTypeVar( new SrcType( tpText ) );
      }
    }
    if( !_psiMethod.getModifierList().hasModifierProperty( PsiModifier.STATIC ) )
    {
      for( PsiTypeParameter tp: _psiClass.getTypeParameters() )
      {
        String tpText = tp.getText();
        if( tpText != null )
        {
          paramsClass.addTypeVar( new SrcType( tpText ) );
        }
      }
    }
  }

  private static String getInitializer( PsiParameter param )
  {
    PsiElement idElem = param.getIdentifyingElement();
    if( idElem == null )
    {
      return null;
    }
    int startOffsetInParent = idElem.getStartOffsetInParent();
    String text = param.getText();
    int iEq = text.indexOf( '=', startOffsetInParent + idElem.getTextLength() );
    return iEq != -1 ? text.substring( iEq + 1 ).trim() : null;
  }

// following code was first pass at this where we generated a structural interface, since tuples can structurally satisfy a data interface
// but the limitations of structural assignability are too great, mostly generics are out the window, which is a show stopper
// Keeping this here for posterity, and for reference
//  private PsiMethod makeForwardingMethod( PsiClass structuralInterface )
//  {
//    ManLightMethodBuilderImpl paramMethod =
//      new ManLightMethodBuilderImpl( ManProject.getModule( _psiClass ), _psiClass.getManager(), _psiMethod.getName() );
//    paramMethod.withMethodReturnType( _psiMethod.getReturnType() );
//    paramMethod.withContainingClass( _psiClass );
//    paramMethod.withNavigationElement( _psiMethod );
//    PsiModifierList targetModList = _psiMethod.getModifierList();
//    for( ModifierMap m : ModifierMap.values() )
//    {
//      if( targetModList.hasExplicitModifier( m.getName() ) )
//      {
//         paramMethod.withModifier( m.getName() );
//      }
//    }
//
//    // Throws
//    for( PsiClassType t: _psiMethod.getThrowsList().getReferencedTypes() )
//    {
//      paramMethod.withException( t );
//    }
//
////    // Type params
////    for( PsiTypeParameter tp: _psiMethod.getTypeParameters() )
////    {
////      String tpName = tp.getName();
////      if( tpName != null )
////      {
////        PsiTypeParameter typeParameter = JavaPsiFacade.getElementFactory( _psiClass.getProject() ).createTypeParameter( tpName, tp.getExtendsListTypes() );
////        paramMethod.withTypeParameterDirect( typeParameter );
////      }
////    }
//
//    // Param
//    PsiTypeParameterList typeParameterList = structuralInterface.getTypeParameterList();
//    PsiClassType paramType;
//    if( typeParameterList != null && typeParameterList.getTypeParameters().length > 0 )
//    {
//      // using raw type for param since its type vars can't ever be inferred from the tuple type :/
////      paramType = PsiTypesUtil.getClassType( structuralInterface );
//      paramType = (PsiClassType)JavaPsiFacade.getElementFactory( _psiClass.getProject() ).createTypeFromText(
//        structuralInterface.getName() + makeTypeParamString( typeParameterList ), structuralInterface );
//    }
//    else
//    {
//      paramType = JavaPsiFacade.getElementFactory( _psiClass.getProject() ).createType( structuralInterface );
//    }
//
//    paramMethod.withParameter( "args", paramType );
//
//    return paramMethod;
//  }
//
//  // note we only need to generate the interface with getters matching the method parameters, since we have structural
//  // assignability builtin (see ManJavaClassSuperImpl)
//  private PsiClass makeStructuralInterface()
//  {
//    String name = getTypeName();
//
//    ManLightClassBuilder structInterface = new ManLightClassBuilder( _psiClass, name, true );
//    LightModifierList modifierList = structInterface.getModifierList();
//    modifierList.addModifier( PsiModifier.STATIC );
//    modifierList.addAnnotation( Structural.class.getTypeName() );
//    structInterface.setContainingClass( _psiClass );
//    structInterface.setNavigationElement( _psiMethod );
//    structInterface.setScope( _psiMethod );
//
////    // Type params
////    addTypeParams( structInterface );
//
//    // Methods (getters matching params)
//    PsiParameter[] methParams = _psiMethod.getParameterList().getParameters();
//    for( int i = 0; i < methParams.length; i++ )
//    {
//      PsiParameter methParam = methParams[i];
//
////      JavaPsiFacade.getElementFactory().createClass("")
//
//      String getterName = makeGetterName( methParam.getName(), methParam.getType() );
//      ManLightMethodBuilderImpl paramMethod =
//        new ManLightMethodBuilderImpl( ManProject.getModule( _psiClass ), _psiClass.getManager(), getterName );
////      PsiType retType = JavaPsiFacade.getElementFactory( _psiClass.getProject() ).createTypeFromText(
////        methParam.getTypeElement().getText(), _psiMethod );
////      paramMethod.withMethodReturnType( retType );
//      paramMethod.withMethodReturnType( RecursiveTypeVarEraser.eraseTypeVars( methParam.getTypeElement().getType(), _psiMethod ) );
//      paramMethod.withContainingClass( structInterface );
//      paramMethod.withNavigationElement( methParam );
//
//      // need explicit modifiers despite being interface
//      paramMethod.withModifier( PsiModifier.PUBLIC );
//      String initializer = getInitializer( methParam );
//      if( initializer != null && !initializer.isEmpty() )
//      {
//        paramMethod.withModifier( PsiModifier.DEFAULT );
//      }
//      else
//      {
//        // need explicit despite being interface
//        paramMethod.withModifier( PsiModifier.ABSTRACT );
//      }
//
//      structInterface.addMethod( paramMethod );
//    }
//
//    return structInterface;
//  }
//
//  private void addTypeParams( ManLightClassBuilder structInterface )
//  {
//    LightTypeParameterListBuilder typeParams = structInterface.getTypeParameterList();
//    PsiTypeParameter[] typeParameters = _psiMethod.getTypeParameters();
//    int tpIndex = 0;
//    for( PsiTypeParameter tp: typeParameters )
//    {
//      String tpName = tp.getName();
//      if( tpName != null )
//      {
//        typeParams.addParameter( new LightTypeParameterBuilder( tpName, structInterface, tpIndex++ ) );
////        PsiTypeParameter typeParameter = JavaPsiFacade.getElementFactory( _psiClass.getProject() ).createTypeParameter( tpName, tp.getExtendsListTypes() );
////        typeParams.addParameter( typeParameter );
//      }
//    }
//    if( !_psiMethod.getModifierList().hasModifierProperty( PsiModifier.STATIC ) )
//    {
//      for( PsiTypeParameter tp: _psiClass.getTypeParameters() )
//      {
//        if( tp.getName() != null )
//        {
//          typeParams.addParameter( new LightTypeParameterBuilder( tp.getName(), structInterface, tpIndex++ ) );
////          PsiTypeParameter typeParameter = JavaPsiFacade.getElementFactory( _psiClass.getProject() ).createTypeParameter( tp.getName(), new PsiClassType[]{} );
////          typeParams.addParameter( typeParameter );
//        }
//      }
//    }
//  }
//  private String makeGetterName( String rawName, PsiType type )
//  {
//    if( PsiTypes.booleanType().equals( type ) )
//    {
//      if( startsWithIs( rawName ) )
//      {
//        return rawName;
//      }
//      return "is" + ManStringUtil.capitalize( rawName );
//    }
//    return "get" + ManStringUtil.capitalize( rawName );
//  }
//
//  private boolean startsWithIs( String name )
//  {
//    return name.length() > 2 && name.startsWith( "is" ) && Character.isUpperCase( name.charAt( 2 ) );
//  }
//
//  private Map<PsiTypeParameter, PsiType> createSubstitutorMap( PsiTypeParameter[] from, PsiTypeParameter[] to )
//  {
//    Map<PsiTypeParameter, PsiType> map = new HashMap<>();
//    for( PsiTypeParameter tpFrom: from )
//    {
//      Arrays.stream( to )
//        .filter( toTp -> Objects.equals( toTp.getName(), tpFrom.getName() ) )
//        .forEach( toTp -> map.put( tpFrom, PsiTypesUtil.getClassType( toTp ) ) );
//    }
//    return map;
//  }

  private boolean shouldCheck()
  {
    return _holder != null;
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
    reportIssue( elem, severity, msg,
      new TextRange( elem.getTextRange().getStartOffset(), elem.getTextRange().getEndOffset() ) );
  }
  private void reportIssue( PsiElement elem, HighlightSeverity severity, String msg, TextRange range )
  {
    if( !shouldCheck() )
    {
      return;
    }

    _holder.newAnnotation( severity, msg )
      .range( range )
      .create();
  }

  private static TextRange getInitializerRange( PsiParameter param )
  {
    PsiElement idElem = param.getIdentifyingElement();
    if( idElem == null )
    {
      return new TextRange( param.getTextRange().getStartOffset(), param.getTextRange().getEndOffset() );
    }
    int startOffsetInParent = idElem.getStartOffsetInParent();
    String text = param.getText();
    int iEq = text.indexOf( '=', startOffsetInParent + idElem.getTextLength() );
    for( int i = iEq+1; i < text.length(); i++ )
    {
      if( !Character.isWhitespace( text.charAt( i ) ) )
      {
        return new TextRange( param.getTextRange().getStartOffset() + i, param.getTextRange().getEndOffset() );
      }
    }
    return new TextRange( param.getTextRange().getStartOffset(), param.getTextRange().getEndOffset() );
  }
}

package manifold.ij.extensions;

import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.search.GlobalSearchScope;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.Arrays;

import manifold.api.gen.SrcAnnotated;
import manifold.api.gen.SrcAnnotationExpression;
import manifold.api.gen.SrcArgument;
import manifold.api.gen.SrcClass;
import manifold.api.gen.SrcField;
import manifold.api.gen.SrcMethod;
import manifold.api.gen.SrcParameter;
import manifold.api.gen.SrcRawExpression;
import manifold.api.gen.SrcRawStatement;
import manifold.api.gen.SrcStatementBlock;
import manifold.api.gen.SrcType;
import manifold.ext.rt.api.Extension;
import manifold.ij.core.ManModule;
import manifold.ij.util.ComputeUtil;

/**
 */
public class StubBuilder
{
  public SrcClass make( String fqn, ManModule module )
  {
    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance( module.getIjProject() );
    PsiClass psiClass = javaPsiFacade.findClass( fqn, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope( module.getIjModule() ) );
    if( psiClass == null )
    {
      psiClass = javaPsiFacade.findClass( fqn, GlobalSearchScope.allScope( module.getIjProject() ) );
      if( psiClass == null )
      {
        return null;
      }
    }
    return makeSrcClass( fqn, psiClass, module );
  }

  public SrcClass makeSrcClass( String fqn, PsiClass psiClass, ManModule module )
  {
    SrcClass srcClass = new SrcClass( fqn, getKind( psiClass ) )
      .modifiers( getModifiers( psiClass.getModifierList() ) );
    for( PsiTypeParameter typeVar : psiClass.getTypeParameters() )
    {
      srcClass.addTypeVar( new SrcType( makeTypeVar( typeVar ) ) );
    }
    setSuperTypes( srcClass, psiClass );
    for( PsiMethod psiMethod : psiClass.getMethods() )
    {
      addMethod( srcClass, psiMethod );
    }
    for( PsiField psiField : psiClass.getFields() )
    {
      addField( srcClass, psiField );
    }
    for( PsiClass psiInnerClass : psiClass.getInnerClasses() )
    {
      addInnerClass( srcClass, psiInnerClass, module );
    }
    return srcClass;
  }

  private void setSuperTypes( SrcClass srcClass, PsiClass psiClass )
  {
    PsiClassType[] superTypes = psiClass.getExtendsListTypes();
    if( superTypes.length > 0 )
    {
      srcClass.superClass( makeSrcType( superTypes[0] ) );
    }
    for( PsiClassType superType : psiClass.getImplementsListTypes() )
    {
      srcClass.addInterface( makeSrcType( superType ) );
    }
  }

  private SrcType makeSrcType( PsiType type )
  {
    SrcType srcType;
    if( type instanceof PsiClassType )
    {
      srcType = new SrcType( ((PsiClassType)type).rawType().getCanonicalText() );
      for( PsiType typeParam : ((PsiClassType)type).getParameters() )
      {
        srcType.addTypeParam( makeSrcType( typeParam ) );
      }
    }
    else if( type instanceof PsiWildcardType )
    {
      srcType = new SrcType( "?" );
      PsiType bound = ((PsiWildcardType)type).getBound();
      if( bound != null )
      {
        srcType.setSuperOrExtends( ((PsiWildcardType)type).isExtends() ? "extends" : "super" );
        srcType.addBound( makeSrcType( bound ) );
      }
      return srcType;
    }
    else
    {
      srcType = new SrcType( type.getCanonicalText() );
    }
    addAnnotations( srcType, type.getAnnotations() );
    return srcType;
  }

  private SrcType makeSrcType( AnnotatedType type )
  {
    SrcType srcType;
    AnnotatedElement rawType = null;
    if( type instanceof AnnotatedParameterizedType )
    {
      AnnotatedParameterizedType annoType = (AnnotatedParameterizedType)type;
      rawType = (AnnotatedElement)((ParameterizedType)annoType.getType()).getRawType();
      srcType = new SrcType( ((Class<?>)rawType).getTypeName() );
      for( AnnotatedType typeParam : annoType.getAnnotatedActualTypeArguments() )
      {
        srcType.addTypeParam( makeSrcType( typeParam ) );
      }
    }
    else if( type instanceof AnnotatedWildcardType )
    {
      srcType = new SrcType( "?" );
      AnnotatedType[] bounds = ((AnnotatedWildcardType)type).getAnnotatedUpperBounds();
      boolean upper = true;
      if( bounds.length == 0 )
      {
        upper = false;
        bounds = ((AnnotatedWildcardType)type).getAnnotatedLowerBounds();
      }
      if( bounds.length > 0 )
      {
        srcType.setSuperOrExtends( upper ? "extends" : "super" );
        srcType.addBound( makeSrcType( bounds[0] ) );
      }
      return srcType;
    }
    else
    {
      srcType = new SrcType( type.getType().getTypeName() );
      rawType = (AnnotatedElement)type;
    }

    addAnnotations( srcType, rawType.getAnnotations() );
    return srcType;
  }

  public static String makeTypeVar( PsiTypeParameter typeVar )
  {
    StringBuilder sb = new StringBuilder();
    sb.append( typeVar.getName() );

    PsiClassType[] bounds = typeVar.getExtendsList().getReferencedTypes();
    if( bounds.length > 0 )
    {
      sb.append( " extends " );
      for( int i = 0; i < bounds.length; i++ )
      {
        if( i > 0 )
        {
          sb.append( " & " );
        }
        sb.append( bounds[i].getCanonicalText() );
      }
    }
    return sb.toString();
  }

  public static long getModifiers( PsiModifierList modifierList )
  {
    long modifiers = 0;
    if( modifierList.hasExplicitModifier( PsiModifier.ABSTRACT ) )
    {
      modifiers |= Modifier.ABSTRACT;
    }
    if( modifierList.hasExplicitModifier( PsiModifier.DEFAULT ) )
    {
      modifiers |= 0x80000000000L; //Flags.DEFAULT;
    }
    if( modifierList.hasExplicitModifier( PsiModifier.FINAL ) )
    {
      modifiers |= Modifier.FINAL;
    }
    if( modifierList.hasExplicitModifier( PsiModifier.PRIVATE ) )
    {
      modifiers |= Modifier.PRIVATE;
    }
    if( modifierList.hasExplicitModifier( PsiModifier.PROTECTED ) )
    {
      modifiers |= Modifier.PROTECTED;
    }
    if( modifierList.hasExplicitModifier( PsiModifier.PUBLIC ) )
    {
      modifiers |= Modifier.PUBLIC;
    }
    if( modifierList.hasExplicitModifier( PsiModifier.STATIC ) )
    {
      modifiers |= Modifier.STATIC;
    }
    if( modifierList.hasExplicitModifier( PsiModifier.SYNCHRONIZED ) )
    {
      modifiers |= Modifier.SYNCHRONIZED;
    }
    if( modifierList.hasExplicitModifier( PsiModifier.TRANSIENT ) )
    {
      modifiers |= Modifier.TRANSIENT;
    }
    if( modifierList.hasExplicitModifier( PsiModifier.VOLATILE ) )
    {
      modifiers |= Modifier.VOLATILE;
    }
    return modifiers;
  }

  private SrcClass.Kind getKind( PsiClass psiClass )
  {
    if( psiClass.isInterface() )
    {
      return SrcClass.Kind.Interface;
    }
    if( psiClass.isAnnotationType() )
    {
      return SrcClass.Kind.Annotation;
    }
    if( psiClass.isEnum() )
    {
      return SrcClass.Kind.Enum;
    }
    return SrcClass.Kind.Class;
  }

  private void addInnerClass( SrcClass srcClass, PsiClass psiClass, ManModule module )
  {
    SrcClass innerClass = makeSrcClass( psiClass.getQualifiedName(), psiClass, module );
    srcClass.addInnerClass( innerClass );
  }

  private void addField( SrcClass srcClass, PsiField field )
  {
    SrcField srcField = new SrcField( field.getName(), makeSrcType( field.getType() ) );
    srcField.modifiers( getModifiers( field.getModifierList() ) );
    if( Modifier.isFinal( (int)srcField.getModifiers() ) )
    {
      srcField.initializer( new SrcRawExpression( ComputeUtil.getDefaultValue( field.getType() ) ) );
    }
    srcClass.addField( srcField );
  }

  private void addMethod( SrcClass srcClass, PsiMethod method )
  {
    SrcMethod srcMethod = new SrcMethod( srcClass );
    addAnnotations( srcMethod, method );
    srcMethod.modifiers( getModifiers( method.getModifierList() ) );
    String name = method.getName();
    srcMethod.name( name );
    PsiClass containingClass = method.getContainingClass();
    boolean isExtensionClass = containingClass instanceof ClsClassImpl && containingClass.getAnnotation( Extension.class.getTypeName() ) != null;
    if( !method.isConstructor() )
    {
      SrcType returnType;
      if( isExtensionClass )
      {
        //todo: remove this after IJ fixes https://youtrack.jetbrains.com/issue/IDEA-247069
        returnType = makeReturnTypeFromClass( method, srcMethod, containingClass );
      }
      else
      {
        returnType = makeSrcType( method.getReturnType() );
      }
      srcMethod.returns( returnType );
    }
    for( PsiTypeParameter typeVar : method.getTypeParameters() )
    {
      srcMethod.addTypeVar( new SrcType( makeTypeVar( typeVar ) ) );
    }
    if( isExtensionClass ) //## todo: kill this whole if( isExtensions ) block after IJ fixes https://youtrack.jetbrains.com/issue/IDEA-247069
    {
      PsiParameter[] parameters = method.getParameterList().getParameters();
      Method m = findRawMethod( method, containingClass );
      for( int iParam = 0; iParam < parameters.length; iParam++ )
      {
        PsiParameter psiParam = parameters[iParam];
        try
        {
          SrcParameter srcParam = new SrcParameter( psiParam.getName(), makeSrcType( m.getAnnotatedParameterTypes()[iParam] ) );
          addAnnotations( srcParam, psiParam );
          srcMethod.addParam( srcParam );
        }
        catch( Exception e )
        {
          SrcParameter srcParam = new SrcParameter( psiParam.getName(), makeSrcType( psiParam.getType() ) );
          addAnnotations( srcParam, psiParam );
          srcMethod.addParam( srcParam );
        }
      }
    }
    else
    {
      for( PsiParameter param : method.getParameterList().getParameters() )
      {
        SrcParameter srcParam = new SrcParameter( param.getName(), makeSrcType( param.getType() ) );
        addAnnotations( srcParam, param );
        srcMethod.addParam( srcParam );
      }
    }
    for( PsiClassType throwType : method.getThrowsList().getReferencedTypes() )
    {
      srcMethod.addThrowType( makeSrcType( throwType ) );
    }
    srcMethod.body( new SrcStatementBlock()
      .addStatement(
        new SrcRawStatement()
          .rawText( "throw new RuntimeException();" ) ) );
    srcClass.addMethod( srcMethod );
  }

  private Method findRawMethod( PsiMethod method, PsiClass containingClass )
  {
    try
    {
      Class<?> cls = Class.forName( containingClass.getQualifiedName() );
      return cls.getDeclaredMethod( method.getName(), Arrays.stream( method.getParameters() )
        .map( e -> getRawClass( (PsiParameter)e ) ).toArray( i -> new Class<?>[i] ) );
    }
    catch( Throwable t )
    {
      return null;
    }
  }

  private Class<?> getRawClass( PsiParameter e )
  {
    try
    {
      PsiType type = e.getType();
      String typeName = type.getCanonicalText();
      if( type instanceof PsiPrimitiveType )
      {
        return Class.class.jailbreak().getPrimitiveClass( typeName );
      }
      int iLt = typeName.indexOf( '<' );
      if( iLt > 0 )
      {
        typeName = typeName.substring( 0, iLt );
      }
      return Class.forName( typeName );
    }
    catch( Exception ex )
    {
      return null;
    }
  }

  private SrcType makeReturnTypeFromClass( PsiMethod method, SrcMethod srcMethod, PsiClass containingClass )
  {
    try
    {
      Method m = findRawMethod( method, containingClass );
      return makeSrcType( m.getAnnotatedReturnType() );
    }
    catch( Exception e )
    {
      return makeSrcType( method.getReturnType() );
    }
  }

  private void addAnnotations( SrcAnnotated<?> srcAnnotated, PsiModifierListOwner annotated )
  {
    addAnnotations( srcAnnotated, annotated.getModifierList().getAnnotations() );
  }
  private void addAnnotations( SrcAnnotated<?> srcAnnotated, PsiAnnotation[] annotations )
  {
    if( annotations == null )
    {
      return;
    }

    for( PsiAnnotation psiAnno : annotations )
    {
      SrcAnnotationExpression annoExpr = new SrcAnnotationExpression( psiAnno.getQualifiedName() );
      for( PsiNameValuePair value : psiAnno.getParameterList().getAttributes() )
      {
        Object realValue = ComputeUtil.computeLiteralValue( value );
        SrcRawExpression expr = realValue == null ? new SrcRawExpression( null ) : new SrcRawExpression( realValue.getClass(), realValue );
        SrcArgument srcArg = new SrcArgument( expr ).name( value.getName() );
        annoExpr.addArgument( srcArg );
      }
      srcAnnotated.addAnnotation( annoExpr );
    }
  }
  private void addAnnotations( SrcAnnotated<?> srcAnnotated, Annotation[] annotations )
  {
    if( annotations == null )
    {
      return;
    }

    for( Annotation anno : annotations )
    {
      SrcAnnotationExpression annoExpr = new SrcAnnotationExpression( anno.annotationType() );
      for( Method m: anno.annotationType().getDeclaredMethods() )
      {
        Object realValue = m.invoke( anno );
        SrcRawExpression expr = realValue == null ? new SrcRawExpression( null ) : new SrcRawExpression( realValue.getClass(), realValue );
        SrcArgument srcArg = new SrcArgument( expr ).name( m.getName() );
        annoExpr.addArgument( srcArg );
      }
      srcAnnotated.addAnnotation( annoExpr );
    }
  }
}

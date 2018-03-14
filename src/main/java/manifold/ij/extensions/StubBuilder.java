package manifold.ij.extensions;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.tree.java.PsiNameValuePairImpl;
import com.intellij.psi.search.GlobalSearchScope;
import java.lang.reflect.Modifier;
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
import manifold.ij.core.ManModule;

/**
 */
public class StubBuilder
{
  public SrcClass make( String fqn, ManModule module )
  {
     JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance( module.getIjProject() );
     PsiClass psiClass = javaPsiFacade.findClass( fqn, GlobalSearchScope.moduleScope( module.getIjModule() ) );
     if( psiClass == null )
     {
       psiClass = javaPsiFacade.findClass( fqn, GlobalSearchScope.allScope( module.getIjProject() ) );
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
    else
    {
      srcType = new SrcType( type.getCanonicalText() );
    }
    return srcType;
  }

  public static String makeTypeVar( PsiTypeParameter typeVar )
  {
    StringBuilder sb = new StringBuilder();
    sb.append( typeVar.getName() );

    PsiJavaCodeReferenceElement[] bounds = typeVar.getExtendsList().getReferenceElements();
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
      srcField.initializer( new SrcRawExpression( getValueForType( field.getType() ) ) );
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
    if( !method.isConstructor() )
    {
      srcMethod.returns( makeSrcType( method.getReturnType() ) );
    }
    for( PsiTypeParameter typeVar : method.getTypeParameters() )
    {
      srcMethod.addTypeVar( new SrcType( makeTypeVar( typeVar ) ) );
    }
    for( PsiParameter param : method.getParameterList().getParameters() )
    {
      SrcParameter srcParam = new SrcParameter( param.getName(), makeSrcType( param.getType() ) );
      addAnnotations( srcParam, param );
      srcMethod.addParam( srcParam );
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

  private void addAnnotations( SrcAnnotated<?> srcAnnotated, PsiModifierListOwner annotated )
  {
    for( PsiAnnotation psiAnno : annotated.getModifierList().getAnnotations() )
    {
      SrcAnnotationExpression annoExpr = new SrcAnnotationExpression( psiAnno.getQualifiedName() );
      for( PsiNameValuePair value : psiAnno.getParameterList().getAttributes() )
      {
        Object realValue = computeLiteralValue( value );
        SrcRawExpression expr = realValue == null ? new SrcRawExpression( null ) : new SrcRawExpression( realValue.getClass(), realValue );
        SrcArgument srcArg = new SrcArgument( expr ).name( value.getName() );
        annoExpr.addArgument( srcArg );
      }
      srcAnnotated.addAnnotation( annoExpr );
    }
  }

  private Object computeLiteralValue( PsiNameValuePair value )
  {
    Object literalValue = null;
    PsiAnnotationMemberValue detachedValue = value.getDetachedValue();
    if( detachedValue instanceof PsiReferenceExpression )
    {
      PsiElement resolve = ((PsiReferenceExpression)detachedValue).resolve();
      if( resolve instanceof PsiVariable )
      {
        literalValue = ((PsiVariable)resolve).computeConstantValue();
      }
    }
    if( literalValue == null )
    {
      if( detachedValue instanceof PsiLiteralExpression )
      {
        PsiType type = ((PsiLiteralExpression)detachedValue).getType();
        if( type instanceof PsiPrimitiveType )
        {
          return getPrimitiveValue( ((PsiPrimitiveType)type).getName(), value.getLiteralValue() );
        }
        else if( type instanceof PsiClassReferenceType )
        {
          int dims = type.getArrayDimensions();
          if( dims > 0 )
          {
            //## todo
          }
          else
          {
            return value.getLiteralValue();
          }
        }
      }
      //((PsiPrimitiveType)((PsiLiteralExpression)value.getDetachedValue()).getType()).getName()
      //((PsiClassReferenceType)((PsiLiteralExpression)value.getDetachedValue()).getType()).getReference().getQualifiedName()
      literalValue = value.getLiteralValue();
    }
    return literalValue == null ? null : literalValue.toString();
  }

  private Object getPrimitiveValue( String name, String literalValue )
  {
    switch( name )
    {
      case "boolean":
        return Boolean.valueOf( literalValue );
      case "char":
        return literalValue.charAt( 0 );
      case "byte":
        return Byte.valueOf( literalValue );
      case "short":
        return Short.valueOf( literalValue );
      case "int":
        return Integer.valueOf( literalValue );
      case "long":
        return Long.valueOf( literalValue );
      case "float":
        return Float.valueOf( literalValue );
      case "double":
        return Double.valueOf( literalValue );
      default:
        throw new IllegalStateException( "\"" + name + "\" is not a primitive type name" );
    }
  }

  private String getValueForType( PsiType type )
  {
    if( type instanceof PsiPrimitiveType )
    {
      if( type.getCanonicalText().equals( "boolean" ) )
      {
        return "false";
      }
      return "0";
    }
    return "null";
  }

}

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

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiJavaFileBaseImpl;
import com.intellij.psi.impl.source.tree.java.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import java.util.List;

import manifold.ext.rt.api.Jailbreak;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.core.ManPsiPostfixExpressionImpl;
import manifold.ij.core.ManPsiPrefixExpressionImpl;
import manifold.ij.psi.ManLightMethodBuilder;
import manifold.ij.template.psi.ManTemplateJavaFile;
import manifold.ij.util.ManPsiUtil;
import manifold.internal.javac.ManAttr;
import manifold.rt.api.util.ManClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


import static com.intellij.psi.impl.source.tree.ChildRole.OPERATION_SIGN;
import static manifold.ij.extensions.ManAugmentProvider.KEY_MAN_INTERFACE_EXTENSIONS;


/**
 * Unfortunately IJ doesn't provide a way to augment a type with interfaces, so we are stuck with suppressing errors
 */
public class ManHighlightInfoFilter implements HighlightInfoFilter
{
  /**
   * Override to filter errors related to type incompatibilities arising from a
   * manifold extension adding an interface to an existing classpath class (as opposed
   * to a source file).  Basically suppress "incompatible type errors" or similar
   * involving a structural interface extension.
   */
  @Override
  public boolean accept( @NotNull HighlightInfo hi, @Nullable PsiFile file )
  {
    if( file == null )
    {
      return true;
    }

    if( !ManProject.isManifoldInUse( file ) )
    {
      // Manifold jars are not used in the project
      return true;
    }

    if( hi.getDescription() == null )
    {
      return true;
    }

    //
    // Handle Warnings OR Errors...
    //

    if( filterComparedUsingEquals( hi, file ) )
    {
      return false;
    }

    if( filterCanBeReplacedWith( hi, file ) )
    {
      return false;
    }

    if( filterCastingStructuralInterfaceWarning( hi, file ) )
    {
      return false;
    }

    if( filterArrayIndexIsOutOfBounds( hi, file ) )
    {
      return false;
    }

    if( filterTemplateUnnecessarySemicolon( hi, file ) )
    {
      return false;
    }

    if( filterCallToToStringOnArray( hi, file ) )
    {
      return false;
    }

    if( hi.getSeverity() != HighlightSeverity.ERROR )
    {
      return true;
    }


    //
    // Handle only Errors...
    //

    if( filterUnhandledCheckedExceptions( hi, file ) )
    {
      return false;
    }

    PsiElement firstElem = file.findElementAt( hi.getStartOffset() );
    if( firstElem == null )
    {
      return true;
    }

    if( filterAmbiguousMethods( hi, firstElem ) )
    {
      return false;
    }

    PsiElement elem = firstElem.getParent();
    if( elem == null )
    {
      return true;
    }

    if( filterIllegalEscapedCharDollars( hi, firstElem, elem ) )
    {
      return false;
    }

    if( filterCannotAssignToFinalIfJailbreak( hi, firstElem ) )
    {
      return false;
    }

    if( filterUnclosedComment( hi, firstElem ) )
    {
      return false;
    }

    if( filterOperatorMinusCannotBeApplied( hi, elem, firstElem ) )
    {
      return false;
    }

    if( filterPrefixExprCannotBeApplied( hi, elem, firstElem ) ||
        filterPostfixExprCannotBeApplied( hi, elem, firstElem ) )
    {
      return false;
    }

    if( filterIncompatibleTypesWithCompoundAssignmentOperatorOverload( hi, elem, firstElem ) )
    {
      return false;
    }

    if( filterOperatorCannotBeAppliedToWithCompoundAssignmentOperatorOverload( hi, elem, firstElem ) )
    {
      return false;
    }

    // handle indexed operator overloading
    if( filterArrayTypeExpected( hi, elem, firstElem ) )
    {
      return false;
    }
    if( filterVariableExpected( hi, elem, firstElem ) )
    {
      return false;
    }

    if( filterAnyAnnoTypeError( hi, elem, firstElem ) )
    {
      return false;
    }

    if( filterIncompatibleReturnType( hi, elem, firstElem ) )
    {
      return false;
    }

    if( filterForeachExpressionErrors( hi, elem, firstElem ) )
    {
      return false;
    }

    if( filterInnerClassReferenceError( hi, elem, firstElem ) )
    {
      return false;
    }

    //##
    //## structural interface extensions cannot be added to the psiClass, so for now we suppress "incompatible type
    //## errors" or similar involving a structural interface extension :(
    //##
    Boolean x = acceptInterfaceError( hi, firstElem, elem );
    if( x != null )
    {
      return x;
    }

    return true;
  }

  private boolean filterTemplateUnnecessarySemicolon( HighlightInfo hi, PsiFile file )
  {
    String description = hi.getDescription();
    return (file instanceof ManTemplateJavaFile || !(file instanceof PsiJavaFileBaseImpl)) &&
      description.contains( "Unnecessary semicolon" );
  }

  // Operator overloading: Filter warning messages like "Number objects are compared using '==', not 'equals()'"
  private boolean filterComparedUsingEquals( HighlightInfo hi, PsiFile file )
  {
    if( hi != null )
    {
      String description = hi.getDescription();
      if( description != null &&
          (description.contains( "compared using '=='" ) ||
           description.contains( "compared using '!='" )) )
      {
        PsiElement firstElem = file.findElementAt( hi.getStartOffset() );
        if( firstElem != null )
        {
          PsiElement parent = firstElem.getParent();
          if( parent instanceof PsiBinaryExpressionImpl )
          {
            PsiType type = ManJavaResolveCache.getTypeForOverloadedBinaryOperator( (PsiBinaryExpression)parent );
            return type != null;
          }
        }
      }
    }
    return false;
  }

  // Filter warning messages like "1 Xxx can be replaced with Xxx" where '1 Xxx' is a binding expression
  private boolean filterCanBeReplacedWith( HighlightInfo hi, PsiFile file )
  {
    if( hi != null )
    {
      String description = hi.getDescription();
      if( description != null && description.contains( "can be replaced with" ) )
      {
        PsiElement firstElem = file.findElementAt( hi.getStartOffset() );
        while( !(firstElem instanceof PsiBinaryExpressionImpl)  )
        {
          if( firstElem == null )
          {
            return false;
          }
          firstElem = firstElem.getParent();
        }

        // a null operator indicates a biding expression
        PsiElement child = ((PsiBinaryExpressionImpl)firstElem).findChildByRoleAsPsiElement( OPERATION_SIGN );
        return child == null;
      }
    }
    return false;
  }

  // Filter warning messages like "1 Xxx can be replaced with Xxx" where '1 Xxx' is a binding expression
  private boolean filterCastingStructuralInterfaceWarning( HighlightInfo hi, PsiFile file )
  {
    if( hi != null )
    {
      String description = hi.getDescription();
      if( description != null && description.startsWith( "Casting '" ) &&
          description.endsWith( "will produce 'ClassCastException' for any non-null value" ) )
      {
        PsiTypeElement typeElem = findTypeElement( file.findElementAt( hi.getStartOffset() ) );
        return isStructuralType( typeElem );
      }
    }
    return false;
  }

  // allow negation operator overload
  private boolean filterOperatorMinusCannotBeApplied( HighlightInfo hi, PsiElement elem, PsiElement firstElem )
  {
    return firstElem instanceof PsiJavaToken && ((PsiJavaToken)firstElem).getTokenType() == JavaTokenType.MINUS &&
           elem instanceof ManPsiPrefixExpressionImpl &&
           hi.getDescription().contains( "Operator '-' cannot be applied to" ) &&
           ((ManPsiPrefixExpressionImpl)elem).getTypeForUnaryMinusOverload() != null;
  }

  // allow unary inc/dec operator overload
  private boolean filterPrefixExprCannotBeApplied( HighlightInfo hi, PsiElement elem, PsiElement firstElem )
  {
    return elem instanceof ManPsiPrefixExpressionImpl &&
           (hi.getDescription().contains( "Operator '-' cannot be applied to" ) ||
            hi.getDescription().contains( "Operator '--' cannot be applied to" ) ||
            hi.getDescription().contains( "Operator '++' cannot be applied to" )) &&
           ((ManPsiPrefixExpressionImpl)elem).isOverloaded();
  }
  private boolean filterPostfixExprCannotBeApplied( HighlightInfo hi, PsiElement elem, PsiElement firstElem )
  {
    return isInOverloadPostfixExpr( elem ) &&
           (hi.getDescription().contains( "Operator '-' cannot be applied to" ) ||
            hi.getDescription().contains( "Operator '--' cannot be applied to" ) ||
            hi.getDescription().contains( "Operator '++' cannot be applied to" ));
  }

  private boolean isInOverloadPostfixExpr( PsiElement elem )
  {
    if( elem == null )
    {
      return false;
    }
    if( elem instanceof ManPsiPostfixExpressionImpl )
    {
      return ((ManPsiPostfixExpressionImpl)elem).isOverloaded();
    }
    return isInOverloadPostfixExpr( elem.getParent() );
  }

  // allow compound assignment operator overloading
  private boolean filterIncompatibleTypesWithCompoundAssignmentOperatorOverload( HighlightInfo hi, PsiElement elem, PsiElement firstElem )
  {
    return elem.getParent() instanceof PsiAssignmentExpressionImpl &&
           hi.getDescription().contains( "Incompatible types" ) &&
           ManJavaResolveCache.getTypeForOverloadedBinaryOperator( (PsiExpression)elem.getParent() ) != null;
  }
  private boolean filterOperatorCannotBeAppliedToWithCompoundAssignmentOperatorOverload( HighlightInfo hi, PsiElement elem, PsiElement firstElem )
  {
    return firstElem.getParent() instanceof PsiAssignmentExpressionImpl &&
           hi.getDescription().contains( "' cannot be applied to " ) &&  // eg. "Operator '+' cannot be applied to 'java.math.BigDecimal'"
           ManJavaResolveCache.getTypeForOverloadedBinaryOperator( (PsiExpression)firstElem.getParent() ) != null;
  }

  // support indexed operator overloading
  private boolean filterArrayTypeExpected( HighlightInfo hi, PsiElement elem, PsiElement firstElem )
  {
    PsiArrayAccessExpressionImpl arrayAccess;
    return (arrayAccess = getArrayAccessExpression( elem )) != null &&
      hi.getDescription().startsWith( "Array type expected" ) &&
      ManJavaResolveCache.getBinaryType( ManJavaResolveCache.INDEXED_GET,
        arrayAccess.getArrayExpression().getType(), arrayAccess.getIndexExpression().getType(), arrayAccess ) != null;
  }

  private PsiArrayAccessExpressionImpl getArrayAccessExpression( PsiElement elem )
  {
    if( elem == null )
    {
      return null;
    }

    if( elem instanceof PsiArrayAccessExpressionImpl )
    {
      return (PsiArrayAccessExpressionImpl)elem;
    }

    return getArrayAccessExpression( elem.getParent() );
  }

  private boolean filterVariableExpected( HighlightInfo hi, PsiElement elem, PsiElement firstElem )
  {
    PsiArrayAccessExpressionImpl arrayAccess = null;
    for( PsiElement csr = getArrayAccessExpression( elem ); csr instanceof PsiArrayAccessExpressionImpl; csr = csr.getParent() )
    {
      // use outermost index expression e.g., matrix[x][y] = 6
      arrayAccess = (PsiArrayAccessExpressionImpl)csr;
    }
    return arrayAccess != null &&
      hi.getDescription().startsWith( "Variable expected" ) &&
      arrayAccess.getIndexExpression() != null &&
      ManJavaResolveCache.getBinaryType( ManJavaResolveCache.INDEXED_SET,
        arrayAccess.getArrayExpression().getType(), arrayAccess.getIndexExpression().getType(), arrayAccess ) != null;
  }
  private boolean filterArrayIndexIsOutOfBounds( HighlightInfo hi, PsiFile file )
  {
    String description = hi.getDescription();
    if( description == null || !description.startsWith( "Array index is out of bounds" ) )
    {
      return false;
    }

    PsiArrayAccessExpressionImpl arrayAccess = getArrayAccessExpression( file.findElementAt( hi.getStartOffset() ) );
    if( arrayAccess == null )
    {
      return false;
    }
    return arrayAccess.getIndexExpression() != null &&
      ManJavaResolveCache.getBinaryType( ManJavaResolveCache.INDEXED_GET,
        arrayAccess.getArrayExpression().getType(), arrayAccess.getIndexExpression().getType(), arrayAccess ) != null;
  }

  private boolean filterAnyAnnoTypeError( HighlightInfo hi, PsiElement elem, PsiElement firstElem )
  {
    // for use with properties e.g., @val(annos = @Foo) String name;

    return elem instanceof PsiAnnotation &&
      hi.getDescription().startsWith( "Incompatible types" ) &&
      hi.getDescription().contains( manifold.rt.api.anno.any.class.getTypeName() );
  }

  private boolean filterIncompatibleReturnType( HighlightInfo hi, PsiElement elem, PsiElement firstElem )
  {
    // filter method override "incompatible return type" error involving 'auto'

    return elem.getText().equals( ManClassUtil.getShortClassName( ManAttr.AUTO_TYPE ) ) &&
      hi.getDescription().contains( "incompatible return type" );
  }

  private boolean filterInnerClassReferenceError( HighlightInfo hi, PsiElement elem, PsiElement firstElem )
  {
    // filter "Non-static field 'x' cannot be referenced from a static context" when 'x' is a valid inner class

    String desc = hi.getDescription();
    if( elem instanceof PsiReferenceExpressionImpl )
    {
      if( desc.startsWith( "Non-static field" ) && desc.contains( "cannot be referenced from a static context" ) )
      {
        return isInnerClass( (PsiReferenceExpressionImpl)elem, firstElem );
      }
      else if( desc.contains( "Static method may be invoked on containing interface class only" ) ||
        desc.contains( "Expected class or package" ) )
      {
        PsiElement parent = elem.getParent().getParent();
        if( parent instanceof PsiReferenceExpression )
        {
          PsiElement ref = parent.getFirstChild();
          if( ref instanceof PsiReferenceExpression )
          {
            return isInnerClass( (PsiReferenceExpressionImpl)ref, ((PsiReferenceExpressionImpl)ref).getReferenceNameElement() );
          }
        }
      }
    }
    return false;
  }

  private static boolean isInnerClass( PsiReferenceExpressionImpl elem, PsiElement firstElem )
  {
    PsiReferenceExpressionImpl refExpr = elem;
    PsiElement qualifier = refExpr.getQualifier();
    if( qualifier instanceof PsiReferenceExpression )
    {
      PsiReference ref = refExpr.getQualifier().getReference();
      if( ref != null )
      {
        PsiElement qualRef = ref.resolve();
        if( qualRef instanceof PsiClass )
        {
          for( PsiClass innerClass : ((PsiClass)qualRef).getInnerClasses() )
          {
            String typeName = innerClass.getName();
            if( typeName != null && typeName.equals( firstElem.getText() ) )
            {
              // ref is inner class
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  private boolean filterForeachExpressionErrors( HighlightInfo hi, PsiElement elem, PsiElement firstElem )
  {
    if( !hi.getDescription().contains( "foreach not applicable to type" ) )
    {
      return false;
    }

    PsiForeachStatement foreachStmt = getForeachStmt( elem );
    if( foreachStmt != null )
    {
      PsiExpression expr = foreachStmt.getIteratedValue();
      if( expr.getType() instanceof PsiClassReferenceType refType )
      {
        PsiClass psiClass = refType.resolve();
        if( psiClass != null )
        {
          for( PsiMethod m: psiClass.findMethodsByName( "iterator", true ) )
          {
            if( !m.hasParameters() && m.getReturnType() != null )
            {
              //## todo: determine the parameterized return type and match it against the foreach stmt's var type
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  private PsiForeachStatement getForeachStmt( PsiElement elem )
  {
    if( elem == null )
    {
      return null;
    }
    if( elem instanceof PsiForeachStatement )
    {
      return (PsiForeachStatement)elem;
    }
    return getForeachStmt( elem.getParent() );
  }

  private boolean filterUnclosedComment( HighlightInfo hi, PsiElement firstElem )
  {
    // Preprocessor directives mask away text source in the lexer as comment tokens, obviously these will not
    // be closed with a normal comment terminator such as '*/'
    return firstElem instanceof PsiComment &&
           firstElem.getText().startsWith( "#" );
  }

  private boolean filterUnhandledCheckedExceptions( @NotNull HighlightInfo hi, @Nullable PsiFile file )
  {
    // Note the message can be singular or plural e.g., "Unhandled exception[s]:"
    if( hi.getDescription().contains( "Unhandled exception" ) )
    {
      Module fileModule = ManProject.getIjModule( file );
      if( fileModule != null )
      {
        ManModule manModule = ManProject.getModule( fileModule );
        return manModule.isExceptionsEnabled();
      }
    }
    return false;
  }

  private boolean filterCallToToStringOnArray( @NotNull HighlightInfo hi, @Nullable PsiFile file )
  {
    if( hi.getDescription().contains( "Call to 'toString()' on array" ) )
    {
      Module fileModule = ManProject.getIjModule( file );
      if( fileModule != null )
      {
        ManModule manModule = ManProject.getModule( fileModule );
        return manModule.isExtEnabled();
      }
    }
    return false;
  }

  private boolean filterCannotAssignToFinalIfJailbreak( HighlightInfo hi, PsiElement firstElem )
  {
    if( !hi.getDescription().startsWith( "Cannot assign a value to final variable" ) )
    {
      return false;
    }

    PsiElement parent = firstElem.getParent();
    PsiType type = null;
    if( parent instanceof PsiReferenceExpression )
    {
      type = ((PsiReferenceExpression)parent).getType();
    }
    return type != null && type.findAnnotation( Jailbreak.class.getTypeName() ) != null;
  }

  private boolean filterAmbiguousMethods( HighlightInfo hi, PsiElement elem )
  {
    if( !hi.getDescription().startsWith( "Ambiguous method call" ) )
    {
      return false;
    }

    while( !(elem instanceof PsiMethodCallExpression) )
    {
      elem = elem.getParent();
      if( elem == null )
      {
        return false;
      }
    }

    PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)elem).getMethodExpression();
    JavaResolveResult[] javaResolveResults = methodExpression.multiResolve( false );
    for( JavaResolveResult result: javaResolveResults )
    {
      if( result instanceof MethodCandidateInfo )
      {
        PsiElement psiMethod = result.getElement();
        if( psiMethod instanceof ManLightMethodBuilder )
        {
          return true;
        }
      }
    }
    return false;
  }

  private boolean filterIllegalEscapedCharDollars( @NotNull HighlightInfo hi, PsiElement firstElem, PsiElement elem )
  {
    return firstElem instanceof PsiJavaToken &&
           ((PsiJavaToken)firstElem).getTokenType() == JavaTokenType.STRING_LITERAL &&
           hi.getDescription().contains( "Illegal escape character" ) &&
           elem.getText().contains( "\\$" );
  }

  @Nullable
  private Boolean acceptInterfaceError( @NotNull HighlightInfo hi, PsiElement firstElem, PsiElement elem )
  {
    if( elem instanceof PsiTypeCastExpression )
    {
      PsiTypeElement castType = ((PsiTypeCastExpression)elem).getCastType();
      if( isStructuralType( castType ) )
      {
//        if( TypeUtil.isStructurallyAssignable( castType.getType(), ((PsiTypeCastExpression)elem).getType(), false ) )
//        {
        // ignore incompatible cast type involving structure
        return false;
//        }
      }
    }
    else if( isTypeParameterStructural( hi, firstElem ) )
    {
      return false;
    }
    else if( firstElem instanceof PsiIdentifier )
    {
      PsiTypeElement lhsType = findTypeElement( firstElem );
      if( isStructuralType( lhsType ) )
      {
        PsiType initType = findInitializerType( firstElem );
        if( initType != null )
        {
//          if( TypeUtil.isStructurallyAssignable( lhsType.getType(), initType, false ) )
//          {
            // ignore incompatible type in assignment involving structure
            return false;
//          }
        }
      }
    }
    else if( hi.getDescription().contains( "cannot be applied to" ) )
    {
      PsiMethodCallExpression methodCall = findMethodCall( firstElem );
      if( methodCall != null )
      {
        PsiMethod psiMethod = methodCall.resolveMethod();
        if( psiMethod != null )
        {
          PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
          PsiType[] argTypes = methodCall.getArgumentList().getExpressionTypes();
          for( int i = 0; i < parameters.length; i++ )
          {
            PsiParameter param = parameters[i];
            if( argTypes.length <= i )
            {
              return true;
            }
            if( !isStructuralType( param.getTypeElement() ) )
            {
              if( argTypes[i] == null || !param.getType().isAssignableFrom( argTypes[i] ) )
              {
                return true;
              }
            }
//            else
//            {
//              boolean nominal = false;//typeExtensionNominallyExtends( methodCall.getArgumentList().getExpressionTypes()[i], param.getTypeElement() );
//              if( !TypeUtil.isStructurallyAssignable( param.getType(), methodCall.getArgumentList().getExpressionTypes()[i], !nominal ) )
//              {
//                return true;
//              }
//            }
          }
          return true;
        }
      }
    }
    return null;
  }

  private boolean isTypeParameterStructural( @NotNull HighlightInfo hi, PsiElement firstElem )
  {
    if( firstElem == null )
    {
      return false;
    }

    String prefix = "is not within its bound; should ";
    int iPrefix = hi.getDescription().indexOf( prefix );
    if( iPrefix < 0 )
    {
      return false;
    }

    String fqn = hi.getDescription().substring( iPrefix + prefix.length() );
    fqn = fqn.substring( fqn.indexOf( '\'' ) + 1, fqn.lastIndexOf( '\'' ) );
    int iAngle = fqn.indexOf( '<' );
    if( iAngle > 0 )
    {
      fqn = fqn.substring( 0, iAngle );
    }
    // punting on generics for now, using just raw types (waiting patiently for jetbrains to add interfaces as augments...)
    PsiClass iface = JavaPsiFacade.getInstance( firstElem.getProject() ).findClass( fqn, GlobalSearchScope.allScope( firstElem.getProject() ) );
    //PsiReferenceExpression expr = (PsiReferenceExpression)PsiElementFactory.SERVICE.getInstance( firstElem.getProject() ).createExpressionFromText( fqn, firstElem );
    return isExtendedWithInterface( firstElem, iface );
  }

  private boolean isExtendedWithInterface( PsiElement firstElem, PsiClass iface )
  {
    if( iface == null || !ManPsiUtil.isStructuralInterface( iface ) )
    {
      return false;
    }

    PsiTypeElement typeElement = findTypeElement( firstElem );
    if( typeElement == null )
    {
      return false;
    }

    PsiType elemType = typeElement.getType();
    PsiClass psiClass = PsiUtil.resolveClassInType( elemType );
    if( psiClass == null )
    {
      while( elemType instanceof PsiCapturedWildcardType )
      {
        elemType = ((PsiCapturedWildcardType)elemType).getWildcard();
      }

      if( elemType instanceof PsiWildcardType )
      {
        PsiType bound = ((PsiWildcardType)elemType).getBound();
        if( bound == null )
        {
          bound = PsiType.getJavaLangObject( firstElem.getManager(), firstElem.getResolveScope() );
        }
        psiClass = PsiUtil.resolveClassInType( bound );
      }

      if( psiClass == null )
      {
        return false;
      }
    }

    return isExtendedWithInterface( psiClass, iface );
  }

  /**
   * This method checks if the given psiClass or anything in its hierarchy is extended with the structural iface
   * via the KEY_MAN_INTERFACE_EXTENSIONS user data, which is added during augmentation as a hack to provide info
   * about extension interfaces.
   */
  private boolean isExtendedWithInterface( PsiClass psiClass, PsiClass psiInterface )
  {
    if( psiClass == null )
    {
      return false;
    }

    psiClass.getMethods(); // force the ManAugmentProvider to add the KEY_MAN_INTERFACE_EXTENSIONS data, if it hasn't yet
    List<String> ifaceExtenions = psiClass.getUserData( KEY_MAN_INTERFACE_EXTENSIONS );
    if( ifaceExtenions == null || ifaceExtenions.isEmpty() )
    {
      return false;
    }

    Project project = psiClass.getProject();
    PsiClassType typeIface = JavaPsiFacade.getInstance( project ).getElementFactory().createType( psiInterface );

    for( String fqnExt: ifaceExtenions )
    {
      PsiClass psiExt = JavaPsiFacade.getInstance( project ).findClass( fqnExt, GlobalSearchScope.allScope( project ) );
      if( psiExt != null )
      {
        PsiClassType typeExt = JavaPsiFacade.getInstance( project ).getElementFactory().createType( psiExt );
        if( typeIface.isAssignableFrom( typeExt ) )
        {
          return true;
        }
      }
    }

    for( PsiClassType extendsType: psiClass.getExtendsListTypes() )
    {
      PsiClass extendsPsi = extendsType.resolve();
      if( isExtendedWithInterface( extendsPsi, psiInterface ) )
      {
        return true;
      }
    }
    for( PsiClassType implementsType: psiClass.getImplementsListTypes() )
    {
      PsiClass implementsPsi = implementsType.resolve();
      if( isExtendedWithInterface( implementsPsi, psiInterface ) )
      {
        return true;
      }
    }
    return false;
  }

  private boolean isInvalidStaticMethodOnInterface( HighlightInfo hi )
  {
    return hi.getDescription().contains( "Static method may be invoked on containing interface class only" );
  }

  private PsiType findInitializerType( PsiElement firstElem )
  {
    PsiElement csr = firstElem;
    while( csr != null && !(csr instanceof PsiLocalVariableImpl) )
    {
      csr = csr.getParent();
    }
    if( csr != null )
    {
      PsiExpression initializer = ((PsiLocalVariableImpl)csr).getInitializer();
      return initializer == null ? null : initializer.getType();
    }
    return null;
  }

//## todo: implementing this is not efficient to say the least, so for now we will always check for structural assignability
//  private boolean typeExtensionNominallyExtends( PsiType psiType, PsiTypeElement typeElement )
//  {
//    if( !(psiType instanceof PsiClassType) )
//    {
//      return false;
//    }
//
//    PsiClassType rawType = ((PsiClassType)psiType).rawType();
//    rawType.getSuperTypes()
//    ManModule module = ManProject.getModule( typeElement );
//    for( ITypeManifold sp : module.getTypeManifolds() )
//    {
//      if( sp.getContributorKind() == Supplemental )
//      {
//
//      }
//    }
//  }

//  private int findArgPos( PsiMethodCallExpression methodCall, PsiElement firstElem )
//  {
//    PsiExpression[] args = methodCall.getArgumentList().getExpressions();
//    for( int i = 0; i < args.length; i++ )
//    {
//      PsiExpression arg = args[i];
//      PsiElement csr = firstElem;
//      while( csr != null && csr != firstElem )
//      {
//        csr = csr.getParent();
//      }
//      if( csr == firstElem )
//      {
//        return i;
//      }
//    }
//    throw new IllegalStateException();
//  }

  private boolean isStructuralType( PsiTypeElement typeElem )
  {
    if( typeElem != null )
    {
      PsiClass psiClass = PsiUtil.resolveClassInType( typeElem.getType() );
      if( psiClass == null )
      {
        return false;
      }
      return ExtensionClassAnnotator.isStructuralInterface( psiClass );
    }
    return false;
  }

  private PsiTypeElement findTypeElement( PsiElement elem )
  {
    PsiElement csr = elem;
    while( csr != null && !(csr instanceof PsiTypeElement) )
    {
      csr = csr.getParent();
    }
    return (PsiTypeElement)csr;
  }

  private PsiMethodCallExpression findMethodCall( PsiElement elem )
  {
    PsiElement csr = elem;
    while( csr != null && !(csr instanceof PsiMethodCallExpression) )
    {
      csr = csr.getParent();
    }
    return (PsiMethodCallExpression)csr;
  }
}

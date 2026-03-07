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
import com.intellij.lang.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiJavaFileBaseImpl;
import com.intellij.psi.impl.source.tree.java.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import manifold.ext.props.rt.api.get;
import manifold.ext.props.rt.api.override;
import manifold.ext.props.rt.api.set;
import manifold.ext.props.rt.api.val;
import manifold.ext.props.rt.api.var;
import manifold.ext.rt.api.Jailbreak;
import manifold.ij.core.*;
import manifold.ij.psi.ManExtensionMethodBuilder;
import manifold.ij.psi.ManLightMethodBuilder;
import manifold.ij.template.psi.ManTemplateJavaFile;
import manifold.ij.util.ManPsiUtil;
import manifold.internal.javac.ManAttr;
import manifold.rt.api.util.ManClassUtil;
import org.apache.commons.lang3.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


import static com.intellij.psi.impl.source.tree.ChildRole.OPERATION_SIGN;
import static manifold.ij.extensions.ManAugmentProvider.KEY_MAN_INTERFACE_EXTENSIONS;

/**
 * Unfortunately IJ doesn't provide a way to augment a type with interfaces, so we are stuck with suppressing errors
 */
public class ManHighlightInfoFilter implements HighlightInfoFilter
{

  private static final Set<String> PROPERTY_ANNO_FQNS =
    Set.of( val.class.getTypeName(), var.class.getTypeName(), set.class.getTypeName(), get.class.getTypeName() );

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

    String description = hi.getDescription();

    if( description == null )
    {
      return true;
    }

    //
    // Handle Warnings OR Errors...
    //

    if( filterTemplateUnnecessarySemicolon( description, file ) )
    {
      return false;
    }

    if( filterCallToToStringOnArray( description, file ) )
    {
      return false;
    }

    if( filterUnhandledCheckedExceptions( description, file ) )
    {
      return false;
    }

    PsiElement firstElem = file.findElementAt( hi.getStartOffset() );

    if( firstElem == null )
    {
      return true;
    }

    if( filterComparedUsingEquals( description, firstElem ) )
    {
      return false;
    }

    if( filterCanBeReplacedWith( description, firstElem ) )
    {
      return false;
    }

    if( filterCastingStructuralInterfaceWarning( description, firstElem ) )
    {
      return false;
    }

    if( filterArrayIndexIsOutOfBounds( description, firstElem ) )
    {
      return false;
    }

    if( filterUpdatedButNeverQueried( description, firstElem ) )
    {
      return false;
    }

    if( filterFieldIsNeverUsed( description, firstElem ) )
    {
      return false;
    }

    if( filterNullMarkedFieldInitializationWarning( description, firstElem ) )
    {
      return false;
    }

    if( filterSynchronizationOnPropertyFieldWarning( description, firstElem ) )
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

    Language language = firstElem.getLanguage();
    if( !language.is( JavaLanguage.INSTANCE ) && !language.isKindOf( JavaLanguage.INSTANCE ) )
    {
      return true;
    }

    if( filterAmbiguousMethods( description, firstElem ) )
    {
      return false;
    }

    PsiElement elem = firstElem.getParent();
    if( elem == null )
    {
      return true;
    }

    if( filterIllegalEscapedCharDollars( description, firstElem, elem ) )
    {
      return false;
    }

    if( filterCannotAssignToFinalIfJailbreak( description, elem ) )
    {
      return false;
    }

    if( filterUnclosedComment( firstElem ) )
    {
      return false;
    }

    if( filterOperatorCannotBeApplied( description, elem, firstElem ) )
    {
      return false;
    }

    if( filterPrefixExprCannotBeApplied( description, elem ) ||
      filterPostfixExprCannotBeApplied( description, elem ) )
    {
      return false;
    }

    if( filterIncompatibleTypesWithCompoundAssignmentOperatorOverload( description, elem ) )
    {
      return false;
    }

    if( filterOperatorCannotBeAppliedToWithCompoundAssignmentOperatorOverload( description, elem ) )
    {
      return false;
    }

    if( filterOperatorCannotBeAppliedToWithBinaryOperatorOverload( description, elem ) )
    {
      return false;
    }

    // handle indexed operator overloading
    if( filterArrayTypeExpected( description, elem ) )
    {
      return false;
    }
    if( filterVariableExpected( description, elem ) )
    {
      return false;
    }
    if( filterIncompatibleTypesWithArrayAccess( description, elem ) )
    {
      return false;
    }

    if( filterAnyAnnoTypeError( description, elem ) )
    {
      return false;
    }

    if( filterIncompatibleReturnType( description, elem ) )
    {
      return false;
    }

    if( filterForeachExpressionErrors( description, elem ) )
    {
      return false;
    }

    if( filterInnerClassReferenceError( description, elem, firstElem ) )
    {
      return false;
    }

    if( filterUsageOfApiNewerThanError( description, elem ) )
    {
      return false;
    }

    if( filterParamsClassErrors( description, elem ) )
    {
      return false;
    }

    if( filterFieldIsNotInitializedInInterfaceError( description, elem ) )
    {
      return false;
    }

    //##
    //## structural interface extensions cannot be added to the psiClass, so for now we suppress "incompatible type
    //## errors" or similar involving a structural interface extension :(
    //##
    Boolean x = acceptInterfaceError( description, firstElem, elem );
    if( x != null )
    {
      return x;
    }

    return true;
  }

  private boolean filterTemplateUnnecessarySemicolon( String description, PsiFile file )
  {
    return (file instanceof ManTemplateJavaFile || !(file instanceof PsiJavaFileBaseImpl)) &&
      containsAny( description, "Unnecessary semicolon" ,  "不必要的分号");
  }

  // Operator overloading: Filter warning messages like "Number objects are compared using '==', not 'equals()'"
  private boolean filterComparedUsingEquals( String description, PsiElement firstElem )
  {
    return firstElem.getParent() instanceof PsiBinaryExpressionImpl binaryExpr &&
      containsAny( description, "compared using '=='", "compared using '!='", "使用 '==' 而不是", "使用 '!=' 而不是" ) &&
      ManJavaResolveCache.getTypeForOverloadedBinaryOperator( binaryExpr ) != null;
  }

  // Filter warning messages like "1 Xxx can be replaced with Xxx" where '1 Xxx' is a binding expression
  private boolean filterCanBeReplacedWith( String description, PsiElement firstElem )
  {
    if( containsAny( description,  "can be replaced with","可被替换为" ) )
    {
      PsiBinaryExpressionImpl binaryExpr = getSelfOrParentOfType( firstElem, PsiBinaryExpressionImpl.class );
      if( binaryExpr == null )
      {
        return false;
      }

      // a null operator indicates a biding expression
      PsiElement child = binaryExpr.findChildByRoleAsPsiElement( OPERATION_SIGN );
      return child == null;
    }
    return false;
  }

  // Filter warning messages like "1 Xxx can be replaced with Xxx" where '1 Xxx' is a binding expression
  private boolean filterCastingStructuralInterfaceWarning( String description, PsiElement firstElem )
  {
    if( startsEndsWith( description, "Casting '",  "will produce 'ClassCastException' for any non-null value" ) ||
      startsEndsWith( description, "将 '",  "会为任意非 null 值生成 'ClassCastException'" ) )
    {
      return isStructuralType( getSelfOrParentOfType( firstElem, PsiTypeElement.class ) );
    }
    return false;
  }

  // allow unary operator overload
  private boolean filterOperatorCannotBeApplied( String description, PsiElement elem, PsiElement firstElem )
  {
    return firstElem instanceof PsiJavaToken javaToken &&
      ( javaToken.getTokenType() == JavaTokenType.MINUS ||
        javaToken.getTokenType() == JavaTokenType.TILDE ||
        javaToken.getTokenType() == JavaTokenType.EXCL) &&
      elem instanceof ManPsiPrefixExpressionImpl prefixExpr &&
      (containsAll( description, "Operator", "cannot be applied to" ) || containsAll( description, "运算符", "不能应用于" )) &&
      prefixExpr.getTypeForUnaryOverload() != null;
  }

  // allow unary inc/dec operator overload
  private boolean filterPrefixExprCannotBeApplied( String description, PsiElement elem )
  {
    return elem instanceof ManPsiPrefixExpressionImpl prefixExpr &&
      containsAny( description,
        "Operator '-' cannot be applied to" , "Operator '--' cannot be applied to", "Operator '++' cannot be applied to",
        "运算符 '-' 不能应用于", "运算符 '--' 不能应用于", "运算符 '++' 不能应用于" ) &&
      prefixExpr.isOverloaded();
  }
  private boolean filterPostfixExprCannotBeApplied( String description, PsiElement elem )
  {
    return isInOverloadPostfixExpr( elem ) &&
      containsAny( description,
        "Operator '-' cannot be applied to", "Operator '--' cannot be applied to", "Operator '++' cannot be applied to",
        "运算符 '-' 不能应用于", "运算符 '--' 不能应用于", "运算符 '++' 不能应用于" );
  }

  private boolean isInOverloadPostfixExpr( PsiElement elem )
  {
    ManPsiPostfixExpressionImpl postfixExpr = getSelfOrParentOfType( elem, ManPsiPostfixExpressionImpl.class );
    return postfixExpr != null && postfixExpr.isOverloaded();
  }

  // allow compound assignment operator overloading
  private boolean filterIncompatibleTypesWithCompoundAssignmentOperatorOverload( String description, PsiElement elem )
  {
    return elem.getParent() instanceof PsiAssignmentExpressionImpl assignmentExpr  &&
      containsAny( description, "Incompatible types", "不兼容的类型" ) &&
      ManJavaResolveCache.getTypeForOverloadedBinaryOperator( assignmentExpr ) != null;
  }
  private boolean filterOperatorCannotBeAppliedToWithCompoundAssignmentOperatorOverload( String description, PsiElement elem )
  {
    if( elem instanceof PsiAssignmentExpressionImpl assignmentExpr )
    {
      return (description.contains( "' cannot be applied to " ) ||  // eg. "Operator '+' cannot be applied to 'java.math.BigDecimal'"
        description.contains( "' 不能应用于 " )) &&  // eg. "运算符 '+' 不能应用于 'java.math.BigDecimal'"
        ManJavaResolveCache.getTypeForOverloadedBinaryOperator( assignmentExpr ) != null;
    }
    return false;
  }

  private boolean filterOperatorCannotBeAppliedToWithBinaryOperatorOverload( String description, PsiElement elem )
  {
    PsiPolyadicExpression pexpr = getSelfOrParentOfType( elem, PsiPolyadicExpression.class );
    if( pexpr != null )
    {
      return (description.contains( "' cannot be applied to " ) ||  // eg. "Operator '+' cannot be applied to 'java.math.BigDecimal'"
        description.contains( "' 不能应用于 " )) &&  // eg. "运算符 '+' 不能应用于 'java.math.BigDecimal'"
        checkPolyadicOperatorApplicable( pexpr );
    }
    return false;
  }

  private static boolean checkPolyadicOperatorApplicable( @NotNull PsiPolyadicExpression expression )
  {
    PsiExpression[] operands = expression.getOperands();

    PsiType lType = operands[0].getType();
    IElementType operationSign = expression.getOperationTokenType();
    for( int i = 1; i < operands.length; i++ )
    {
      PsiExpression operand = operands[i];
      PsiType rType = operand.getType();
      if( !TypeConversionUtil.isBinaryOperatorApplicable( operationSign, lType, rType, false ) )
      {
        if( expression instanceof PsiBinaryExpression &&
          ManJavaResolveCache.getTypeForOverloadedBinaryOperator( expression ) != null )
        {
          continue;
        }
        else if( isInsideBindingExpression( expression ) )
        {
          // filter errors on binding expressions because they report errors on the '*" operator, those are properly
          // reported in our MiscAnnotator
          continue;
        }
        PsiJavaToken token = expression.getTokenBeforeOperand( operand );
        assert token != null : expression;

        // the error is legit, operator is indeed invalid
        return false;
      }
      lType = TypeConversionUtil.calcTypeForBinaryExpression( lType, rType, operationSign, true );
    }

    // filter the error, it is a valid operator via overload
    return true;
  }

  private static boolean isInsideBindingExpression( PsiElement expression )
  {
    return expression instanceof PsiBinaryExpression binaryExpression &&
      !ManJavaResolveCache.isBindingExpression( binaryExpression ) &&
      isInsideBindingExpression( expression.getParent() );
  }

  // support indexed operator overloading
  private boolean filterArrayTypeExpected( String description, PsiElement elem )
  {
    return startsWithAny( description, "Array type expected", "应为数组类型" ) &&
      hasBinaryType( getSelfOrParentOfType( elem, PsiArrayAccessExpressionImpl.class ) );
  }
  private boolean filterIncompatibleTypesWithArrayAccess( String description, PsiElement elem )
  {
    return elem.getParent() instanceof PsiArrayAccessExpression arrayAccessExpr &&
      containsAny( description, "Incompatible types", "不兼容的类型" ) &&
      arrayAccessExpr.getType() != null && arrayAccessExpr.getType().isValid();
  }

  private boolean filterVariableExpected( String description, PsiElement elem )
  {
    PsiArrayAccessExpressionImpl arrayAccess = getSelfOrParentOfType( elem, PsiArrayAccessExpressionImpl.class);
    while( arrayAccess != null && arrayAccess.getParent() instanceof PsiArrayAccessExpressionImpl parent )
    {
      // use outermost index expression e.g., matrix[x][y] = 6
      arrayAccess = parent;
    }
    return startsWithAny( description, "Variable expected", "应为变量" ) && hasBinaryType( arrayAccess );
  }
  private boolean filterArrayIndexIsOutOfBounds( String description, PsiElement firstElem )
  {
    return startsWithAny( description, "Array index is out of bounds", "数组索引超出范围" ) &&
      hasBinaryType( getSelfOrParentOfType( firstElem, PsiArrayAccessExpressionImpl.class ) );
  }

  private boolean hasBinaryType( @Nullable PsiArrayAccessExpressionImpl arrayAccess )
  {
    return arrayAccess != null && arrayAccess.getIndexExpression() != null &&
      ManJavaResolveCache.getBinaryType( ManJavaResolveCache.INDEXED_GET,
        arrayAccess.getArrayExpression().getType(), arrayAccess.getIndexExpression().getType(), arrayAccess ) != null;
  }

  private boolean filterAnyAnnoTypeError( String description, PsiElement elem )
  {
    // for use with properties e.g., @val(annos = @Foo) String name;

    return elem instanceof PsiAnnotation &&
      startsWithAny( description, "Incompatible types", "不兼容的类型" ) &&
      description.contains( manifold.rt.api.anno.any.class.getTypeName() );
  }

  private boolean filterUpdatedButNeverQueried( String description, PsiElement elem )
  {
    // for use with string templates when variable is referenced in the string

    if( containsAny( description, "updated, but never queried", "更新，但从未被查询" ) ||

      containsAll( description, "changed" , "is never used" ) || //todo: chinese

      containsAll( description,"The value ", "is never used") ) //todo: chinese
    {
      elem = resolveRef( elem ); // mainly for "is never used" cases
      return elem != null && new ManStringLiteralTemplateUsageProvider().isImplicitUsage( elem );
    }

    return false;
  }

  @Nullable
  private static PsiElement resolveRef( PsiElement elem )
  {
    if( elem instanceof PsiIdentifier && elem.getParent() instanceof PsiReferenceExpressionImpl referenceExpr )
    {
      return referenceExpr.resolve();
    }
    return elem;
  }

  private boolean filterIncompatibleReturnType( String description, PsiElement elem )
  {
    // filter method override "incompatible return type" error involving 'auto'

    return elem.getText().equals( ManClassUtil.getShortClassName( ManAttr.AUTO_TYPE ) ) &&
      containsAny( description, "incompatible return type", "返回类型不兼容" );
  }

  private boolean filterInnerClassReferenceError( String desc, PsiElement elem, PsiElement firstElem )
  {
    // filter "Non-static field 'x' cannot be referenced from a static context" when 'x' is a valid inner class

    if( elem instanceof PsiReferenceExpressionImpl referenceExpr )
    {
      if( containsAll( desc, "Non-static field", "cannot be referenced from a static context" ) )
      {
        return isInnerClass( referenceExpr, firstElem );
      }
      else if( containsAny( desc, "Static method may be invoked on containing interface class only", "Expected class or package" ) )
      {
        return elem.getParent().getParent() instanceof PsiReferenceExpression parent &&
          parent.getFirstChild() instanceof PsiReferenceExpressionImpl refExpr &&
          isInnerClass( refExpr, refExpr.getReferenceNameElement() );
      }
    }
    return false;
  }

  private static final Pattern PARAMS_CLASS_PATTERN = Pattern.compile( "\\$([a-zA-Z_$][a-zA-Z_$0-9]*)_(_[a-zA-Z_$][a-zA-Z_$0-9]*)" );
  private boolean filterParamsClassErrors( String description, PsiElement elem )
  {
    if( containedInTupleExpr( elem, elem ) )
    {
      if( description.contains( "<lambda parameter>" ) )
      {
        // bogus error wrt lambda arg to optional params method
        return true;
      }

      // pattern for matching manifold-params generated params class names such as $mymethodname__param1_param2.
      // basically, filtering out error messages concerning params method bc we do error checking on args wrt original
      // user-defined method, not generated one
      return PARAMS_CLASS_PATTERN.matcher( description ).find();
    }

    // allow for @Override on opt params method if it has at least on telescoping method that that overrides
    if( elem instanceof PsiAnnotation annotation &&
      Override.class.getTypeName().equals( annotation.getQualifiedName() ) )
    {
      PsiMethod enclosingMethod = RefactoringUtil.getEnclosingMethod( elem );
      if( enclosingMethod != null && ParamsMaker.hasOptionalParams( enclosingMethod ) )
      {
        PsiClass psiClass = enclosingMethod.getContainingClass();
        if( psiClass != null )
        {
          return false;
        }
        // opt params method has at least one telescoping overload that overrides a super method
        for( PsiMethod method : psiClass.getMethods() )
        {
          if( method instanceof ManExtensionMethodBuilder manMeth &&
            manMeth.getTargetMethod().equals( enclosingMethod ) &&
            !manMeth.findSuperMethods().isEmpty() );
          {
            return true;
          }
        }
        return false;
      }
    }

    return false;
  }

  private boolean filterFieldIsNotInitializedInInterfaceError( String description, PsiElement elem )
  {
    PsiField psiField = getSelfOrParentOfType( elem, PsiField.class );
    return psiField != null &&
      startsEndsWith( description, "Field '", "' might not have been initialized") &&
      isElementInInterface( elem ) && hasPropertyAnnotation( psiField );
  }

  private boolean filterFieldIsNeverUsed( String description, PsiElement firstElem )
  {
    if( !startsEndsWith( description, "Field '", "' is never used" ) )
    {
      return false;
    }
    PsiField psiField = getSelfOrParentOfType( firstElem, PsiField.class );
    return psiField != null && hasPropertyAnnotation( psiField ) && hasAnnotation( psiField, override.class );
  }

  private boolean filterNullMarkedFieldInitializationWarning( String description, PsiElement elem )
  {
    PsiField psiField = getSelfOrParentOfType( elem, PsiField.class );
    return psiField != null && description.equals( "@NullMarked fields must be initialized" )
      && isElementInInterface( elem ) && hasPropertyAnnotation( psiField );
  }

  private boolean filterSynchronizationOnPropertyFieldWarning( String description, PsiElement elem )
  {
    if( !description.startsWith( "Synchronization on a non-final field '" ) )
    {
      return false;
    }
    PsiReferenceExpression ref = PsiTreeUtil.getParentOfType( elem, PsiReferenceExpression.class );
    return ref != null &&  ref.resolve() instanceof PsiField field &&  hasAnyAnnotation( field, val.class, get.class );
  }

  private boolean isElementInInterface( PsiElement element )
  {
    PsiClass psiClass = PsiTreeUtil.getParentOfType( element, PsiClass.class );
    return psiClass != null && psiClass.isInterface();
  }

  private boolean hasAnnotation( @Nullable PsiAnnotationOwner psiAnnotationOwner, Class<?> annoType )
  {
    return psiAnnotationOwner != null && psiAnnotationOwner.hasAnnotation( annoType.getTypeName());
  }

  private boolean hasAnyAnnotation( @Nullable PsiAnnotationOwner psiAnnotationOwner, Class<?>... annoTypes )
  {
    for( Class<?> annoType : annoTypes )
    {
      if( hasAnnotation( psiAnnotationOwner, annoType ) )
      {
        return true;
      }
    }
    return false;
  }

  private boolean hasPropertyAnnotation( PsiField field )
  {
    for( PsiAnnotation a : field.getAnnotations() )
    {
      if( isPropertyAnnotation( a ) )
      {
        return true;
      }
    }
    return false;
  }

  private boolean isPropertyAnnotation( PsiAnnotation anno )
  {
    return PROPERTY_ANNO_FQNS.contains( anno.getQualifiedName() );
  }

  private static boolean containedInTupleExpr( PsiElement origin, PsiElement elem )
  {
    ManPsiTupleExpression manPsiTupleExpr = getSelfOrParentOfType(elem, ManPsiTupleExpression.class);
    return manPsiTupleExpr != null && manPsiTupleExpr.getTextRange().contains( origin.getTextRange() );
  }

  private boolean filterUsageOfApiNewerThanError( String description, PsiElement elem )
  {
    // filter "Usage of API..." error when `--release` version is older than API version and where the method call is an extension method

    if( description.startsWith( "Usage of API documented as" ) && elem instanceof PsiReferenceExpression ref )
    {
      PsiElement methodCall = ref.resolve();
      // the extension method supersedes the API in this case
      return methodCall instanceof ManExtensionMethodBuilder;
    }
    return false;
  }

  private static boolean isInnerClass( PsiReferenceExpressionImpl elem, PsiElement firstElem )
  {
    if( elem.getQualifier() instanceof PsiReferenceExpression qualifier )
    {
      PsiReference ref = qualifier.getReference();
      if( ref != null &&  ref.resolve() instanceof PsiClass psiClass )
      {
        for( PsiClass innerClass : psiClass.getInnerClasses() )
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
    return false;
  }

  private boolean filterForeachExpressionErrors( String description, PsiElement elem )
  {
    if( containsNone( description, "foreach not applicable to type", "oreach 不适用于类型" ) )
    {
      return false;
    }

    PsiForeachStatement foreachStmt = getSelfOrParentOfType( elem, PsiForeachStatement.class );
    if( foreachStmt != null )
    {
      PsiExpression expr = foreachStmt.getIteratedValue();
      if( expr != null && expr.getType() instanceof PsiClassReferenceType refType )
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

  private boolean filterUnclosedComment( PsiElement firstElem )
  {
    // Preprocessor directives mask away text source in the lexer as comment tokens, obviously these will not
    // be closed with a normal comment terminator such as '*/'
    return firstElem instanceof PsiComment && firstElem.getText().startsWith( "#" );
  }

  private boolean filterUnhandledCheckedExceptions( String description, PsiFile file )
  {
    // Note the message can be singular or plural e.g., "Unhandled exception[s]:"
    if( containsAny( description, "Unhandled exception", "未处理的异常", "未处理 异常" ) )
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

  private boolean filterCallToToStringOnArray( String description, PsiFile file )
  {
    if( description.contains( "Call to 'toString()' on array" ) )
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

  private boolean filterCannotAssignToFinalIfJailbreak( String description, PsiElement elem )
  {
    if( containsNone(description,  "Cannot assign a value to final variable", "无法将值赋给 final 变量" ) )
    {
      return false;
    }

    return elem instanceof PsiReferenceExpression refExpr && hasAnnotation( refExpr.getType(), Jailbreak.class );
  }

  private boolean filterAmbiguousMethods( String description, PsiElement elem )
  {
    if( containsNone(description,  "Ambiguous method call", "方法调用不明确" ) )
    {
      return false;
    }

    PsiMethodCallExpression methodCallExpr = getSelfOrParentOfType( elem, PsiMethodCallExpression.class );
    if( methodCallExpr == null )
    {
      return false;
    }

    PsiReferenceExpression methodExpression = methodCallExpr.getMethodExpression();
    JavaResolveResult[] javaResolveResults = methodExpression.multiResolve( false );
    for( JavaResolveResult result: javaResolveResults )
    {
      if( result instanceof MethodCandidateInfo && result.getElement() instanceof ManLightMethodBuilder )
      {
        return true;
      }
    }
    return false;
  }

  private boolean filterIllegalEscapedCharDollars( String description, PsiElement firstElem, PsiElement elem )
  {
    return firstElem instanceof PsiJavaToken javaToken &&
      javaToken.getTokenType() == JavaTokenType.STRING_LITERAL &&
      containsAny( description, "Illegal escape character", "字符串文字中的非法转义字符" ) &&
      elem.getText().contains( "\\$" );
  }

  @Nullable
  private Boolean acceptInterfaceError( String description, PsiElement firstElem, PsiElement elem )
  {
    if( elem instanceof PsiTypeCastExpression typeCastExpr )
    {
      PsiTypeElement castType = typeCastExpr.getCastType();
      if( isStructuralType( castType ) )
      {
//        if( TypeUtil.isStructurallyAssignable( castType.getType(), ((PsiTypeCastExpression)elem).getType(), false ) )
//        {
        // ignore incompatible cast type involving structure
        return false;
//        }
      }
    }
    else if( isTypeParameterStructural( description, firstElem ) )
    {
      return false;
    }
    else if( firstElem instanceof PsiIdentifier )
    {
      PsiTypeElement lhsType = getSelfOrParentOfType( firstElem, PsiTypeElement.class );
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
    else if( containsAny( description, "cannot be applied to", "不能应用于" ) )
    {
      PsiMethodCallExpression methodCall = getSelfOrParentOfType( firstElem, PsiMethodCallExpression.class );
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

  private boolean isTypeParameterStructural( String description, PsiElement firstElem )
  {
    String prefix = "is not within its bound; should ";
    int iPrefix = description.indexOf( prefix );
    if( iPrefix < 0 )
    {
      return false;
    }

    String fqn = description.substring( iPrefix + prefix.length() );
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

  private boolean isExtendedWithInterface( PsiElement firstElem, @Nullable PsiClass iface )
  {
    if( iface == null || !ManPsiUtil.isStructuralInterface( iface ) )
    {
      return false;
    }

    PsiTypeElement typeElement = getSelfOrParentOfType( firstElem, PsiTypeElement.class );
    if( typeElement == null )
    {
      return false;
    }

    PsiType elemType = typeElement.getType();
    PsiClass psiClass = PsiUtil.resolveClassInType( elemType );
    if( psiClass == null )
    {
      while( elemType instanceof PsiCapturedWildcardType capturedWildcardType )
      {
        elemType = capturedWildcardType.getWildcard();
      }

      if( elemType instanceof PsiWildcardType wildcardType )
      {
        PsiType bound = wildcardType.getBound();
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
  private boolean isExtendedWithInterface( @Nullable PsiClass psiClass, PsiClass psiInterface )
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

  private @Nullable PsiType findInitializerType( PsiElement firstElem )
  {
    PsiLocalVariableImpl csr = getSelfOrParentOfType( firstElem, PsiLocalVariableImpl.class );
    if( csr != null )
    {
      PsiExpression initializer = csr.getInitializer();
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

  private boolean isStructuralType( @Nullable PsiTypeElement typeElem )
  {
    if( typeElem != null )
    {
      PsiClass psiClass = PsiUtil.resolveClassInType( typeElem.getType() );
      return psiClass != null && ExtensionClassAnnotator.isStructuralInterface( psiClass );
    }
    return false;
  }

  private static <T extends PsiElement> @Nullable T getSelfOrParentOfType( @Nullable PsiElement elem , Class<T> type )
  {
    if( type.isInstance( elem ) )
    {
      return (T) elem;
    }
    return PsiTreeUtil.getParentOfType( elem, type );
  }

  private boolean startsWithAny( String description, String... parts )
  {
    for( String part: parts )
    {
      if( description.startsWith( part ) )
      {
        return true;
      }
    }
    return false;
  }


  private boolean containsAny( String description, String... parts )
  {
    for( String part: parts )
    {
      if( description.contains( part ) )
      {
        return true;
      }
    }
    return false;
  }

  private boolean containsAll( String description, String... parts )
  {
    for( String part : parts )
    {
      if( !description.contains( part ) )
      {
        return false;
      }
    }
    return true;
  }

  private boolean startsEndsWith( String description, String start, String end )
  {
    return description.startsWith( start ) && description.endsWith( end );
  }

  private boolean containsNone( String description, String... parts )
  {
    return !containsAny( description, parts );
  }
}
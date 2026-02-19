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
import java.util.regex.Matcher;
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
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NullMarked;


import static com.intellij.psi.impl.source.tree.ChildRole.OPERATION_SIGN;
import static manifold.ij.extensions.ManAugmentProvider.KEY_MAN_INTERFACE_EXTENSIONS;


/**
 * Unfortunately IJ doesn't provide a way to augment a type with interfaces, so we are stuck with suppressing errors
 */
@NullMarked
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
  public boolean accept( HighlightInfo hi, @Nullable PsiFile file )
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

    PsiElement firstElem = file.findElementAt( hi.getStartOffset() );

    if( filterFieldIsNeverUsed( description, firstElem ) )
    {
      return false;
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

    if( filterUnhandledCheckedExceptions( description, file ) )
    {
      return false;
    }

    if( firstElem == null )
    {
      return true;
    }

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

    if( filterPrefixExprCannotBeApplied( description, elem ) || filterPostfixExprCannotBeApplied( description, elem ) )
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
            (description.contains( "Unnecessary semicolon" ) || description.contains( "不必要的分号" ));
  }

  // Operator overloading: Filter warning messages like "Number objects are compared using '==', not 'equals()'"
  private boolean filterComparedUsingEquals( String description, @Nullable PsiElement firstElem )
  {
    if( firstElem == null ||
      ( !description.contains( "compared using '=='" ) && !description.contains( "compared using '!='" ) &&
      !description.contains( "使用 '==' 而不是" ) && !description.contains( "使用 '!=' 而不是" ) ) )
    {
      return false;
    }
    return firstElem.getParent() instanceof PsiBinaryExpression binaryExpr &&
      ManJavaResolveCache.getTypeForOverloadedBinaryOperator( binaryExpr ) != null;
  }

  // Filter warning messages like "1 Xxx can be replaced with Xxx" where '1 Xxx' is a binding expression
  private boolean filterCanBeReplacedWith( String description, @Nullable PsiElement firstElem )
  {
    if( description.contains( "can be replaced with" ) || description.contains( "可被替换为" ) )
    {
      PsiBinaryExpressionImpl binaryExpr = findElementOfType( firstElem, PsiBinaryExpressionImpl.class );
      // a null operator indicates a biding expression
      return binaryExpr != null && binaryExpr.findChildByRoleAsPsiElement( OPERATION_SIGN ) == null;
    }
    return false;
  }

  // Filter warning messages like "1 Xxx can be replaced with Xxx" where '1 Xxx' is a binding expression
  private boolean filterCastingStructuralInterfaceWarning( String description, @Nullable PsiElement firstElem )
  {
    if( descriptionStartsAndEndsWith (description, "Casting '", "will produce 'ClassCastException' for any non-null value" )
      || descriptionStartsAndEndsWith (description, "将 '", "会为任意非 null 值生成 'ClassCastException'" )  )
    {
      return isStructuralType( findElementOfType( firstElem, PsiTypeElement.class ) );
    }
    return false;
  }

  // allow unary operator overload
  private boolean filterOperatorCannotBeApplied( String description, PsiElement elem, PsiElement firstElem )
  {
    return firstElem instanceof PsiJavaToken javaToken &&
           (javaToken.getTokenType() == JavaTokenType.MINUS ||
            javaToken.getTokenType() == JavaTokenType.TILDE ||
            javaToken.getTokenType() == JavaTokenType.EXCL) &&
           elem instanceof ManPsiPrefixExpressionImpl prefixExpr &&
           (description.contains( "Operator" ) && description.contains( "cannot be applied to" ) ||
            description.contains( "运算符" ) && description.contains( "不能应用于" )) &&
           prefixExpr.getTypeForUnaryOverload() != null;
  }

  // allow unary inc/dec operator overload
  private boolean filterPrefixExprCannotBeApplied( String description, PsiElement elem )
  {
    return elem instanceof ManPsiPrefixExpressionImpl prefixExpression &&
           (description.contains( "Operator '-' cannot be applied to" ) ||
            description.contains( "Operator '--' cannot be applied to" ) ||
            description.contains( "Operator '++' cannot be applied to" ) ||

            description.contains( "运算符 '-' 不能应用于" ) ||
            description.contains( "运算符 '--' 不能应用于" ) ||
            description.contains( "运算符 '++' 不能应用于" )) &&
           prefixExpression.isOverloaded();
  }
  private boolean filterPostfixExprCannotBeApplied( String description, PsiElement elem )
  {
    return isInOverloadPostfixExpr( elem ) &&
           (description.contains( "Operator '-' cannot be applied to" ) ||
            description.contains( "Operator '--' cannot be applied to" ) ||
            description.contains( "Operator '++' cannot be applied to" ) ||

            description.contains( "运算符 '-' 不能应用于" ) ||
            description.contains( "运算符 '--' 不能应用于" ) ||
            description.contains( "运算符 '++' 不能应用于" ));
  }

  private boolean isInOverloadPostfixExpr( @Nullable PsiElement elem )
  {
    if( elem == null )
    {
      return false;
    }
    if( elem instanceof ManPsiPostfixExpressionImpl postfixExpression )
    {
      return postfixExpression.isOverloaded();
    }
    return isInOverloadPostfixExpr( elem.getParent() );
  }

  // allow compound assignment operator overloading
  private boolean filterIncompatibleTypesWithCompoundAssignmentOperatorOverload( String description, PsiElement elem )
  {
    return elem.getParent() instanceof PsiAssignmentExpressionImpl assignmentExpr &&
           (description.contains( "Incompatible types" ) ||
            description.contains( "不兼容的类型" )) &&
           ManJavaResolveCache.getTypeForOverloadedBinaryOperator( assignmentExpr ) != null;
  }
  private boolean filterOperatorCannotBeAppliedToWithCompoundAssignmentOperatorOverload( String description, PsiElement elem )
  {
    return elem instanceof PsiAssignmentExpressionImpl assignmentExpression &&
           (description.contains( "' cannot be applied to " ) ||  // eg. "Operator '+' cannot be applied to 'java.math.BigDecimal'"
            description.contains( "' 不能应用于 " )) &&  // eg. "运算符 '+' 不能应用于 'java.math.BigDecimal'"
           ManJavaResolveCache.getTypeForOverloadedBinaryOperator( assignmentExpression ) != null;
  }

  private boolean filterOperatorCannotBeAppliedToWithBinaryOperatorOverload( String description, PsiElement elem )
  {
    if( !description.contains( "' cannot be applied to " ) // eg. "Operator '+' cannot be applied to 'java.math.BigDecimal'"
      && !description.contains( "' 不能应用于 " ) ) // eg. "运算符 '+' 不能应用于 'java.math.BigDecimal'"
    {
      return false;
    }
    PsiPolyadicExpression pexpr = findElementOfType( elem, PsiPolyadicExpression.class );
    if( pexpr != null )
    {
      return checkPolyadicOperatorApplicable( pexpr );
    }
    return false;
  }

  private static boolean checkPolyadicOperatorApplicable( PsiPolyadicExpression expression )
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
    if( !(expression instanceof PsiBinaryExpression binaryExpression ) )
    {
      return false;
    }

    if( ManJavaResolveCache.isBindingExpression(binaryExpression) )
    {
      return true;
    }

    return isInsideBindingExpression( expression.getParent() );
  }

  // support indexed operator overloading
  private boolean filterArrayTypeExpected( String description, PsiElement elem )
  {
    if( !description.startsWith( "Array type expected" ) && !description.startsWith( "应为数组类型" ) )
    {
      return false;
    }
    PsiArrayAccessExpression arrayAccess = findElementOfType( elem, PsiArrayAccessExpression.class ) ;
    if( arrayAccess == null )
    {
      return false;
    }
    PsiExpression indexExpression = arrayAccess.getIndexExpression();
    return indexExpression != null &&
      ManJavaResolveCache.getBinaryType( ManJavaResolveCache.INDEXED_GET,
        arrayAccess.getArrayExpression().getType(), indexExpression.getType(), arrayAccess ) != null;
  }

  private boolean filterIncompatibleTypesWithArrayAccess( String description, PsiElement elem )
  {
    return elem.getParent() instanceof PsiArrayAccessExpression arrayAccessExpr &&
      (description.contains( "Incompatible types" ) || description.contains( "不兼容的类型" )) &&
      arrayAccessExpr.getType() != null && arrayAccessExpr.getType().isValid();
  }

  private boolean filterVariableExpected( String description, PsiElement elem )
  {
    if( !description.startsWith( "Variable expected" ) && !description.startsWith( "应为变量" ) )
    {
      return false;
    }
    PsiArrayAccessExpression arrayAccess = null;
    for( PsiElement csr = findElementOfType( elem, PsiArrayAccessExpression.class); csr instanceof PsiArrayAccessExpression arrayAccessExpr; csr = csr.getParent() )
    {
      // use outermost index expression e.g., matrix[x][y] = 6
      arrayAccess = arrayAccessExpr;
    }
    if( arrayAccess == null )
    {
      return false;
    }
    PsiExpression indexExpression = arrayAccess.getIndexExpression();
    return indexExpression != null &&
      ManJavaResolveCache.getBinaryType( ManJavaResolveCache.INDEXED_SET,
        arrayAccess.getArrayExpression().getType(), indexExpression.getType(), arrayAccess ) != null;
  }
  private boolean filterArrayIndexIsOutOfBounds( String description, @Nullable PsiElement firstElem )
  {
    if( !description.startsWith( "Array index is out of bounds" ) && !description.startsWith( "数组索引超出范围" ) )
    {
      return false;
    }
    PsiArrayAccessExpression arrayAccess = findElementOfType( firstElem, PsiArrayAccessExpression.class);
    if( arrayAccess == null )
    {
      return false;
    }
    PsiExpression indexExpression = arrayAccess.getIndexExpression();
    return indexExpression != null &&
      ManJavaResolveCache.getBinaryType( ManJavaResolveCache.INDEXED_GET,
        arrayAccess.getArrayExpression().getType(), indexExpression.getType(), arrayAccess ) != null;
  }

  private boolean filterAnyAnnoTypeError( String description, PsiElement elem )
  {
    // for use with properties e.g., @val(annos = @Foo) String name;

    return elem instanceof PsiAnnotation &&
      (description.startsWith( "Incompatible types" ) || description.startsWith( "不兼容的类型" )) &&
      description.contains( manifold.rt.api.anno.any.class.getTypeName() );
  }

  private boolean filterUpdatedButNeverQueried( String description, @Nullable PsiElement elem )
  {
    // for use with string templates when variable is referenced in the string

    if( (description.contains( "updated, but never queried" ) || description.contains( "更新，但从未被查询" )) ||

        (description.contains( "changed" ) && description.contains( "is never used" )) || //todo: chinese

        descriptionStartsAndEndsWith( description, "The value " , "is never used" ) ) //todo: chinese
    {
      elem = resolveRef( elem ); // mainly for "is never used" cases
      if( elem == null )
      {
        return false;
      }
      return new ManStringLiteralTemplateUsageProvider().isImplicitUsage( elem );
    }

    return false;
  }

  private static @Nullable PsiElement resolveRef( @Nullable PsiElement elem )
  {
    if( elem instanceof PsiIdentifier && elem.getParent() instanceof PsiReferenceExpressionImpl refExpr )
    {
      return refExpr.resolve();
    }
    return elem;
  }

  private boolean filterIncompatibleReturnType( String description, PsiElement elem )
  {
    // filter method override "incompatible return type" error involving 'auto'

    return elem.getText().equals( ManClassUtil.getShortClassName( ManAttr.AUTO_TYPE ) ) &&
      (description.contains( "incompatible return type" ) || description.contains( "返回类型不兼容" ));
  }

  private boolean filterInnerClassReferenceError( String desc, PsiElement elem, PsiElement firstElem )
  {
    // filter "Non-static field 'x' cannot be referenced from a static context" when 'x' is a valid inner class

    if( elem instanceof PsiReferenceExpressionImpl referenceExpression )
    {
      if( desc.startsWith( "Non-static field" ) && desc.contains( "cannot be referenced from a static context" ) )
      {
        return isInnerClass( referenceExpression, firstElem );
      }
      else if( desc.contains( "Static method may be invoked on containing interface class only" ) ||
        desc.contains( "Expected class or package" ) )
      {
        return elem.getParent().getParent() instanceof PsiReferenceExpression parentRefExpr
          && parentRefExpr.getFirstChild() instanceof PsiReferenceExpression refExpr
          && isInnerClass( refExpr, refExpr.getReferenceNameElement() );
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
      Matcher m = PARAMS_CLASS_PATTERN.matcher( description );
      return m.find();
    }

    // allow for @Override on opt params method if it has at least on telescoping method that that overrides
    if( elem instanceof PsiAnnotation anno && Override.class.getTypeName().equals( anno.getQualifiedName() ) )
    {
      PsiMethod enclosingMethod = RefactoringUtil.getEnclosingMethod( elem );
      if( enclosingMethod != null && ParamsMaker.hasOptionalParams( enclosingMethod ) )
      {
        PsiClass psiClass = enclosingMethod.getContainingClass();
        // opt params method has at least one telescoping overload that overrides a super method
        return psiClass != null && psiClass.getMethods().stream()
          .anyMatch( m -> m instanceof ManExtensionMethodBuilder manMeth &&
            manMeth.getTargetMethod().equals( enclosingMethod ) &&
            !manMeth.findSuperMethods().isEmpty() );
      }
    }

    return false;
  }

  /**
   * Determines whether the {@code Field 'X' might not have been initialized} highlight
   * should be suppressed.
   * <p>
   * The warning is suppressed if:
   * <ul>
   *   <li>The highlighted element is located inside an interface</li>
   *   <li>The field is annotated with a property annotation</li>
   * </ul>
   *
   * @param description the description produced by the inspection
   * @param elem the PSI element at the highlight location
   * @return Whether the warning should be suppressed
   */
  private boolean filterFieldIsNotInitializedInInterfaceError( String description, PsiElement elem )
  {
    return descriptionStartsAndEndsWith(description, "Field '", "' might not have been initialized")
      && isElementInInterface( elem ) && hasPropertyAnnotation( findElementOfType( elem, PsiField.class) );
  }

  /**
   * Determines whether the {@code Field 'X' is never used} highlight
   * should be suppressed.
   * <p>
   * The warning is suppressed if:
   * <ul>
   *   <li>The field is annotated with a property annotation</li>
   *   <li>The field is additionally annotated with {@link override}</li>
   * </ul>
   *
   * @param description the description produced by the inspection
   * @param elem the PSI element at the highlight location
   * @return Whether the warning should be suppressed
   */
  private boolean filterFieldIsNeverUsed( String description, @Nullable PsiElement elem )
  {
    if( elem == null || !descriptionStartsAndEndsWith(description, "Field '", "' is never used"))
    {
      return false;
    }
    PsiField psiFieldElement = findElementOfType( elem, PsiField.class);
    return hasPropertyAnnotation( psiFieldElement ) && hasAnnotation( psiFieldElement, override.class );
  }

  /**
   * Determines whether the {@code @NullMarked fields must be initialized} highlight
   * should be suppressed.
   *
   * <p>The suppression applies only if:
   * <ul>
   *   <li>The highlighted element is located inside an interface</li>
   *   <li>The field is annotated with a property annotation</li>
   * </ul>
   *
   * @param description the description produced by the inspection
   * @param elem the PSI element at the highlight location
   * @return Whether the warning should be suppressed
   */
  private boolean filterNullMarkedFieldInitializationWarning( String description, @Nullable PsiElement elem )
  {
    return elem != null && description.equals( "@NullMarked fields must be initialized" )
      && isElementInInterface( elem ) && hasPropertyAnnotation( findElementOfType( elem, PsiField.class) );
  }

  /**
   * Determines whether the {@code Synchronization on a non-final field} warning
   * should be suppressed.
   *
   * <p>This suppresses false positives when a field is effectively treated
   * as a final property via unmodifiable property annotations (i.e. {@link val}
   * or {@link get}), even if it is not declared {@code final} in source.</p>
   *
   * <p>The suppression applies only if:
   * <ul>
   *   <li>The field is annotated with a supported property annotation (i.e. {@link val} or {@link get})</li>
   * </ul>
   *
   * @param description the description produced by the inspection
   * @param elem the PSI element at the highlight location
   * @return Whether the warning should be suppressed
   */
  private boolean filterSynchronizationOnPropertyFieldWarning( String description, @Nullable PsiElement elem) {
    if( elem == null || !description.startsWith( "Synchronization on a non-final field '" ) )
    {
      return false;
    }
    PsiReferenceExpression reference = PsiTreeUtil.getParentOfType( elem, PsiReferenceExpression.class );
    if ( reference == null ) {
      return false;
    }
    return reference.resolve() instanceof PsiField field
      && ( hasAnnotation( field, val.class )  || hasAnnotation( field, get.class ) );
  }

  /**
   * Checks whether the description of a {@link HighlightInfo} starts and ends
   * with the given string fragments.
   *
   * @param description the description produced by the inspection
   * @param start required prefix
   * @param end required suffix
   * @return Whether the description matches the pattern
   */
  private boolean descriptionStartsAndEndsWith( String description, String start, String end )
  {
    return description.startsWith( start ) && description.endsWith( end );
  }

  /**
   * Determines whether the given PSI element is declared inside an interface.
   *
   * @param element the PSI element to inspect
   * @return Whether the element belongs to an interface class
   */
  private boolean isElementInInterface( PsiElement element )
  {
    PsiClass psiClass = PsiTreeUtil.getParentOfType( element, PsiClass.class );
    return psiClass != null && psiClass.isInterface();
  }

  /**
   * Checks whether the given field is annotated with the specified annotation type.
   *
   * @param field the field to inspect
   * @param annoType the annotation class to match
   * @return Whether the field has the provide annotation
   */
  private boolean hasAnnotation( @Nullable PsiField field, Class<?> annoType )
  {
    return field != null && Arrays.stream( field.getAnnotations() )
      .anyMatch( anno -> annoType.getTypeName().equals( anno.getQualifiedName() ) );
  }

  /**
   * Checks whether the given field has one of the property annotations.
   *
   * @param field the field to inspect
   * @return Whether the field has a property annotation
   */
  private boolean hasPropertyAnnotation( @Nullable PsiField field )
  {
    return field != null && Arrays.stream( field.getAnnotations() ).anyMatch( this::isPropertyAnnotation );
  }

  /**
   * Determines whether the given annotation represents a property annotation
   *       (i.e. {@link val}, {@link var}, {@link get}, {@link set})
   *
   * @param anno the annotation to inspect
   * @return Whether the annotation matches one of the supported types
   */
  private boolean isPropertyAnnotation( PsiAnnotation anno )
  {
    return PROPERTY_ANNO_FQNS.contains( anno.getQualifiedName() );
  }

  private static boolean containedInTupleExpr( PsiElement origin, @Nullable PsiElement elem )
  {
    if( elem == null )
    {
      return false;
    }
    if( elem instanceof ManPsiTupleExpression )
    {
      return elem.getTextRange().contains( origin.getTextRange() );
    }
    return containedInTupleExpr( origin, elem.getParent() );
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

  private static boolean isInnerClass( PsiReferenceExpression elem, PsiElement firstElem )
  {
    if( elem.getQualifier() instanceof PsiReferenceExpression referenceExpression )
    {
      PsiReference ref = referenceExpression.getReference();
      if( ref != null )
      {
        PsiElement qualRef = ref.resolve();
        if( qualRef instanceof PsiClass psiClass )
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
    }
    return false;
  }

  private boolean filterForeachExpressionErrors( String description, PsiElement elem )
  {
    if( !description.contains( "foreach not applicable to type" ) &&
        !description.contains( "oreach 不适用于类型" ) )
    {
      return false;
    }

    PsiForeachStatement foreachStmt = findElementOfType( elem, PsiForeachStatement.class );
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

  private boolean filterUnhandledCheckedExceptions( String description, @Nullable PsiFile file )
  {
    // Note the message can be singular or plural e.g., "Unhandled exception[s]:"
    if( description.contains( "Unhandled exception" ) ||
        description.contains( "未处理的异常" ) || description.contains( "未处理 异常" ) )
    {
      Module fileModule = ManProject.getIjModule( file );
      ManModule manModule = ManProject.getModule( fileModule );
      return manModule != null && manModule.isExceptionsEnabled();
    }
    return false;
  }

  private boolean filterCallToToStringOnArray( String description, @Nullable PsiFile file )
  {
    if( description.contains( "Call to 'toString()' on array" ) )
    {
      Module fileModule = ManProject.getIjModule( file );
      if( fileModule != null )
      {
        ManModule manModule = ManProject.getModule( fileModule );
        return manModule != null && manModule.isExtEnabled();
      }
    }
    return false;
  }

  private boolean filterCannotAssignToFinalIfJailbreak( String description, PsiElement elem )
  {
    if( !description.startsWith( "Cannot assign a value to final variable" ) &&
        !description.startsWith( "无法将值赋给 final 变量" ) )
    {
      return false;
    }

    PsiType type = null;
    if( elem instanceof PsiReferenceExpression referenceExpression )
    {
      type = referenceExpression.getType();
    }
    return type != null && type.hasAnnotation( Jailbreak.class.getTypeName() );
  }

  private boolean filterAmbiguousMethods( String description, PsiElement elem )
  {
    if( !description.startsWith( "Ambiguous method call" ) && !description.startsWith( "方法调用不明确" ) )
    {
      return false;
    }
    PsiMethodCallExpression methodCallExpression = findElementOfType( elem, PsiMethodCallExpression.class );
    if( methodCallExpression == null )
    {
      return false;
    }
    for( JavaResolveResult result: methodCallExpression.getMethodExpression().multiResolve( false ) )
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
           (description.contains( "Illegal escape character" ) || description.contains( "字符串文字中的非法转义字符" )) &&
           elem.getText().contains( "\\$" );
  }

  private @Nullable Boolean acceptInterfaceError( String description, PsiElement firstElem, PsiElement elem )
  {
    if( elem instanceof PsiTypeCastExpression typeCastExpression )
    {
      if( isStructuralType( typeCastExpression.getCastType() ) )
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
      PsiTypeElement lhsType = findElementOfType( firstElem, PsiTypeElement.class );
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
    else if( description.contains( "cannot be applied to" ) || description.contains( "不能应用于" ) )
    {
      PsiMethodCallExpression methodCall = findElementOfType( elem, PsiMethodCallExpression.class );
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
              if( !param.getType().isAssignableFrom( argTypes[i] ) )
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

  private boolean isExtendedWithInterface( @Nullable PsiElement firstElem, @Nullable PsiClass iface )
  {
    if( firstElem == null || iface == null || !ManPsiUtil.isStructuralInterface( iface ) )
    {
      return false;
    }

    PsiTypeElement typeElement = findElementOfType( firstElem, PsiTypeElement.class );
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

  private @Nullable PsiType findInitializerType( @Nullable PsiElement firstElem )
  {
    PsiLocalVariableImpl csr = findElementOfType( firstElem, PsiLocalVariableImpl.class );
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
    return typeElem != null && ExtensionClassAnnotator.isStructuralInterface( PsiUtil.resolveClassInType( typeElem.getType() ) );
  }

  private <C extends PsiElement> @Nullable C findElementOfType( @Nullable PsiElement element, Class<C> type )
  {
    PsiElement elem = element;
    while( elem != null && !type.isInstance( elem) )
    {
      elem = elem.getParent();
    }
    return type.cast( elem );
  }
}

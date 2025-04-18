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

package manifold.ij.core;

import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.WhitespacesBinders;
import com.intellij.lang.java.parser.*;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import java.util.function.Function;

import com.intellij.util.BitUtil;
import manifold.ext.rt.api.Jailbreak;
import manifold.util.ReflectUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import static com.intellij.codeInsight.daemon.JavaErrorBundle.BUNDLE;
import static com.intellij.lang.PsiBuilderUtil.*;
import static com.intellij.lang.PsiBuilderUtil.advance;
import static com.intellij.lang.java.parser.JavaParserUtil.*;
import static com.intellij.lang.java.parser.JavaParserUtil.emptyElement;
import static com.intellij.psi.impl.source.tree.JavaElementType.BINARY_EXPRESSION;
import static manifold.ij.core.ManElementType.TUPLE_EXPRESSION;
import static manifold.ij.core.ManElementType.TUPLE_VALUE_EXPRESSION;

//Manifold: replace ExpressionsParser with ManExpressionParser to handle "binding" expressions. See parseBinary().
public class ManExpressionParser extends ExpressionParser {
  private enum ExprType {
    CONDITIONAL_OR, CONDITIONAL_AND, OR, XOR, AND, EQUALITY, RELATIONAL, SHIFT, ADDITIVE, MULTIPLICATIVE, UNARY, TYPE
  }

  public static final TokenSet ASSIGNMENT_OPS = TokenSet.create(
    JavaTokenType.EQ, JavaTokenType.ASTERISKEQ, JavaTokenType.DIVEQ, JavaTokenType.PERCEQ, JavaTokenType.PLUSEQ, JavaTokenType.MINUSEQ,
    JavaTokenType.LTLTEQ, JavaTokenType.GTGTEQ, JavaTokenType.GTGTGTEQ, JavaTokenType.ANDEQ, JavaTokenType.OREQ, JavaTokenType.XOREQ);
  private static final TokenSet RELATIONAL_OPS = TokenSet.create(JavaTokenType.LT, JavaTokenType.GT, JavaTokenType.LE, JavaTokenType.GE);
  private static final TokenSet POSTFIX_OPS = TokenSet.create(JavaTokenType.PLUSPLUS, JavaTokenType.MINUSMINUS);
  private static final TokenSet PREF_ARITHMETIC_OPS = TokenSet.orSet(POSTFIX_OPS, TokenSet.create(JavaTokenType.PLUS, JavaTokenType.MINUS));
  private static final TokenSet PREFIX_OPS = TokenSet.orSet(PREF_ARITHMETIC_OPS, TokenSet.create(JavaTokenType.TILDE, JavaTokenType.EXCL));
  private static final TokenSet LITERALS = TokenSet.create(
    JavaTokenType.TRUE_KEYWORD, JavaTokenType.FALSE_KEYWORD, JavaTokenType.NULL_KEYWORD, JavaTokenType.INTEGER_LITERAL,
    JavaTokenType.LONG_LITERAL, JavaTokenType.FLOAT_LITERAL, JavaTokenType.DOUBLE_LITERAL, JavaTokenType.CHARACTER_LITERAL,
    JavaTokenType.STRING_LITERAL, JavaTokenType.TEXT_BLOCK_LITERAL );
  private static final TokenSet CONDITIONAL_OR_OPS = TokenSet.create(JavaTokenType.OROR);
  private static final TokenSet CONDITIONAL_AND_OPS = TokenSet.create(JavaTokenType.ANDAND);
  private static final TokenSet OR_OPS = TokenSet.create(JavaTokenType.OR);
  private static final TokenSet XOR_OPS = TokenSet.create(JavaTokenType.XOR);
  private static final TokenSet AND_OPS = TokenSet.create(JavaTokenType.AND);
  private static final TokenSet EQUALITY_OPS = TokenSet.create(JavaTokenType.EQEQ, JavaTokenType.NE);
  private static final TokenSet SHIFT_OPS = TokenSet.create(JavaTokenType.LTLT, JavaTokenType.GTGT, JavaTokenType.GTGTGT);
  private static final TokenSet ADDITIVE_OPS = TokenSet.create(JavaTokenType.PLUS, JavaTokenType.MINUS);
  private static final TokenSet MULTIPLICATIVE_OPS = TokenSet.create(JavaTokenType.ASTERISK, JavaTokenType.DIV, JavaTokenType.PERC);
  private static final TokenSet ARGS_LIST_END = TokenSet.create(JavaTokenType.RPARENTH, JavaTokenType.RBRACE, JavaTokenType.RBRACKET);
  private static final TokenSet ARGS_LIST_CONTINUE = TokenSet.create(
    JavaTokenType.IDENTIFIER, TokenType.BAD_CHARACTER, JavaTokenType.COMMA, JavaTokenType.INTEGER_LITERAL, JavaTokenType.STRING_LITERAL);
  private static final TokenSet THIS_OR_SUPER = TokenSet.create(JavaTokenType.THIS_KEYWORD, JavaTokenType.SUPER_KEYWORD);
  private static final TokenSet ID_OR_SUPER = TokenSet.create(JavaTokenType.IDENTIFIER, JavaTokenType.SUPER_KEYWORD);
  private static final TokenSet TYPE_START = TokenSet.orSet(
    ElementType.PRIMITIVE_TYPE_BIT_SET, TokenSet.create(JavaTokenType.IDENTIFIER, JavaTokenType.AT));

  static final int FORBID_LAMBDA_MASK = 0x1;

  private final JavaParser myParser;

  public ManExpressionParser(@NotNull JavaParser javaParser) {
    super( javaParser );
    myParser = javaParser;
  }

  @Nullable
  public PsiBuilder.Marker parse(@NotNull PsiBuilder builder) {
    return parseAssignment(builder);
  }

  @Nullable
  public PsiBuilder.Marker parseCaseLabel(@NotNull PsiBuilder builder) {
    @Jailbreak StatementParser statementParser = myParser.getStatementParser();
    return statementParser.parseCaseLabel(builder).first;
  }

  @Nullable
  private PsiBuilder.Marker parseAssignment(final PsiBuilder builder) {
    return parseAssignment( builder, 0, false );
  }
  //Manifold: added lhs param to distinguish between declaration part and non-declaration part e.g., X y = z, X y is decl, z is non-decl.
  public PsiBuilder.Marker parseAssignment(final PsiBuilder builder, final int mode, boolean lhs) {

    final PsiBuilder.Marker left;
    if( lhs )
    {
      // unary expr may be the actual target call for the lhs of decl, this change
      // allows binding expressions to parse (see parseBinary())
      left = parseUnary( builder, mode );
    }
    else
    {
      left = parseConditional(builder, mode);
    }

    if (left == null) return null;

    final IElementType tokenType = getGtTokenType(builder);
    if (tokenType != null && ASSIGNMENT_OPS.contains(tokenType)) {
      final PsiBuilder.Marker assignment = left.precede();
      advanceGtToken(builder, tokenType);

      final PsiBuilder.Marker right = parse(builder);
      if (right == null) {
        error(builder, JavaPsiBundle.message("expected.expression"));
      }

      assignment.done(JavaElementType.ASSIGNMENT_EXPRESSION);
      return assignment;
    }

    return left;
  }

  @Nullable
  PsiBuilder.Marker parseConditionalAnd(final PsiBuilder builder, final int mode) {
    return parseExpression(builder, ExprType.CONDITIONAL_AND, mode);
  }

  @Nullable
  public PsiBuilder.Marker parseConditional(final PsiBuilder builder, final int mode) {
    final PsiBuilder.Marker condition = parseExpression(builder, ExprType.CONDITIONAL_OR, mode);
    if (condition == null) return null;

    if (builder.getTokenType() != JavaTokenType.QUEST) return condition;
    final PsiBuilder.Marker ternary = condition.precede();
    builder.advanceLexer();

    final PsiBuilder.Marker truePart = parse(builder);
    if (truePart == null) {
      error(builder, JavaPsiBundle.message("expected.expression"));
      ternary.done(JavaElementType.CONDITIONAL_EXPRESSION);
      return ternary;
    }

    if (builder.getTokenType() != JavaTokenType.COLON) {
      error(builder, JavaPsiBundle.message("expected.colon"));
      ternary.done(JavaElementType.CONDITIONAL_EXPRESSION);
      return ternary;
    }
    builder.advanceLexer();

    final PsiBuilder.Marker falsePart = parseConditional(builder, mode);
    if (falsePart == null) {
      error(builder, JavaPsiBundle.message("expected.expression"));
      ternary.done(JavaElementType.CONDITIONAL_EXPRESSION);
      return ternary;
    }

    ternary.done(JavaElementType.CONDITIONAL_EXPRESSION);
    return ternary;
  }

  @Nullable
  private PsiBuilder.Marker parseExpression(final PsiBuilder builder, final ExprType type, final int mode) {
    //Manifold: handle binding expressions (null type indiciates binding expr type)
    if( type == null ) {
      return parseBinary( builder, ExprType.MULTIPLICATIVE, TokenSet.EMPTY, mode );
    }

    switch (type) {
      case CONDITIONAL_OR:
        return parseBinary(builder, ExprType.CONDITIONAL_AND, CONDITIONAL_OR_OPS, mode);

      case CONDITIONAL_AND:
        return parseBinary(builder, ExprType.OR, CONDITIONAL_AND_OPS, mode);

      case OR:
        return parseBinary(builder, ExprType.XOR, OR_OPS, mode);

      case XOR:
        return parseBinary(builder, ExprType.AND, XOR_OPS, mode);

      case AND:
        return parseBinary(builder, ExprType.EQUALITY, AND_OPS, mode);

      case EQUALITY:
        return parseBinary(builder, ExprType.RELATIONAL, EQUALITY_OPS, mode);

      case RELATIONAL:
        return parseRelational(builder, mode);

      case SHIFT:
        return parseBinary(builder, ExprType.ADDITIVE, SHIFT_OPS, mode);

      case ADDITIVE:
        //Manifold: null expr type indicates binding expr, which has precedence between + and *
        return parseBinary(builder, null, ADDITIVE_OPS, mode);

      // See type == null case above for binding expressions

      case MULTIPLICATIVE:
        return parseBinary(builder, ExprType.UNARY, MULTIPLICATIVE_OPS, mode);

      case UNARY:
        return parseUnary(builder, mode);

      case TYPE:
        return myParser.getReferenceParser().parseType(builder, ReferenceParser.EAT_LAST_DOT | ReferenceParser.WILDCARD);

      default:
        assert false : "Unexpected type: " + type;
        return null;
    }
  }

  //Manifold: modify parseBinary() to handle binding expressions
  @Nullable
  private PsiBuilder.Marker parseBinary( final PsiBuilder builder, final ExprType type, final TokenSet ops, final int mode) {
    return parseBinary( null, builder, type, ops, mode );
  }
  private PsiBuilder.Marker parseBinary( final PsiBuilder.Marker lhs, final PsiBuilder builder, final ExprType type, final TokenSet ops, final int mode) {
    //Manifold: optional lhs from binding expr parsing
    PsiBuilder.Marker result = lhs == null ? parseExpression(builder, type, mode) : lhs;
    if (result == null) return null;
    int operandCount = 1;

    IElementType tokenType = getGtTokenType(builder);
    IElementType currentExprTokenType = tokenType;
    while (true) {
      //Manifold: the "empty" operator precedence is in between '+' and '*', the next token is the operand (since operator is empty)
      boolean isBinderExpr = ops == TokenSet.EMPTY && isBinderRhs( tokenType );
      if( !ops.contains( tokenType ) && !isBinderExpr )
      {
        break;
      }

      //Manifold: don't advance if "empty" operator
      if( !isBinderExpr )
      {
        advanceGtToken( builder, tokenType );
      }

      final PsiBuilder.Marker right = isBinderExpr ? parseBinderRhs( builder, type, mode ) : parseExpression(builder, type, mode);
      operandCount++;
      tokenType = getGtTokenType(builder);
      //Manifold: comment out if-stmt to prevent "polyadic" expressions, favor binary expressions instead to make bindings work
      //if (!ops.contains( tokenType ) || tokenType != currentExprTokenType || right == null ) {
        // save
        result = result.precede();
        if (right == null) {
          error(builder, JavaPsiBundle.message("expected.expression"));
        }
        result.done(operandCount > 2 ? JavaElementType.POLYADIC_EXPRESSION : BINARY_EXPRESSION);
        if (right == null) break;
        currentExprTokenType = tokenType;
        operandCount = 1;
      //}
    }

    return result;
  }

  //Manifold:
  private PsiBuilder.Marker parseBinderRhs( PsiBuilder builder, ExprType type, final int mode)
  {
    IElementType tokenType = builder.getTokenType();

    PsiBuilder.Marker expr = null;

    if (LITERALS.contains(tokenType)) {
      final PsiBuilder.Marker literal = builder.mark();
      builder.advanceLexer();
      literal.done(JavaElementType.LITERAL_EXPRESSION);
      expr = literal;
    }
    else if (tokenType == JavaTokenType.LPARENTH) {
      final PsiBuilder.Marker parenth = builder.mark();
      builder.advanceLexer();

      final PsiBuilder.Marker inner = parse(builder);
      if (inner == null) {
        error(builder, JavaPsiBundle.message("expected.expression"));
      }

      if (!expect(builder, JavaTokenType.RPARENTH)) {
        if (inner != null) {
          error(builder, JavaPsiBundle.message("expected.rparen"));
        }
      }

      parenth.done(JavaElementType.PARENTH_EXPRESSION);
      expr = parenth;
    }
    else if (tokenType == JavaTokenType.IDENTIFIER) {
      final PsiBuilder.Marker refExpr;
      refExpr = builder.mark();
      builder.mark().done(JavaElementType.REFERENCE_PARAMETER_LIST);

      builder.advanceLexer();
      refExpr.done(JavaElementType.REFERENCE_EXPRESSION);
      expr = refExpr;
    }
    else {
      return null;
    }

    tokenType = getGtTokenType( builder );
    if( MULTIPLICATIVE_OPS.contains( tokenType ) )
    {
      expr = parseBinary( expr, builder, ExprType.UNARY, MULTIPLICATIVE_OPS, mode );
    }
    return expr;
  }

  //Manifold: binding expr operands start with Identifier or Literal or LeftParen
  private boolean isBinderRhs( IElementType tokenType )
  {
    return
      tokenType == JavaTokenType.IDENTIFIER ||
      tokenType == JavaTokenType.INTEGER_LITERAL ||
      tokenType == JavaTokenType.LONG_LITERAL ||
      tokenType == JavaTokenType.FLOAT_LITERAL ||
      tokenType == JavaTokenType.DOUBLE_LITERAL ||
      tokenType == JavaTokenType.STRING_LITERAL ||

      // for explicit grouping: (9 (x y z)) or ((a b)(c d)), not this is not illegal (a (b)) because a(b) conflicts
      // with method call grammar, must be (a)(b) instead
      tokenType == JavaTokenType.LPARENTH;
  }

  @Nullable
  private PsiBuilder.Marker parseRelational(final PsiBuilder builder, final int mode) {
    PsiBuilder.Marker left = parseExpression(builder, ExprType.SHIFT, mode);
    if (left == null) return null;

    IElementType tokenType;
    while ((tokenType = getGtTokenType(builder)) != null) {
      final IElementType toCreate;
      final boolean patternExpected; // Otherwise ExprType.SHIFT is expected
      if (RELATIONAL_OPS.contains(tokenType)) {
        toCreate = JavaElementType.BINARY_EXPRESSION;
        patternExpected = false;
      }
      else if (tokenType == JavaTokenType.INSTANCEOF_KEYWORD) {
        toCreate = JavaElementType.INSTANCE_OF_EXPRESSION;
        patternExpected = true;
      }
      else {
        break;
      }

      final PsiBuilder.Marker expression = left.precede();
      advanceGtToken(builder, tokenType);
      if (patternExpected) {
        if (!myParser.getPatternParser().isPattern(builder)) {
          PsiBuilder.Marker type = parseExpression(builder, ExprType.TYPE, mode);
          if (type == null) {
            error(builder, JavaPsiBundle.message("expected.type"));
          }
          expression.done(toCreate);
          return expression;
        }
        parsePrimaryPattern( builder );
      } else {
        final PsiBuilder.Marker right = parseExpression(builder, ExprType.SHIFT, mode);
        if (right == null) {
          error(builder, JavaPsiBundle.message("expected.expression"));
          expression.done(toCreate);
          return expression;
        }
      }

      expression.done(toCreate);
      left = expression;
    }

    return left;
  }

  //Manifold: IJ 2022.2.1 adds a boolean param to PatternParser#parsePrimaryPattern()
  private void parsePrimaryPattern( PsiBuilder builder )
  {
    PatternParser patternParser = myParser.getPatternParser();
    ReflectUtil.LiveMethodRef parsePrimaryPattern = ReflectUtil.WithNull.method(
      patternParser, "parsePrimaryPattern", PsiBuilder.class, boolean.class );
    if( parsePrimaryPattern != null )
    {
      parsePrimaryPattern.invoke( builder, false );
    }
    else
    {
      // prior to 2022.2.1 no boolean param
      ReflectUtil.method( patternParser, "parsePrimaryPattern", PsiBuilder.class ).invoke( builder );
    }
  }

  @Nullable
  private PsiBuilder.Marker parseUnary(final PsiBuilder builder, final int mode) {
    final IElementType tokenType = builder.getTokenType();

    if (PREFIX_OPS.contains(tokenType)) {
      final PsiBuilder.Marker unary = builder.mark();
      builder.advanceLexer();

      final PsiBuilder.Marker operand = parseUnary(builder, mode);
      if (operand == null) {
        error(builder, JavaPsiBundle.message("expected.expression"));
      }

      unary.done(JavaElementType.PREFIX_EXPRESSION);
      return unary;
    }
    else if (tokenType == JavaTokenType.LPARENTH) {
      final PsiBuilder.Marker typeCast = builder.mark();
      builder.advanceLexer();

      ReferenceParser.TypeInfo typeInfo = myParser.getReferenceParser().parseTypeInfo(
        builder, ReferenceParser.EAT_LAST_DOT | ReferenceParser.WILDCARD | ReferenceParser.CONJUNCTIONS | ReferenceParser.INCOMPLETE_ANNO);
      if (typeInfo == null || !expect(builder, JavaTokenType.RPARENTH)) {
        typeCast.rollbackTo();
        return parsePostfix(builder, mode);
      }

      if (PREF_ARITHMETIC_OPS.contains(builder.getTokenType()) && !typeInfo.isPrimitive) {
        typeCast.rollbackTo();
        return parsePostfix(builder, mode);
      }

      final PsiBuilder.Marker expr = parseUnary(builder, mode);
      if (expr == null) {
        if (!typeInfo.isParameterized) {  // cannot parse correct parenthesized expression after correct parameterized type
          typeCast.rollbackTo();
          return parsePostfix(builder, mode);
        }
        else {
          error(builder, JavaPsiBundle.message("expected.expression"));
        }
      }

      typeCast.done(JavaElementType.TYPE_CAST_EXPRESSION);
      return typeCast;
    }
    else if (tokenType == JavaTokenType.SWITCH_KEYWORD) {
      return myParser.getStatementParser().parseExprInParenthWithBlock(builder, JavaElementType.SWITCH_EXPRESSION, true);
    }
    else {
      return parsePostfix(builder, mode);
    }
  }

  @Nullable
  private PsiBuilder.Marker parsePostfix(final PsiBuilder builder, final int mode) {
    PsiBuilder.Marker operand = parsePrimary(builder, null, -1, mode);
    if (operand == null) return null;

    while (POSTFIX_OPS.contains(builder.getTokenType())) {
      final PsiBuilder.Marker postfix = operand.precede();
      builder.advanceLexer();
      postfix.done(JavaElementType.POSTFIX_EXPRESSION);
      operand = postfix;
    }

    return operand;
  }

  private enum BreakPoint {P1, P2, P4}

  @Nullable
  private PsiBuilder.Marker parsePrimary(PsiBuilder builder, @Nullable BreakPoint breakPoint, int breakOffset, final int mode) {
    PsiBuilder.Marker startMarker = builder.mark();

    PsiBuilder.Marker expr = parsePrimaryExpressionStart(builder, mode);
    if (expr == null) {
      startMarker.drop();
      return null;
    }

    while (true) {
      final IElementType tokenType = builder.getTokenType();
      if (tokenType == JavaTokenType.DOT) {
        final PsiBuilder.Marker dotPos = builder.mark();
        final int dotOffset = builder.getCurrentOffset();
        builder.advanceLexer();

        IElementType dotTokenType = builder.getTokenType();
        if (dotTokenType == JavaTokenType.AT) {
          myParser.getDeclarationParser().parseAnnotations(builder);
          dotTokenType = builder.getTokenType();
        }

        if (dotTokenType == JavaTokenType.CLASS_KEYWORD && exprType(expr) == JavaElementType.REFERENCE_EXPRESSION) {
          if (breakPoint == BreakPoint.P1 && builder.getCurrentOffset() == breakOffset) {
            error(builder, JavaPsiBundle.message("expected.identifier"));
            drop(startMarker, dotPos);
            return expr;
          }

          final PsiBuilder.Marker copy = startMarker.precede();
          final int offset = builder.getCurrentOffset();
          startMarker.rollbackTo();

          final PsiBuilder.Marker classObjAccess = parseClassAccessOrMethodReference(builder);
          if (classObjAccess == null || builder.getCurrentOffset() < offset) {
            copy.rollbackTo();
            return parsePrimary(builder, BreakPoint.P1, offset, mode);
          }

          startMarker = copy;
          expr = classObjAccess;
        }
        else if (dotTokenType == JavaTokenType.NEW_KEYWORD) {
          dotPos.drop();
          expr = parseNew(builder, expr);
        }
        else if (dotTokenType == JavaTokenType.SUPER_KEYWORD && builder.lookAhead(1) == JavaTokenType.LPARENTH) {
          dotPos.drop();
          PsiBuilder.Marker refExpr = expr.precede();
          builder.mark().done(JavaElementType.REFERENCE_PARAMETER_LIST);
          builder.advanceLexer();
          refExpr.done(JavaElementType.REFERENCE_EXPRESSION);
          expr = refExpr;
        }
        else if (dotTokenType == JavaTokenType.STRING_TEMPLATE_BEGIN || dotTokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN) {
          dotPos.drop();
          expr = parseStringTemplate(builder, expr, dotTokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN);
        }
        else if (dotTokenType == JavaTokenType.STRING_LITERAL || dotTokenType == JavaTokenType.TEXT_BLOCK_LITERAL) {
          dotPos.drop();
          final PsiBuilder.Marker templateExpression = expr.precede();
          final PsiBuilder.Marker literal = builder.mark();
          builder.advanceLexer();
          literal.done(JavaElementType.LITERAL_EXPRESSION);
          templateExpression.done(JavaElementType.TEMPLATE_EXPRESSION);
          expr = templateExpression;
        }
        else if (THIS_OR_SUPER.contains(dotTokenType) && exprType(expr) == JavaElementType.REFERENCE_EXPRESSION) {
          if (breakPoint == BreakPoint.P2 && builder.getCurrentOffset() == breakOffset) {
            dotPos.rollbackTo();
            startMarker.drop();
            return expr;
          }

          PsiBuilder.Marker copy = startMarker.precede();
          int offset = builder.getCurrentOffset();
          startMarker.rollbackTo();

          PsiBuilder.Marker ref = myParser.getReferenceParser().parseJavaCodeReference(builder, false, true, false, false);
          if (ref == null || builder.getTokenType() != JavaTokenType.DOT || builder.getCurrentOffset() != dotOffset) {
            copy.rollbackTo();
            return parsePrimary(builder, BreakPoint.P2, offset, mode);
          }
          builder.advanceLexer();

          if (builder.getTokenType() != dotTokenType) {
            copy.rollbackTo();
            return parsePrimary(builder, BreakPoint.P2, offset, mode);
          }
          builder.advanceLexer();

          startMarker = copy;
          expr = ref.precede();
          expr.done(dotTokenType == JavaTokenType.THIS_KEYWORD ? JavaElementType.THIS_EXPRESSION : JavaElementType.SUPER_EXPRESSION);
        }
        else {
          PsiBuilder.Marker refExpr = expr.precede();

          myParser.getReferenceParser().parseReferenceParameterList(builder, false, false);

          if (!expect(builder, ID_OR_SUPER)) {
            dotPos.rollbackTo();
            builder.advanceLexer();
            myParser.getReferenceParser().parseReferenceParameterList(builder, false, false);
            error(builder, JavaPsiBundle.message("expected.identifier"));
            refExpr.done(JavaElementType.REFERENCE_EXPRESSION);
            startMarker.drop();
            return refExpr;
          }

          dotPos.drop();
          refExpr.done(JavaElementType.REFERENCE_EXPRESSION);
          expr = refExpr;
        }
      }
      else if (tokenType == JavaTokenType.LPARENTH) {
        if (exprType(expr) != JavaElementType.REFERENCE_EXPRESSION) {
          startMarker.drop();
          return expr;
        }

        PsiBuilder.Marker callExpr = expr.precede();
        parseArgumentList(builder);
        callExpr.done(JavaElementType.METHOD_CALL_EXPRESSION);
        expr = callExpr;
      }
      else if (tokenType == JavaTokenType.LBRACKET) {
        if (breakPoint == BreakPoint.P4) {
          startMarker.drop();
          return expr;
        }

        builder.advanceLexer();

        if (builder.getTokenType() == JavaTokenType.RBRACKET && exprType(expr) == JavaElementType.REFERENCE_EXPRESSION) {
          final int pos = builder.getCurrentOffset();
          final PsiBuilder.Marker copy = startMarker.precede();
          startMarker.rollbackTo();

          final PsiBuilder.Marker classObjAccess = parseClassAccessOrMethodReference(builder);
          if (classObjAccess == null || builder.getCurrentOffset() <= pos) {
            copy.rollbackTo();
            return parsePrimary(builder, BreakPoint.P4, -1, mode);
          }

          startMarker = copy;
          expr = classObjAccess;
        }
        else {
          final PsiBuilder.Marker arrayAccess = expr.precede();

          final PsiBuilder.Marker index = parse(builder);
          if (index == null) {
            error(builder, JavaPsiBundle.message("expected.expression"));
            arrayAccess.done(JavaElementType.ARRAY_ACCESS_EXPRESSION);
            startMarker.drop();
            return arrayAccess;
          }

          if (builder.getTokenType() != JavaTokenType.RBRACKET) {
            error(builder, JavaPsiBundle.message("expected.rbracket"));
            arrayAccess.done(JavaElementType.ARRAY_ACCESS_EXPRESSION);
            startMarker.drop();
            return arrayAccess;
          }
          builder.advanceLexer();

          arrayAccess.done(JavaElementType.ARRAY_ACCESS_EXPRESSION);
          expr = arrayAccess;
        }
      }
      else if (tokenType == JavaTokenType.DOUBLE_COLON) {
        return parseMethodReference(builder, startMarker);
      }
      else {
        startMarker.drop();
        return expr;
      }
    }
  }

  @Nullable
  private PsiBuilder.Marker parsePrimaryExpressionStart(final PsiBuilder builder, final int mode) {
    IElementType tokenType = builder.getTokenType();

    if (tokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN || tokenType == JavaTokenType.STRING_TEMPLATE_BEGIN) {
      return parseStringTemplate(builder, null, tokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN);
    }

    if (ElementType.ALL_LITERALS.contains(tokenType)) {
      final PsiBuilder.Marker literal = builder.mark();
      builder.advanceLexer();
      literal.done(JavaElementType.LITERAL_EXPRESSION);
      return literal;
    }

    if (tokenType == JavaTokenType.LBRACE) {
      return parseArrayInitializer(builder);
    }

    if (tokenType == JavaTokenType.NEW_KEYWORD) {
      return parseNew(builder, null);
    }

    if (tokenType == JavaTokenType.LPARENTH) {
      return parseTupleOrLambdaOrParens( builder, mode );
    }

    if (TYPE_START.contains(tokenType)) {
      final PsiBuilder.Marker mark = builder.mark();

      final ReferenceParser.TypeInfo typeInfo = myParser.getReferenceParser().parseTypeInfo(builder, 0);
      if (typeInfo != null) {
        boolean optionalClassKeyword = typeInfo.isPrimitive || typeInfo.isArray;
        if (optionalClassKeyword || !typeInfo.hasErrors && typeInfo.isParameterized) {
          final PsiBuilder.Marker result = parseClassAccessOrMethodReference(builder, mark, optionalClassKeyword);
          if (result != null) {
            return result;
          }
        }
      }

      mark.rollbackTo();
    }

    PsiBuilder.Marker annotation = null;
    if (tokenType == JavaTokenType.AT) {
      annotation = myParser.getDeclarationParser().parseAnnotations(builder);
      tokenType = builder.getTokenType();
    }

    if (tokenType == JavaTokenType.VAR_KEYWORD) {
      builder.remapCurrentToken(tokenType = JavaTokenType.IDENTIFIER);
    }
    if (tokenType == JavaTokenType.IDENTIFIER) {
      if (!BitUtil.isSet(mode, FORBID_LAMBDA_MASK) && builder.lookAhead(1) == JavaTokenType.ARROW) {
        return parseLambdaExpression(builder, false);
      }

      final PsiBuilder.Marker refExpr;
      if (annotation != null) {
        final PsiBuilder.Marker refParam = annotation.precede();
        refParam.doneBefore(JavaElementType.REFERENCE_PARAMETER_LIST, annotation);
        refExpr = refParam.precede();
      }
      else {
        refExpr = builder.mark();
        builder.mark().done(JavaElementType.REFERENCE_PARAMETER_LIST);
      }

      builder.advanceLexer();
      refExpr.done(JavaElementType.REFERENCE_EXPRESSION);
      return refExpr;
    }

    if (annotation != null) {
      annotation.rollbackTo();
      tokenType = builder.getTokenType();
    }

    PsiBuilder.Marker expr = null;
    if (tokenType == JavaTokenType.LT) {
      expr = builder.mark();

      if (!myParser.getReferenceParser().parseReferenceParameterList(builder, false, false)) {
        expr.rollbackTo();
        return null;
      }

      tokenType = builder.getTokenType();
      if (!THIS_OR_SUPER.contains(tokenType)) {
        expr.rollbackTo();
        return null;
      }
    }

    if (THIS_OR_SUPER.contains(tokenType)) {
      if (expr == null) {
        expr = builder.mark();
        builder.mark().done(JavaElementType.REFERENCE_PARAMETER_LIST);
      }
      builder.advanceLexer();
      expr.done(builder.getTokenType() == JavaTokenType.LPARENTH
        ? JavaElementType.REFERENCE_EXPRESSION
        : tokenType == JavaTokenType.THIS_KEYWORD
        ? JavaElementType.THIS_EXPRESSION
        : JavaElementType.SUPER_EXPRESSION);
      return expr;
    }

    return null;
  }

  private PsiBuilder.Marker parseTupleOrLambdaOrParens( PsiBuilder builder, int mode )
  {
    if( !BitUtil.isSet( mode, FORBID_LAMBDA_MASK ) )
    {
      final PsiBuilder.Marker lambda = parseLambdaAfterParenth( builder );
      if( lambda != null )
      {
        return lambda;
      }
    }

    return parseTupleOrExpr( builder, true );
  }

  PsiBuilder.Marker parseTupleOrExpr( PsiBuilder builder, boolean requireParen )
  {
    final PsiBuilder.Marker parenth = builder.mark();
    if( requireParen )
    {
      builder.advanceLexer();
    }
    int argCount = 0;
    boolean colonFound = false;
    PsiBuilder.Marker argWhole;
    PsiBuilder.Marker arg;
    while( true )
    {
      argWhole = builder.mark();
      arg = builder.mark();
      IElementType tokenType = builder.getTokenType();
      if( tokenType == JavaTokenType.IDENTIFIER )
      {
        builder.advanceLexer();
        tokenType = builder.getTokenType();
        if( tokenType == JavaTokenType.COLON )
        {
          colonFound = true;
          arg.drop();
          builder.advanceLexer();
          PsiBuilder.Marker labeledValue = parse( builder );
          if( labeledValue == null )
          {
            error( builder, JavaPsiBundle.message( "expected.expression" ) );
          }
        }
        else
        {
          arg.rollbackTo();
          arg = parse( builder );
          if( arg == null )
          {
            error( builder, JavaPsiBundle.message( "expected.expression" ) );
          }
        }
        argCount++;
      }
      else
      {
        arg.rollbackTo();
        arg = parse( builder );
        if( arg != null || !requireParen )
        {
          argCount++;
        }
//        if( arg == null && requireParen )
//        {
//          error( builder, JavaPsiBundle.message( "expected.expression" ) );
//        }
      }

      tokenType = builder.getTokenType();

      boolean comma = tokenType == JavaTokenType.COMMA;

      if( argCount > 1 || colonFound || comma )
      {
        argWhole.done( TUPLE_VALUE_EXPRESSION );
      }
      else
      {
        argWhole.drop();
      }

      if( !comma )
      {
        break;
      }

      builder.advanceLexer();
    }
    if( requireParen && !expect( builder, JavaTokenType.RPARENTH ) )
    {
      if( argCount > 0 )
      {
        error( builder, JavaPsiBundle.message( "expected.rparen" ) );
      }
    }

    if( requireParen )
    {
      parenth.done( argCount != 1 || colonFound ? ManElementType.TUPLE_EXPRESSION : JavaElementType.PARENTH_EXPRESSION );
      return parenth;
    }
    else if( argCount > 1 || colonFound )
    {
      // return tuple or optional params tuple (neither has parens)
      parenth.done( ManElementType.TUPLE_EXPRESSION );
      return parenth;
    }
    else
    {
      parenth.drop();
      return arg;
    }
  }

  @NotNull
  private PsiBuilder.Marker parseArrayInitializer(PsiBuilder builder) {
    return parseArrayInitializer(builder, JavaElementType.ARRAY_INITIALIZER_EXPRESSION, this::parse, "expected.expression");
  }

  @NotNull
  public PsiBuilder.Marker parseArrayInitializer(@NotNull PsiBuilder builder,
                                                 @NotNull IElementType type,
                                                 @NotNull Function<? super PsiBuilder, PsiBuilder.Marker> elementParser,
                                                 @NotNull @PropertyKey(resourceBundle = BUNDLE) String missingElementKey) {
    PsiBuilder.Marker arrayInit = builder.mark();
    builder.advanceLexer();

    boolean first = true;
    while (true) {
      if (builder.getTokenType() == JavaTokenType.RBRACE) {
        builder.advanceLexer();
        break;
      }

      if (builder.getTokenType() == null) {
        error(builder, JavaPsiBundle.message("expected.rbrace"));
        break;
      }

      if (elementParser.apply(builder) == null) {
        if (builder.getTokenType() == JavaTokenType.COMMA) {
          if (first && builder.lookAhead(1) == JavaTokenType.RBRACE) {
            advance(builder, 2);
            break;
          }
          builder.error(JavaPsiBundle.message(missingElementKey));
        }
        else if (builder.getTokenType() != JavaTokenType.RBRACE) {
          error(builder, JavaPsiBundle.message("expected.rbrace"));
          break;
        }
      }

      first = false;

      IElementType tokenType = builder.getTokenType();
      if (!expect(builder, JavaTokenType.COMMA) && tokenType != JavaTokenType.RBRACE) {
        error(builder, JavaPsiBundle.message("expected.comma"));
      }
    }

    arrayInit.done(type);
    return arrayInit;
  }

  private PsiBuilder.Marker parseStringTemplate(PsiBuilder builder, PsiBuilder.Marker start, boolean textBlock) {
    final PsiBuilder.Marker templateExpression = start == null ? builder.mark() : start.precede();
    final PsiBuilder.Marker template = builder.mark();
    IElementType tokenType;
    do {
      builder.advanceLexer();
      tokenType = builder.getTokenType();
      if (textBlock
          ? tokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_MID || tokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_END
          : tokenType == JavaTokenType.STRING_TEMPLATE_MID || tokenType == JavaTokenType.STRING_TEMPLATE_END) {
        emptyExpression(builder);
      }
      else {
        parse(builder);
        tokenType = builder.getTokenType();
      }
    } while (textBlock ? tokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_MID : tokenType == JavaTokenType.STRING_TEMPLATE_MID);
    if (textBlock ? tokenType != JavaTokenType.TEXT_BLOCK_TEMPLATE_END : tokenType != JavaTokenType.STRING_TEMPLATE_END) {
      builder.error(JavaPsiBundle.message("expected.template.fragment"));
    }
    else {
      builder.advanceLexer();
    }
    template.done(JavaElementType.TEMPLATE);
    templateExpression.done(JavaElementType.TEMPLATE_EXPRESSION);
    return templateExpression;
  }

  @NotNull
  private PsiBuilder.Marker parseNew(PsiBuilder builder, @Nullable PsiBuilder.Marker start) {
    PsiBuilder.Marker newExpr = (start != null ? start.precede() : builder.mark());
    builder.advanceLexer();

    myParser.getReferenceParser().parseReferenceParameterList(builder, false, true);

    PsiBuilder.Marker refOrType;
    PsiBuilder.Marker anno = myParser.getDeclarationParser().parseAnnotations(builder);
    IElementType tokenType = builder.getTokenType();
    if (tokenType == JavaTokenType.IDENTIFIER) {
      rollbackTo(anno);
      refOrType = myParser.getReferenceParser().parseJavaCodeReference(builder, true, true, true, true);
      if (refOrType == null) {
        error(builder, JavaPsiBundle.message("expected.identifier"));
        newExpr.done(JavaElementType.NEW_EXPRESSION);
        return newExpr;
      }
    }
    else if (ElementType.PRIMITIVE_TYPE_BIT_SET.contains(tokenType)) {
      refOrType = null;
      builder.advanceLexer();
    }
    else {
      error(builder, JavaPsiBundle.message("expected.identifier"));
      newExpr.done(JavaElementType.NEW_EXPRESSION);
      return newExpr;
    }

    if (refOrType != null && builder.getTokenType() == JavaTokenType.LPARENTH) {
      parseArgumentList(builder);
      if (builder.getTokenType() == JavaTokenType.LBRACE) {
        final PsiBuilder.Marker classElement = refOrType.precede();
        myParser.getDeclarationParser().parseClassBodyWithBraces(builder, false, false);
        classElement.done(JavaElementType.ANONYMOUS_CLASS);
      }
      newExpr.done(JavaElementType.NEW_EXPRESSION);
      return newExpr;
    }

    anno = myParser.getDeclarationParser().parseAnnotations(builder);

    if (builder.getTokenType() != JavaTokenType.LBRACKET) {
      rollbackTo(anno);
      error(builder, JavaPsiBundle.message(refOrType == null ? "expected.lbracket" : "expected.lparen.or.lbracket"));
      newExpr.done(JavaElementType.NEW_EXPRESSION);
      return newExpr;
    }

    int bracketCount = 0;
    int dimCount = 0;
    while (true) {
      anno = myParser.getDeclarationParser().parseAnnotations(builder);

      if (builder.getTokenType() != JavaTokenType.LBRACKET) {
        rollbackTo(anno);
        break;
      }
      builder.advanceLexer();

      if (bracketCount == dimCount) {
        final PsiBuilder.Marker dimExpr = parse(builder);
        if (dimExpr != null) {
          dimCount++;
        }
      }
      bracketCount++;

      if (!expectOrError(builder, JavaTokenType.RBRACKET, "expected.rbracket")) {
        newExpr.done(JavaElementType.NEW_EXPRESSION);
        return newExpr;
      }
    }

    if (dimCount == 0) {
      if (builder.getTokenType() == JavaTokenType.LBRACE) {
        parseArrayInitializer(builder);
      }
      else {
        error(builder, JavaPsiBundle.message("expected.array.initializer"));
      }
    }

    newExpr.done(JavaElementType.NEW_EXPRESSION);
    return newExpr;
  }

  @Nullable
  private PsiBuilder.Marker parseClassAccessOrMethodReference(PsiBuilder builder) {
    PsiBuilder.Marker expr = builder.mark();

    boolean primitive = ElementType.PRIMITIVE_TYPE_BIT_SET.contains(builder.getTokenType());
    if (myParser.getReferenceParser().parseType(builder, 0) == null) {
      expr.drop();
      return null;
    }

    PsiBuilder.Marker result = parseClassAccessOrMethodReference(builder, expr, primitive);
    if (result == null) expr.rollbackTo();
    return result;
  }

  @Nullable
  private PsiBuilder.Marker parseClassAccessOrMethodReference(PsiBuilder builder, PsiBuilder.Marker expr, boolean optionalClassKeyword) {
    IElementType tokenType = builder.getTokenType();
    if (tokenType == JavaTokenType.DOT) {
      return parseClassObjectAccess(builder, expr, optionalClassKeyword);
    }
    else if (tokenType == JavaTokenType.DOUBLE_COLON) {
      return parseMethodReference(builder, expr);
    }

    return null;
  }

  @Nullable
  private static PsiBuilder.Marker parseClassObjectAccess(PsiBuilder builder, PsiBuilder.Marker expr, boolean optionalClassKeyword) {
    final PsiBuilder.Marker mark = builder.mark();
    builder.advanceLexer();

    if (builder.getTokenType() == JavaTokenType.CLASS_KEYWORD) {
      mark.drop();
      builder.advanceLexer();
    }
    else {
      if (!optionalClassKeyword) return null;
      mark.rollbackTo();
      builder.error(JavaPsiBundle.message("class.literal.expected"));
    }

    expr.done(JavaElementType.CLASS_OBJECT_ACCESS_EXPRESSION);
    return expr;
  }

  @NotNull
  private PsiBuilder.Marker parseMethodReference(final PsiBuilder builder, final PsiBuilder.Marker start) {
    builder.advanceLexer();

    myParser.getReferenceParser().parseReferenceParameterList(builder, false, false);

    if (!expect(builder, JavaTokenType.IDENTIFIER) && !expect(builder, JavaTokenType.NEW_KEYWORD)) {
      error(builder, JavaPsiBundle.message("expected.identifier"));
    }

    start.done(JavaElementType.METHOD_REF_EXPRESSION);
    return start;
  }

  @Nullable
  private PsiBuilder.Marker parseLambdaAfterParenth(final PsiBuilder builder) {
    final boolean isLambda;
    final boolean isTyped;

    final IElementType nextToken1 = builder.lookAhead(1);
    final IElementType nextToken2 = builder.lookAhead(2);
    if (nextToken1 == JavaTokenType.RPARENTH && nextToken2 == JavaTokenType.ARROW) {
      isLambda = true;
      isTyped = false;
    }
    else if (nextToken1 == JavaTokenType.AT ||
             ElementType.MODIFIER_BIT_SET.contains(nextToken1) || ElementType.PRIMITIVE_TYPE_BIT_SET.contains(nextToken1)) {
      isLambda = true;
      isTyped = true;
    }
    else if (nextToken1 == JavaTokenType.IDENTIFIER) {
      if (nextToken2 == JavaTokenType.COMMA || nextToken2 == JavaTokenType.RPARENTH && builder.lookAhead(3) == JavaTokenType.ARROW) {
        isLambda = true;
        isTyped = false;
      }
      else if (nextToken2 == JavaTokenType.ARROW) {
        isLambda = false;
        isTyped = false;
      }
      else {
        boolean lambda = false;

        PsiBuilder.Marker marker = builder.mark();
        builder.advanceLexer();
        ReferenceParser.TypeInfo typeInfo = myParser.getReferenceParser().parseTypeInfo(
          builder, ReferenceParser.EAT_LAST_DOT | ReferenceParser.ELLIPSIS | ReferenceParser.WILDCARD);
        if (typeInfo != null) {
          IElementType t = builder.getTokenType();
          if (t == JavaTokenType.IDENTIFIER ||
              t == JavaTokenType.THIS_KEYWORD ||
              t == JavaTokenType.RPARENTH && builder.lookAhead(1) == JavaTokenType.ARROW) {
            lambda = true;
          }
        }
        marker.rollbackTo();

        isLambda = lambda;
        isTyped = true;
      }
    }
    else {
      isLambda = false;
      isTyped = false;
    }

    return isLambda ? parseLambdaExpression(builder, isTyped) : null;
  }

  @Nullable
  private PsiBuilder.Marker parseLambdaExpression(final PsiBuilder builder, final boolean typed) {
    final PsiBuilder.Marker start = builder.mark();

    myParser.getDeclarationParser().parseLambdaParameterList(builder, typed);

    if (!expect(builder, JavaTokenType.ARROW)) {
      start.rollbackTo();
      return null;
    }

    final PsiBuilder.Marker body;
    if (builder.getTokenType() == JavaTokenType.LBRACE) {
      body = myParser.getStatementParser().parseCodeBlock(builder);
    }
    else {
      body = parse(builder);
    }

    if (body == null) {
      builder.error(JavaPsiBundle.message("expected.lbrace"));
    }

    start.done(JavaElementType.LAMBDA_EXPRESSION);
    return start;
  }

  @NotNull
  public PsiBuilder.Marker parseArgumentList(final PsiBuilder builder) {
//    builder.setDebugMode( true );
    final PsiBuilder.Marker list = builder.mark();
    final PsiBuilder.Marker tupleExpr = builder.mark();
    builder.advanceLexer();

    boolean colon = false;
    boolean first = true;
    while( true )
    {
      final IElementType tokenType = builder.getTokenType();
      if( first && (ARGS_LIST_END.contains( tokenType ) || builder.eof()) ) break;
      if( !first && !ARGS_LIST_CONTINUE.contains( tokenType ) ) break;

      boolean hasError = false;
      if( !first )
      {
        if( builder.getTokenType() == JavaTokenType.COMMA )
        {
          builder.advanceLexer();
        }
        else
        {
          hasError = true;
          error( builder, JavaPsiBundle.message( "expected.comma.or.rparen" ) );
          emptyExpression( builder );
        }
      }
      first = false;

      PsiBuilder.Marker argWhole = builder.mark();
      boolean isTupleItem = false;
      if( builder.getTokenType() == JavaTokenType.IDENTIFIER && builder.lookAhead( 1 ) == JavaTokenType.COLON )
      {
        builder.advanceLexer();
        builder.advanceLexer();
        isTupleItem = true;
        colon = true;
      }
      final PsiBuilder.Marker arg = parse( builder );
      if( arg == null )
      {
        if( !hasError )
        {
          error( builder, JavaPsiBundle.message( "expected.expression" ) );
          emptyExpression( builder );
        }
        if( !ARGS_LIST_CONTINUE.contains( builder.getTokenType() ) )
        {
          if( isTupleItem )
          {
            argWhole.done( TUPLE_VALUE_EXPRESSION );
          }
          else
          {
            argWhole.drop();
          }
          break;
        }
        if( builder.getTokenType() != JavaTokenType.COMMA && !builder.eof() )
        {
          builder.advanceLexer();
        }
      }

      if( arg != null && isTupleItem )
      {
        argWhole.done( TUPLE_VALUE_EXPRESSION );
      }
      else
      {
        argWhole.drop();
      }
    }
    final boolean closed = expectOrError(builder, JavaTokenType.RPARENTH, "expected.rparen");

    if( colon )
    {
      tupleExpr.done( TUPLE_EXPRESSION );
    }
    else
    {
      tupleExpr.drop();
    }

    list.done(JavaElementType.EXPRESSION_LIST);
    if (!closed) {
      list.setCustomEdgeTokenBinders(null, WhitespacesBinders.GREEDY_RIGHT_BINDER);
    }
    return list;
  }

  private static void emptyExpression(final PsiBuilder builder) {
    emptyElement(builder, JavaElementType.EMPTY_EXPRESSION);
  }

  @Nullable
  private static IElementType getGtTokenType(final PsiBuilder builder) {
    IElementType tokenType = builder.getTokenType();
    if (tokenType != JavaTokenType.GT) return tokenType;

    if (builder.rawLookup(1) == JavaTokenType.GT) {
      if (builder.rawLookup(2) == JavaTokenType.GT) {
        if (builder.rawLookup(3) == JavaTokenType.EQ) {
          tokenType = JavaTokenType.GTGTGTEQ;
        }
        else {
          tokenType = JavaTokenType.GTGTGT;
        }
      }
      else if (builder.rawLookup(2) == JavaTokenType.EQ) {
        tokenType = JavaTokenType.GTGTEQ;
      }
      else {
        tokenType = JavaTokenType.GTGT;
      }
    }
    else if (builder.rawLookup(1) == JavaTokenType.EQ) {
      tokenType = JavaTokenType.GE;
    }

    return tokenType;
  }

  private static void advanceGtToken(final PsiBuilder builder, final IElementType type) {
    final PsiBuilder.Marker gtToken = builder.mark();

    if (type == JavaTokenType.GTGTGTEQ) {
      advance(builder, 4);
    }
    else if (type == JavaTokenType.GTGTGT || type == JavaTokenType.GTGTEQ) {
      advance(builder, 3);
    }
    else if (type == JavaTokenType.GTGT || type == JavaTokenType.GE) {
      advance(builder, 2);
    }
    else {
      gtToken.drop();
      builder.advanceLexer();
      return;
    }

    gtToken.collapse(type);
  }
}
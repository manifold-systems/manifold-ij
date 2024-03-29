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

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.WhitespacesBinders;
import com.intellij.lang.java.parser.DeclarationParser;
import com.intellij.lang.java.parser.ExpressionParser;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.StatementParser;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.psi.tree.TokenSet;
import manifold.ext.rt.api.Jailbreak;
import manifold.util.ReflectUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


import static com.intellij.lang.PsiBuilderUtil.*;
import static com.intellij.lang.PsiBuilderUtil.expect;
import static com.intellij.lang.java.parser.JavaParserUtil.*;
import static com.intellij.lang.java.parser.JavaParserUtil.done;

public class ManStatementParser extends StatementParser {
  private static final TokenSet YIELD_STMT_INDICATOR_TOKENS = TokenSet.create(
    JavaTokenType.PLUS, JavaTokenType.MINUS, JavaTokenType.EXCL,

    JavaTokenType.SUPER_KEYWORD, JavaTokenType.THIS_KEYWORD,

    JavaTokenType.TRUE_KEYWORD, JavaTokenType.FALSE_KEYWORD, JavaTokenType.NULL_KEYWORD,

    JavaTokenType.STRING_LITERAL, JavaTokenType.INTEGER_LITERAL, JavaTokenType.DOUBLE_LITERAL,
    JavaTokenType.FLOAT_LITERAL, JavaTokenType.LONG_LITERAL, JavaTokenType.CHARACTER_LITERAL,

    JavaTokenType.IDENTIFIER, JavaTokenType.SWITCH_KEYWORD, JavaTokenType.NEW_KEYWORD,

    JavaTokenType.LPARENTH,

    // recovery
    JavaTokenType.RBRACE, JavaTokenType.SEMICOLON, JavaTokenType.CASE_KEYWORD
  );

  private enum BraceMode {
    TILL_FIRST, TILL_LAST
  }

  private static final TokenSet TRY_CLOSERS_SET = TokenSet.create(JavaTokenType.CATCH_KEYWORD, JavaTokenType.FINALLY_KEYWORD);

  private final JavaParser myParser;

  public ManStatementParser(@NotNull JavaParser javaParser) {
    super(javaParser);
    myParser = javaParser;
  }

  @Nullable
  public PsiBuilder.Marker parseCodeBlock(@NotNull PsiBuilder builder) {
    return parseCodeBlock(builder, false);
  }

  @Nullable
  public PsiBuilder.Marker parseCodeBlock(PsiBuilder builder, boolean isStatement) {
    if (builder.getTokenType() != JavaTokenType.LBRACE) return null;
    if (isStatement && isParseStatementCodeBlocksDeep(builder)) return parseCodeBlockDeep(builder, false);
    return parseBlockLazy(builder, JavaTokenType.LBRACE, JavaTokenType.RBRACE, JavaElementType.CODE_BLOCK);
  }

  @Nullable
  public PsiBuilder.Marker parseCodeBlockDeep(@NotNull PsiBuilder builder, boolean parseUntilEof) {
    if (builder.getTokenType() != JavaTokenType.LBRACE) return null;

    PsiBuilder.Marker codeBlock = builder.mark();
    builder.advanceLexer();

    parseStatements(builder, parseUntilEof ? BraceMode.TILL_LAST : BraceMode.TILL_FIRST);

    boolean greedyBlock = !expectOrError(builder, JavaTokenType.RBRACE, "expected.rbrace");
    builder.getTokenType(); // eat spaces

    done(codeBlock, JavaElementType.CODE_BLOCK);
    if (greedyBlock) {
      codeBlock.setCustomEdgeTokenBinders(null, WhitespacesBinders.GREEDY_RIGHT_BINDER);
    }
    return codeBlock;
  }

  public void parseStatements(@NotNull PsiBuilder builder) {
    parseStatements(builder, null);
  }

  private void parseStatements(PsiBuilder builder, @Nullable BraceMode braceMode) {
    while (builder.getTokenType() != null) {
      PsiBuilder.Marker statement = parseStatement(builder);
      if (statement != null) continue;

      IElementType tokenType = builder.getTokenType();
      if (tokenType == JavaTokenType.RBRACE &&
          (braceMode == BraceMode.TILL_FIRST || braceMode == BraceMode.TILL_LAST && builder.lookAhead(1) == null)) {
        break;
      }

      PsiBuilder.Marker error = builder.mark();
      builder.advanceLexer();
      if (tokenType == JavaTokenType.ELSE_KEYWORD) {
        error.error(JavaErrorBundle.message("else.without.if"));
      }
      else if (tokenType == JavaTokenType.CATCH_KEYWORD) {
        error.error(JavaErrorBundle.message("catch.without.try"));
      }
      else if (tokenType == JavaTokenType.FINALLY_KEYWORD) {
        error.error(JavaErrorBundle.message("finally.without.try"));
      }
      else {
        error.error(JavaErrorBundle.message("unexpected.token"));
      }
    }
  }

  @Nullable
  public PsiBuilder.Marker parseStatement(PsiBuilder builder) {
    IElementType tokenType = builder.getTokenType();
    if (tokenType == JavaTokenType.IF_KEYWORD) {
      return parseIfStatement(builder);
    }
    else if (tokenType == JavaTokenType.WHILE_KEYWORD) {
      return parseWhileStatement(builder);
    }
    else if (tokenType == JavaTokenType.FOR_KEYWORD) {
      return parseForStatement(builder);
    }
    else if (tokenType == JavaTokenType.DO_KEYWORD) {
      return parseDoWhileStatement(builder);
    }
    else if (tokenType == JavaTokenType.SWITCH_KEYWORD) {
      return parseSwitchStatement(builder);
    }
    else if (tokenType == JavaTokenType.CASE_KEYWORD || tokenType == JavaTokenType.DEFAULT_KEYWORD) {
      return parseSwitchLabelStatement(builder);
    }
    else if (tokenType == JavaTokenType.BREAK_KEYWORD) {
      return parseBreakStatement(builder);
    }
    else if (isStmtYieldToken(builder, tokenType)) {
      return parseYieldStatement(builder);
    }
    else if (tokenType == JavaTokenType.CONTINUE_KEYWORD) {
      return parseContinueStatement(builder);
    }
    else if (tokenType == JavaTokenType.RETURN_KEYWORD) {
      return parseReturnStatement(builder);
    }
    else if (tokenType == JavaTokenType.THROW_KEYWORD) {
      return parseThrowStatement(builder);
    }
    else if (tokenType == JavaTokenType.SYNCHRONIZED_KEYWORD) {
      return parseSynchronizedStatement(builder);
    }
    else if (tokenType == JavaTokenType.TRY_KEYWORD) {
      return parseTryStatement(builder);
    }
    else if (tokenType == JavaTokenType.ASSERT_KEYWORD) {
      return parseAssertStatement(builder);
    }
    else if (tokenType == JavaTokenType.LBRACE) {
      return parseBlockStatement(builder);
    }
    else if (tokenType instanceof ILazyParseableElementType) {
      builder.advanceLexer();
      return null;
    }
    else if (tokenType == JavaTokenType.SEMICOLON) {
      PsiBuilder.Marker empty = builder.mark();
      builder.advanceLexer();
      done(empty, JavaElementType.EMPTY_STATEMENT);
      return empty;
    }
    else if (tokenType == JavaTokenType.IDENTIFIER || tokenType == JavaTokenType.AT) {
      PsiBuilder.Marker refPos = builder.mark();
      myParser.getDeclarationParser().parseAnnotations(builder);
      skipQualifiedName(builder);
      IElementType suspectedLT = builder.getTokenType(), next = builder.lookAhead(1);
      refPos.rollbackTo();

      if (suspectedLT == JavaTokenType.LT || suspectedLT == JavaTokenType.DOT && next == JavaTokenType.AT) {
        PsiBuilder.Marker declStatement = builder.mark();

        if (myParser.getDeclarationParser().parse(builder, DeclarationParser.Context.CODE_BLOCK) != null) {
          done(declStatement, JavaElementType.DECLARATION_STATEMENT);
          return declStatement;
        }

        PsiBuilder.Marker type = myParser.getReferenceParser().parseType(builder, 0);
        if (type == null || builder.getTokenType() != JavaTokenType.DOUBLE_COLON) {
          error(builder, JavaErrorBundle.message("expected.identifier"));
          if (type == null) builder.advanceLexer();
          done(declStatement, JavaElementType.DECLARATION_STATEMENT);
          return declStatement;
        }

        declStatement.rollbackTo();  // generic type followed by the double colon is a good candidate for being a constructor reference
      }
    }

    PsiBuilder.Marker pos = builder.mark();
    PsiBuilder.Marker expr = parseAssignmentExpr( builder, true );

    if (expr != null) {
      int count = 1;
      PsiBuilder.Marker list = expr.precede();
      PsiBuilder.Marker statement = list.precede();
      while (builder.getTokenType() == JavaTokenType.COMMA) {
        PsiBuilder.Marker commaPos = builder.mark();
        builder.advanceLexer();
        PsiBuilder.Marker expr1 = parseAssignmentExpr( builder, true );
        if (expr1 == null) {
          commaPos.rollbackTo();
          break;
        }
        commaPos.drop();
        count++;
      }
      if (count > 1) {
        pos.drop();
        done(list, JavaElementType.EXPRESSION_LIST);
        semicolon(builder);
        done(statement, JavaElementType.EXPRESSION_LIST_STATEMENT);
        return statement;
      }
      if (exprType(expr) != JavaElementType.REFERENCE_EXPRESSION) {
        drop(list, pos);
        semicolon(builder);
        done(statement, JavaElementType.EXPRESSION_STATEMENT);
        return statement;
      }
      pos.rollbackTo();
    }
    else {
      pos.drop();
    }

    PsiBuilder.Marker decl = myParser.getDeclarationParser().parse(builder, DeclarationParser.Context.CODE_BLOCK);
    if (decl != null) {
      PsiBuilder.Marker statement = decl.precede();
      done(statement, JavaElementType.DECLARATION_STATEMENT);
      return statement;
    }

    if (builder.getTokenType() == JavaTokenType.IDENTIFIER && builder.lookAhead(1) == JavaTokenType.COLON) {
      PsiBuilder.Marker statement = builder.mark();
      advance(builder, 2);
      parseStatement(builder);
      done(statement, JavaElementType.LABELED_STATEMENT);
      return statement;
    }

    if (expr != null) {
      PsiBuilder.Marker statement = builder.mark();
      parseAssignmentExpr( builder, false );
      semicolon(builder);
      done(statement, JavaElementType.EXPRESSION_STATEMENT);
      return statement;
    }

    return null;
  }

  private PsiBuilder.Marker parseAssignmentExpr( PsiBuilder builder, boolean lhs )
  {
    @Jailbreak ExpressionParser exprParser = myParser.getExpressionParser();
    return exprParser instanceof ManExpressionParser
     ? ((ManExpressionParser)exprParser).parseAssignment( builder, 0, lhs )
      //todo: in 2023 EA they changed the ExpressionParser, calling
      : exprParser.useNewImplementation
        ? exprParser.myNewExpressionParser.parse( builder, 0 )
        : exprParser.myOldExpressionParser.jailbreak().parseAssignment( builder, 0 );
  }

  private static boolean isStmtYieldToken(@NotNull PsiBuilder builder, IElementType tokenType) {
    if (!(tokenType == JavaTokenType.IDENTIFIER &&
          PsiKeyword.YIELD.equals(builder.getTokenText()) &&
          getLanguageLevel(builder).isAtLeast(LanguageLevel.JDK_14))) {
      return false;
    }
    // we prefer to parse it as yield stmt wherever possible (even in incomplete syntax)
    PsiBuilder.Marker maybeYieldStmt = builder.mark();
    builder.advanceLexer();
    IElementType tokenAfterYield = builder.getTokenType();
    if (tokenAfterYield == null || YIELD_STMT_INDICATOR_TOKENS.contains(tokenAfterYield)) {
      maybeYieldStmt.rollbackTo();
      return true;
    }
    if (JavaTokenType.PLUSPLUS.equals(tokenAfterYield) || JavaTokenType.MINUSMINUS.equals(tokenAfterYield)) {
      builder.advanceLexer();
      boolean isYieldStmt = !builder.getTokenType().equals(JavaTokenType.SEMICOLON);
      maybeYieldStmt.rollbackTo();
      return isYieldStmt;
    }
    maybeYieldStmt.rollbackTo();
    return false;
  }

  private static void skipQualifiedName(PsiBuilder builder) {
    if (!expect(builder, JavaTokenType.IDENTIFIER)) return;
    while (builder.getTokenType() == JavaTokenType.DOT && builder.lookAhead(1) == JavaTokenType.IDENTIFIER) {
      advance(builder, 2);
    }
  }

  @NotNull
  private PsiBuilder.Marker parseIfStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    if (parseExprInParenth(builder)) {
      PsiBuilder.Marker thenStatement = parseStatement(builder);
      if (thenStatement == null) {
        error(builder, JavaErrorBundle.message("expected.statement"));
      }
      else if (expect(builder, JavaTokenType.ELSE_KEYWORD)) {
        PsiBuilder.Marker elseStatement = parseStatement(builder);
        if (elseStatement == null) {
          error(builder, JavaErrorBundle.message("expected.statement"));
        }
      }
    }

    done(statement, JavaElementType.IF_STATEMENT);
    return statement;
  }

  @NotNull
  private PsiBuilder.Marker parseWhileStatement(PsiBuilder builder) {
    return parseExprInParenthWithBlock(builder, JavaElementType.WHILE_STATEMENT, false);
  }

  @NotNull
  private PsiBuilder.Marker parseForStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    if (!expect(builder, JavaTokenType.LPARENTH)) {
      error(builder, JavaErrorBundle.message("expected.lparen"));
      done(statement, JavaElementType.FOR_STATEMENT);
      return statement;
    }

    PsiBuilder.Marker afterParenth = builder.mark();
    PsiBuilder.Marker param = myParser.getDeclarationParser().parseParameter(builder, false, false, true);
    if (param == null || exprType(param) != JavaElementType.PARAMETER || builder.getTokenType() != JavaTokenType.COLON) {
      afterParenth.rollbackTo();
      return parseForLoopFromInitializer(builder, statement);
    }
    else {
      afterParenth.drop();
      return parseForEachFromColon(builder, statement);
    }
  }

  @NotNull
  private PsiBuilder.Marker parseForLoopFromInitializer(PsiBuilder builder, PsiBuilder.Marker statement) {
    if (parseStatement(builder) == null) {
      error(builder, JavaErrorBundle.message("expected.statement"));
      if (!expect(builder, JavaTokenType.RPARENTH)) {
        done(statement, JavaElementType.FOR_STATEMENT);
        return statement;
      }
    }
    else {
      boolean missingSemicolon = false;
      if (getLastToken(builder) != JavaTokenType.SEMICOLON) {
        missingSemicolon = !expectOrError(builder, JavaTokenType.SEMICOLON, "expected.semicolon");
      }

      PsiBuilder.Marker expr = myParser.getExpressionParser().parse(builder);
      missingSemicolon &= expr == null;

      if (!expect(builder, JavaTokenType.SEMICOLON)) {
        if (!missingSemicolon) {
          error(builder, JavaErrorBundle.message("expected.semicolon"));
        }
        if (!expect(builder, JavaTokenType.RPARENTH)) {
          done(statement, JavaElementType.FOR_STATEMENT);
          return statement;
        }
      }
      else {
        parseForUpdateExpressions(builder);
        if (!expect(builder, JavaTokenType.RPARENTH)) {
          error(builder, JavaErrorBundle.message("expected.rparen"));
          done(statement, JavaElementType.FOR_STATEMENT);
          return statement;
        }
      }
    }

    PsiBuilder.Marker bodyStatement = parseStatement(builder);
    if (bodyStatement == null) {
      error(builder, JavaErrorBundle.message("expected.statement"));
    }

    done(statement, JavaElementType.FOR_STATEMENT);
    return statement;
  }

  private static IElementType getLastToken(PsiBuilder builder) {
    IElementType token;
    int offset = -1;
    while (ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains((token = builder.rawLookup(offset)))) offset--;
    return token;
  }

  private void parseForUpdateExpressions(PsiBuilder builder) {
    PsiBuilder.Marker expr = myParser.getExpressionParser().parse(builder);
    if (expr == null) return;

    PsiBuilder.Marker expressionStatement;
    if (builder.getTokenType() != JavaTokenType.COMMA) {
      expressionStatement = expr.precede();
      done(expressionStatement, JavaElementType.EXPRESSION_STATEMENT);
    }
    else {
      PsiBuilder.Marker expressionList = expr.precede();
      expressionStatement = expressionList.precede();

      do {
        builder.advanceLexer();
        PsiBuilder.Marker nextExpression = myParser.getExpressionParser().parse(builder);
        if (nextExpression == null) {
          error(builder, JavaErrorBundle.message("expected.expression"));
        }
      }
      while (builder.getTokenType() == JavaTokenType.COMMA);

      done(expressionList, JavaElementType.EXPRESSION_LIST);
      done(expressionStatement, JavaElementType.EXPRESSION_LIST_STATEMENT);
    }

    expressionStatement.setCustomEdgeTokenBinders(null, WhitespacesBinders.DEFAULT_RIGHT_BINDER);
  }

  @NotNull
  private PsiBuilder.Marker parseForEachFromColon(PsiBuilder builder, PsiBuilder.Marker statement) {
    builder.advanceLexer();

    if (myParser.getExpressionParser().parse(builder) == null) {
      error(builder, JavaErrorBundle.message("expected.expression"));
    }

    if (expectOrError(builder, JavaTokenType.RPARENTH, "expected.rparen") && parseStatement(builder) == null) {
      error(builder, JavaErrorBundle.message("expected.statement"));
    }

    done(statement, JavaElementType.FOREACH_STATEMENT);
    return statement;
  }

  @NotNull
  private PsiBuilder.Marker parseDoWhileStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    PsiBuilder.Marker body = parseStatement(builder);
    if (body == null) {
      error(builder, JavaErrorBundle.message("expected.statement"));
    }
    else if (!expect(builder, JavaTokenType.WHILE_KEYWORD)) {
      error(builder, JavaErrorBundle.message("expected.while"));
    }
    else if (parseExprInParenth(builder)) {
      semicolon(builder);
    }

    done(statement, JavaElementType.DO_WHILE_STATEMENT);
    return statement;
  }

  @NotNull
  private PsiBuilder.Marker parseSwitchStatement(PsiBuilder builder) {
    return parseExprInParenthWithBlock(builder, JavaElementType.SWITCH_STATEMENT, true);
  }

  private PsiBuilder.Marker parseSwitchLabelStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    boolean isCase = builder.getTokenType() == JavaTokenType.CASE_KEYWORD;
    builder.advanceLexer();

    if (isCase) {
      PsiBuilder.Marker expressionList = builder.mark();
      do {
        PsiBuilder.Marker nextExpression = myParser.getExpressionParser().parseCaseLabel(builder);
        if (nextExpression == null) {
          error(builder, JavaErrorBundle.message("expected.expression"));
        }
      }
      while (expect(builder, JavaTokenType.COMMA));
      done(expressionList, JavaElementType.CASE_LABEL_ELEMENT_LIST);
    }

    if (expect(builder, JavaTokenType.ARROW)) {
      PsiBuilder.Marker expr;
      if (builder.getTokenType() == JavaTokenType.LBRACE) {
        PsiBuilder.Marker body = builder.mark();
        parseCodeBlock(builder, true);
        body.done(JavaElementType.BLOCK_STATEMENT);
        if (builder.getTokenType() == JavaTokenType.SEMICOLON) {
          PsiBuilder.Marker mark = builder.mark();
          while (builder.getTokenType() == JavaTokenType.SEMICOLON) builder.advanceLexer();
          mark.error(JavaErrorBundle.message("expected.switch.label"));
        }
      }
      else if (builder.getTokenType() == JavaTokenType.THROW_KEYWORD) {
        parseThrowStatement(builder);
      }
      else if ((expr = myParser.getExpressionParser().parse(builder)) != null) {
        PsiBuilder.Marker body = expr.precede();
        semicolon(builder);
        body.done(JavaElementType.EXPRESSION_STATEMENT);
      }
      else {
        error(builder, JavaErrorBundle.message("expected.switch.rule"));
        expect(builder, JavaTokenType.SEMICOLON);
      }
      done(statement, JavaElementType.SWITCH_LABELED_RULE);
    }
    else {
      expectOrError(builder, JavaTokenType.COLON, "expected.colon");
      done(statement, JavaElementType.SWITCH_LABEL_STATEMENT);
    }

    return statement;
  }

  @NotNull
  private static PsiBuilder.Marker parseBreakStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();
    expect(builder, JavaTokenType.IDENTIFIER);
    semicolon(builder);
    done(statement, JavaElementType.BREAK_STATEMENT);
    return statement;
  }

  @NotNull
  private PsiBuilder.Marker parseYieldStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    builder.remapCurrentToken(JavaTokenType.YIELD_KEYWORD);
    builder.advanceLexer();

    if (myParser.getExpressionParser().parse(builder) == null) {
      error(builder, JavaErrorBundle.message("expected.expression"));
    }
    else {
      semicolon(builder);
    }

    done(statement, JavaElementType.YIELD_STATEMENT);
    return statement;
  }

  @NotNull
  private static PsiBuilder.Marker parseContinueStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();
    expect(builder, JavaTokenType.IDENTIFIER);
    semicolon(builder);
    done(statement, JavaElementType.CONTINUE_STATEMENT);
    return statement;
  }

  @NotNull
  private PsiBuilder.Marker parseReturnStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();
    ExpressionParser expressionParser = myParser.getExpressionParser();
    if( expressionParser instanceof ManExpressionParser )
    {
      ((ManExpressionParser)expressionParser).parseTupleOrExpr( builder, false );
    }
    else
    {
      expressionParser.parse( builder );
    }
    semicolon(builder);
    done(statement, JavaElementType.RETURN_STATEMENT);
    return statement;
  }

  @NotNull
  private PsiBuilder.Marker parseThrowStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    if (myParser.getExpressionParser().parse(builder) == null) {
      error(builder, JavaErrorBundle.message("expected.expression"));
    }
    else {
      semicolon(builder);
    }

    done(statement, JavaElementType.THROW_STATEMENT);
    return statement;
  }

  @NotNull
  private PsiBuilder.Marker parseSynchronizedStatement(PsiBuilder builder) {
    return parseExprInParenthWithBlock(builder, JavaElementType.SYNCHRONIZED_STATEMENT, true);
  }

  @NotNull
  private PsiBuilder.Marker parseTryStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    boolean hasResourceList = builder.getTokenType() == JavaTokenType.LPARENTH;
    if (hasResourceList) {
      myParser.getDeclarationParser().parseResourceList(builder);
    }

    PsiBuilder.Marker tryBlock = parseCodeBlock(builder, true);
    if (tryBlock == null) {
      error(builder, JavaErrorBundle.message("expected.lbrace"));
    }
    else if (!hasResourceList && !TRY_CLOSERS_SET.contains(builder.getTokenType())) {
      error(builder, JavaErrorBundle.message("expected.catch.or.finally"));
    }
    else {
      while (builder.getTokenType() == JavaTokenType.CATCH_KEYWORD) {
        if (!parseCatchBlock(builder)) break;
      }

      if (expect(builder, JavaTokenType.FINALLY_KEYWORD)) {
        PsiBuilder.Marker finallyBlock = parseCodeBlock(builder, true);
        if (finallyBlock == null) {
          error(builder, JavaErrorBundle.message("expected.lbrace"));
        }
      }
    }

    done(statement, JavaElementType.TRY_STATEMENT);
    return statement;
  }

  public boolean parseCatchBlock(@NotNull PsiBuilder builder) {
    assert builder.getTokenType() == JavaTokenType.CATCH_KEYWORD : builder.getTokenType();
    PsiBuilder.Marker section = builder.mark();
    builder.advanceLexer();

    if (!expect(builder, JavaTokenType.LPARENTH)) {
      error(builder, JavaErrorBundle.message("expected.lparen"));
      done(section, JavaElementType.CATCH_SECTION);
      return false;
    }

    PsiBuilder.Marker param = myParser.getDeclarationParser().parseParameter(builder, false, true, false);
    if (param == null) {
      error(builder, JavaErrorBundle.message("expected.parameter"));
    }

    if (!expect(builder, JavaTokenType.RPARENTH)) {
      error(builder, JavaErrorBundle.message("expected.rparen"));
      done(section, JavaElementType.CATCH_SECTION);
      return false;
    }

    PsiBuilder.Marker body = parseCodeBlock(builder, true);
    if (body == null) {
      error(builder, JavaErrorBundle.message("expected.lbrace"));
      done(section, JavaElementType.CATCH_SECTION);
      return false;
    }

    done(section, JavaElementType.CATCH_SECTION);
    return true;
  }

  @NotNull
  private PsiBuilder.Marker parseAssertStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    if (myParser.getExpressionParser().parse(builder) == null) {
      error(builder, JavaErrorBundle.message("expected.boolean.expression"));
    }
    else if (expect(builder, JavaTokenType.COLON) && myParser.getExpressionParser().parse(builder) == null) {
      error(builder, JavaErrorBundle.message("expected.expression"));
    }
    else {
      semicolon(builder);
    }

    done(statement, JavaElementType.ASSERT_STATEMENT);
    return statement;
  }

  @NotNull
  private PsiBuilder.Marker parseBlockStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    parseCodeBlock(builder, true);
    done(statement, JavaElementType.BLOCK_STATEMENT);
    return statement;
  }

  @NotNull
  public PsiBuilder.Marker parseExprInParenthWithBlock(@NotNull PsiBuilder builder, @NotNull IElementType type, boolean block) {
    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    if (parseExprInParenth(builder)) {
      PsiBuilder.Marker body = block ? parseCodeBlock(builder, true) : parseStatement(builder);
      if (body == null) {
        error(builder, JavaErrorBundle.message(block ? "expected.lbrace" : "expected.statement"));
      }
    }

    done(statement, type);
    return statement;
  }

  private boolean parseExprInParenth(PsiBuilder builder) {
    if (!expect(builder, JavaTokenType.LPARENTH)) {
      error(builder, JavaErrorBundle.message("expected.lparen"));
      return false;
    }

    PsiBuilder.Marker beforeExpr = builder.mark();
    PsiBuilder.Marker expr = myParser.getExpressionParser().parse(builder);
    if (expr == null || builder.getTokenType() == JavaTokenType.SEMICOLON) {
      beforeExpr.rollbackTo();
      error(builder, JavaErrorBundle.message("expected.expression"));
      if (builder.getTokenType() != JavaTokenType.RPARENTH) {
        return false;
      }
    }
    else {
      beforeExpr.drop();
      if (builder.getTokenType() != JavaTokenType.RPARENTH) {
        error(builder, JavaErrorBundle.message("expected.rparen"));
        return false;
      }
    }

    builder.advanceLexer();
    return true;
  }
}

package manifold.ij.extensions;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.java.syntax.lexer.JavaLexer;
import com.intellij.java.syntax.lexer.JavaLexerHook;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import manifold.ext.rt.api.Jailbreak;
import manifold.ij.util.ReparseUtil;
import manifold.preprocessor.PreprocessorParser;
import manifold.preprocessor.definitions.Definitions;
import manifold.preprocessor.expression.Expression;
import manifold.preprocessor.expression.ExpressionParser;
import manifold.preprocessor.statement.FileStatement;
import manifold.preprocessor.statement.IfStatement;
import manifold.preprocessor.statement.SourceStatement;
import manifold.preprocessor.statement.Statement;
import manifold.util.concurrent.LocklessLazyVar;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.java.syntax.element.JavaSyntaxTokenType.C_STYLE_COMMENT;
import static manifold.preprocessor.TokenType.*;

public class ManPreprocessorJavaLexerHook implements JavaLexerHook
{
  private static final String MANIFOLD_PREPROCESSOR_DUMB_MODE = "manifold.preprocessor.dumb_mode";
  private static final LocklessLazyVar<boolean[]> PREPROCESSOR_DUMB_MODE = LocklessLazyVar.make(() ->
    new boolean[]{PropertiesComponent.getInstance().getBoolean(MANIFOLD_PREPROCESSOR_DUMB_MODE)});

  private SmartPsiElementPointer<PsiJavaFile> _psiFile;
  private ASTNode _chameleon;
  private @Jailbreak JavaLexer _lexer;

  private final List<SourceStatement> _visibleStmts = new ArrayList<>();
  private final LocklessLazyVar<Definitions> _definitions = LocklessLazyVar.make(() -> {
    Definitions definitions = makeDefinitions();
    PsiJavaFile psiJavaFile;
    if (_psiFile != null && (psiJavaFile = _psiFile.getElement()) != null) {
      // add local #define symbols
      CharSequence text = _lexer.myBuffer.length() < psiJavaFile.getTextLength() ? psiJavaFile.getText() : _lexer.myBuffer;
      if (StringUtil.contains(text, "#define") || StringUtil.contains(text, "#undef")) {
        FileStatement fileStmt = new PreprocessorParser(text, null).parseFile();
        fileStmt.execute(new ArrayList<>(), true, definitions);
      }
    }
    return definitions;
  });

  static boolean isDumbPreprocessorMode() {
    return PREPROCESSOR_DUMB_MODE.get()[0];
  }

  static void setDumbPreprocessorMode(boolean dumbMode) {
    PREPROCESSOR_DUMB_MODE.get()[0] = dumbMode;
    PropertiesComponent.getInstance().setValue(MANIFOLD_PREPROCESSOR_DUMB_MODE, dumbMode);
    ReparseUtil.instance().reparseOpenJavaFilesForAllProjects();
  }

  public void setChameleon(ASTNode chameleon) {
    _chameleon = chameleon;
    PsiJavaFile psiFile = ManPsiBuilderFactoryHook.getPsiFile( chameleon );
    _psiFile = psiFile == null ? null : SmartPointerManager.createPointer(psiFile);
  }

  @NotNull
  private ManDefinitions makeDefinitions() {
    return new ManDefinitions(_chameleon, _psiFile);
  }

  @Override
  public void accept(@NotNull JavaLexer javaLexer) {
    _lexer = javaLexer;
  }

  @Override
  public void start() {
    _visibleStmts.clear();
    _definitions.clear();
  }

  @Override
  public boolean locateToken(char c) {
    if( c == '#' ) {
      return makeDirective();
    }

    return false;
  }

  private boolean makeDirective() {
    if (_chameleon == null || isDumbPreprocessorMode()) {
      // note the `_chamelion == null` check fixes an issue when an opening multiline comment proceeds a preprocessor directive
      return makeDumbDirective();
    }
    return makeSmartDirective();
  }

  private boolean makeDumbDirective() {
    int offset = _lexer.myBufferIndex + 1;
    String directive;
    if (match(directive = Define.getDirective(), offset) ||
      match(directive = Undef.getDirective(), offset) ||
      match(directive = If.getDirective(), offset) ||
      match(directive = Ifdef.getDirective(), offset) ||
      match(directive = Ifndef.getDirective(), offset) ||
      match(directive = Elif.getDirective(), offset) ||
      match(directive = Error.getDirective(), offset) ||
      match(directive = Warning.getDirective(), offset)) {
      offset = skipSpaces(offset + directive.length());
      Expression expr = new ExpressionParser(_lexer.myBuffer, offset, _lexer.myBufferEndOffset).parse();

      _lexer.myTokenType = C_STYLE_COMMENT;
      _lexer.myTokenEndOffset = expr.getEndOffset();
    } else if (match(directive = Else.getDirective(), offset) ||
      match(directive = Endif.getDirective(), offset)) {
      _lexer.myTokenType = C_STYLE_COMMENT;
      _lexer.myTokenEndOffset = offset + directive.length();
    } else {
      return false;
    }
    return true;
  }

  private boolean makeSmartDirective() {
    int offset = _lexer.myBufferIndex + 1;
    if (match(Elif.getDirective(), offset) ||
      match(Else.getDirective(), offset)) {
      _lexer.myTokenType = C_STYLE_COMMENT;
      _lexer.myTokenEndOffset = findCommentRangeEnd(false);
    } else if (match(Endif.getDirective(), offset)) {
      _lexer.myTokenType = C_STYLE_COMMENT;
      _lexer.myTokenEndOffset = offset + Endif.getDirective().length();
    } else if (match(If.getDirective(), offset) ||
               match(Ifdef.getDirective(), offset) ||
               match(Ifndef.getDirective(), offset)) {
      int rangeEnd = findCommentRangeEnd(true);
      if (rangeEnd > 0) {
        // handle nested `#if`
        _lexer.myTokenType = C_STYLE_COMMENT;
        _lexer.myTokenEndOffset = rangeEnd;
      } else {
        // Create a PreprocessorParser with position set to '#' char, then call parseStatement()
        // Then use that to figure out what is a comment and what is not :)
        PreprocessorParser preProc = new PreprocessorParser(_lexer.myBuffer, _lexer.myBufferIndex, _lexer.myBufferEndOffset, null);
        IfStatement statement = (IfStatement) preProc.parseStatement();

        // note we always parse the toplevel #if, and nested #ifs are parsed with it.  since the lexer tokenizes
        // the whole #if statement, we only ever have just one list of visible stmts to manage
        _visibleStmts.clear();
        statement.execute(_visibleStmts, true, _definitions.get());
        // add empty statement marking end of if-stmt
        _visibleStmts.add(new SourceStatement(null, statement.getTokenEnd(), statement.getTokenEnd()));

        _lexer.myTokenType = C_STYLE_COMMENT;
        _lexer.myTokenEndOffset = findCommentRangeEnd(false);
      }
    } else if (match(Define.getDirective(), offset) ||
      match(Undef.getDirective(), offset)) {
      PreprocessorParser preProc = new PreprocessorParser(_lexer.myBuffer, _lexer.myBufferIndex, _lexer.myBufferEndOffset, null);
      Statement statement = preProc.parseStatement();

      statement.execute(new ArrayList<>(), true, _definitions.get());

      _lexer.myTokenType = C_STYLE_COMMENT;
      _lexer.myTokenEndOffset = statement.getTokenEnd();
    } else if (match(Error.getDirective(), offset) ||
      match(Warning.getDirective(), offset)) {
      PreprocessorParser preProc = new PreprocessorParser(_lexer.myBuffer, _lexer.myBufferIndex, _lexer.myBufferEndOffset, null);
      Statement statement = preProc.parseStatement();
      _lexer.myTokenType = C_STYLE_COMMENT;
      _lexer.myTokenEndOffset = statement.getTokenEnd();
    } else {
      return false;
    }
    return true;
  }

  private int findCommentRangeEnd(boolean nestedIf) {
    if (nestedIf && _visibleStmts.isEmpty()) {
      return -1;
    }

    for (int i = 0; i < _visibleStmts.size(); i++) {
      Statement stmt = _visibleStmts.get(i);
      if (nestedIf && i == _visibleStmts.size() - 1) {
        assert stmt.getTokenEnd() == stmt.getTokenStart(); // the empty statement
        // a nested `#if` must match a real
        return -1;
      }

      if (_lexer.myBufferIndex < stmt.getTokenStart()) {
        return stmt.getTokenStart();
      }

      if (_lexer.myBufferIndex < stmt.getTokenEnd()) {
        throw new IllegalStateException("Directive is located in a visible statement at: " + _lexer.myBufferIndex);
      }
    }
    return _lexer.myBufferEndOffset;
  }

  private int skipSpaces(int offset) {
    if (offset >= _lexer.myBufferEndOffset) {
      return _lexer.myBufferEndOffset;
    }

    int pos = offset;
    char c = _lexer.locateCharAt(pos);

    while (c == ' ' || c == '\t') {
      pos++;
      if (pos == _lexer.myBufferEndOffset) {
        return pos;
      }
      c = _lexer.locateCharAt(pos);
    }

    return pos;
  }

  private boolean match(String str, int offset) {
    if (_lexer.myBuffer.length() < offset + str.length()) {
      return false;
    }

    int i;
    for (i = 0; i < str.length(); i++) {
      if (str.charAt(i) != _lexer.myBuffer.charAt(offset + i)) {
        return false;
      }
    }

    if (offset + i >= _lexer.myBuffer.length()) {
      return true;
    }

    char c = _lexer.myBuffer.charAt(offset + i);
    return !Character.isJavaIdentifierPart(c);
  }
}

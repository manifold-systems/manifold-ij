package manifold.ij.extensions;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.java.syntax.lexer.JavaLexer;
import com.intellij.java.syntax.lexer.JavaLexerHook;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.syntax.SyntaxElementType;
import com.intellij.platform.syntax.lexer.Lexer;
import com.intellij.pom.java.LanguageLevel;
import manifold.ext.rt.api.Jailbreak;
import manifold.ij.util.WhatsActiveUtil;
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
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.java.syntax.element.JavaSyntaxTokenType.*;
import static manifold.preprocessor.TokenType.*;

public class ManPreprocessorJavaLexerHook implements JavaLexerHook
{
  private static final Logger LOG = Logger.getInstance( ManPreprocessorJavaLexerHook.class);

  private static final String MANIFOLD_PREPROCESSOR_DUMB_MODE = "manifold.preprocessor.dumb_mode";
  private static final LocklessLazyVar<boolean[]> PREPROCESSOR_DUMB_MODE = LocklessLazyVar.make(() ->
    new boolean[]{PropertiesComponent.getInstance().getBoolean(MANIFOLD_PREPROCESSOR_DUMB_MODE)});

  private @Jailbreak JavaLexer _lexer;
  private Project _project;
  private VirtualFile _vfile;
  private boolean _useLexerContents;

  private final List<SourceStatement> _visibleStmts = new ArrayList<>();
  private final LocklessLazyVar<Definitions> _definitions = LocklessLazyVar.make( () -> {
    Definitions definitions = makeDefinitions();
    // add local #define symbols
    CharSequence text = getDocContents();
    if( StringUtil.contains( text, "#define" ) || StringUtil.contains( text, "#undef" ) )
    {
      FileStatement fileStmt = new PreprocessorParser( text, null ).parseFile();
      fileStmt.execute( new ArrayList<>(), true, definitions );
    }
    return definitions;
  } );

  private @NonNull CharSequence getDocContents()
  {
    if( _useLexerContents )
    {
      return _lexer.getBufferSequence();
    }
    Document doc = WhatsActiveUtil.getActiveDocument( _project );
    return doc != null ? doc.getText() : _lexer.getBufferSequence();
  }

  static boolean isDumbPreprocessorMode() {
    return PREPROCESSOR_DUMB_MODE.get()[0];
  }

  static void setDumbPreprocessorMode(boolean dumbMode) {
    PREPROCESSOR_DUMB_MODE.get()[0] = dumbMode;
    PropertiesComponent.getInstance().setValue(MANIFOLD_PREPROCESSOR_DUMB_MODE, dumbMode);
    ReparseUtil.instance().reparseOpenJavaFilesForAllProjects();
  }

  public void setChameleon(ASTNode chameleon) {
  }

  private VirtualFile deriveFile()
  {
    CharSequence content = _lexer.getBufferSequence();
    VirtualFile vfile;
    String fqn = extractFqn( content );
    if( fqn != null )
    {
      vfile = findFile( fqn );
      if( vfile != null )
      {
        // use the file corresponding with the FQN derived from the lexer's text
        _useLexerContents = true;
        return vfile;
      }
    }

    // use the selected editor's file (from the focused project)
    vfile = WhatsActiveUtil.getActiveFile( _project );
    if( vfile == null )
    {
      // use the project's root directory
      // recall the file here is only used to as the context from which the preprocessor locates builder.properties files.
      _useLexerContents = true; // lexer contents will be a fragment here
      vfile = _project.getBaseDir();
    }
    return vfile;
  }

  private VirtualFile findFile( String fqn )
  {
    String relPath = fqn.replace( '.', '/' ) + ".java";
    for( Project project : ProjectManager.getInstance().getOpenProjects() )
    {
      for( Module module : ModuleManager.getInstance( project ).getModules() )
      {
        ModuleRootManager mrm = ModuleRootManager.getInstance( module );
        for( VirtualFile root : mrm.getSourceRoots() )
        {
          VirtualFile file = root.findFileByRelativePath( relPath );
          if( file != null )
          {
            _project = project;
            return file;
          }
        }
      }
    }
    return null;
  }

  @NotNull
  private ManDefinitions makeDefinitions() {
    _project = WhatsActiveUtil.getActiveProject();
    _vfile = deriveFile();
    return new ManDefinitions( _project, _vfile );
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
    if( isDumbPreprocessorMode() || StringUtil.endsWith( _lexer.myBuffer, "extractFqn_" ) )
    {
      return makeDumbDirective();
    }
    return makeSmartDirective();
  }

  private boolean makeDumbDirective() {
    int offset = _lexer.myBufferIndex + 1;
    String directive;

    // handle directives with expressions: #define, #undef, #if, #elif, #error, #warning
    if (match(directive = Define.getDirective(), offset) ||
      match(directive = Undef.getDirective(), offset) ||
      match(directive = If.getDirective(), offset) ||
      match(directive = Elif.getDirective(), offset) ||
      match(directive = Error.getDirective(), offset) ||
      match(directive = Warning.getDirective(), offset)) {
      offset = skipSpaces(offset + directive.length());
      Expression expr = new ExpressionParser(_lexer.myBuffer, offset, _lexer.myBufferEndOffset).parse();

      _lexer.myTokenType = C_STYLE_COMMENT;
        _lexer.myTokenEndOffset = Math.min(expr.getEndOffset(), _lexer.myBufferEndOffset);
    }
    // handle directives without expressions: #else, #endif
    else if (match(directive = Else.getDirective(), offset) ||
      match(directive = Endif.getDirective(), offset)) {
      _lexer.myTokenType = C_STYLE_COMMENT;
      _lexer.myTokenEndOffset = Math.min(offset + directive.length(), _lexer.myBufferEndOffset);
    }
    else {
      return false;
    }
    return true;
  }

  private boolean makeSmartDirective() {
    LOG.debug( "### makeSmartDirective" );
    int offset = _lexer.myBufferIndex + 1;

    // handle #elif / #else
    if (match(Elif.getDirective(), offset) ||
      match(Else.getDirective(), offset)) {
      _lexer.myTokenType = C_STYLE_COMMENT;
      _lexer.myTokenEndOffset = Math.min(findCommentRangeEnd(false), _lexer.myBufferEndOffset);
    }
    // handle #endif
    else if (match(Endif.getDirective(), offset)) {
      _lexer.myTokenType = C_STYLE_COMMENT;
      _lexer.myTokenEndOffset = Math.min(offset + Endif.getDirective().length(), _lexer.myBufferEndOffset);
    }
    // handle #if
    else if (match(If.getDirective(), offset)) {
      int rangeEnd = findCommentRangeEnd(true);
      if (rangeEnd > 0) {
        // handle nested `#if`
        _lexer.myTokenType = C_STYLE_COMMENT;
        _lexer.myTokenEndOffset = Math.min(rangeEnd, _lexer.myBufferEndOffset);
      } else {
        // Create a PreprocessorParser with position set to '#' char, then call parseStatement()
        // Then use that to figure out what is a comment and what is not :)
        PreprocessorParser preProc = new PreprocessorParser(_lexer.myBuffer, _lexer.myBufferIndex, _lexer.myBufferEndOffset, null);
        IfStatement statement = (IfStatement) preProc.parseStatement();

        // note we always parse the toplevel #if, and nested #ifs are parsed with it.  since the lexer tokenizes
        // the whole #if statement, we only ever have just one list of visible stmts to manage
        _visibleStmts.clear();
        statement.execute(_visibleStmts, true, _definitions.get() );

        // add empty statement marking end of if-stmt
        _visibleStmts.add(new SourceStatement(null, statement.getTokenEnd(), statement.getTokenEnd()));

        _lexer.myTokenType = C_STYLE_COMMENT;
        _lexer.myTokenEndOffset = Math.min(findCommentRangeEnd(false), _lexer.myBufferEndOffset);
      }
    }
    // handle #define / #undef
    else if (match(Define.getDirective(), offset) ||
      match(Undef.getDirective(), offset)) {
      PreprocessorParser preProc = new PreprocessorParser(_lexer.myBuffer, _lexer.myBufferIndex, _lexer.myBufferEndOffset, null);
      Statement statement = preProc.parseStatement();

      statement.execute(new ArrayList<>(), true, _definitions.get() );

      _lexer.myTokenType = C_STYLE_COMMENT;
      _lexer.myTokenEndOffset = Math.min(statement.getTokenEnd(), _lexer.myBufferEndOffset);
    }
    // handle #error / #warning
    else if (match(Error.getDirective(), offset) ||
      match(Warning.getDirective(), offset)) {
      PreprocessorParser preProc = new PreprocessorParser(_lexer.myBuffer, _lexer.myBufferIndex, _lexer.myBufferEndOffset, null);
      Statement statement = preProc.parseStatement();
      _lexer.myTokenType = C_STYLE_COMMENT;
      _lexer.myTokenEndOffset = Math.min(statement.getTokenEnd(), _lexer.myBufferEndOffset);
    }
    else {
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

  // use lexer to derive fqn from source file
  private static String extractFqn( CharSequence text )
  {
    // add marker to short-circuit lexer infinitum
    StringBuilder sb = new StringBuilder( text ).append( "extractFqn_" );
    Lexer lexer = new JavaLexer( LanguageLevel.HIGHEST );
    lexer.start( sb );

    StringBuilder pkg = null;
    String className = null;

    boolean inPackage = false;
    boolean expectClassName = false;

    int braceDepth = 0;

    while( lexer.getTokenType() != null )
    {
      SyntaxElementType token = lexer.getTokenType();
      String tokenText = sb.subSequence( lexer.getTokenStart(), lexer.getTokenEnd() ).toString();

      // Track top-level vs inner types
      if( token == LBRACE )
      {
        braceDepth++;
      }
      else if( token == RBRACE )
      {
        braceDepth--;
      }

      // package parsing
      if( token == PACKAGE_KEYWORD && braceDepth == 0 )
      {
        inPackage = true;
        pkg = new StringBuilder();
      }
      else if( inPackage )
      {
        if( token == IDENTIFIER )
        {
          if( pkg.length() > 0 )
          {
            pkg.append( '.' );
          }
          pkg.append( tokenText );
        }
        else if( token == DOT )
        {
          // handled implicitly
        }
        else if( token == SEMICOLON )
        {
          inPackage = false;
        }
      }

      // type declaration detection
      if( braceDepth == 0 )
      {
        if( token == CLASS_KEYWORD ||
            token == INTERFACE_KEYWORD ||
            token == ENUM_KEYWORD ||
            token == RECORD_KEYWORD )
        {
          expectClassName = true;
        }
        else if( expectClassName && token == IDENTIFIER )
        {
          className = tokenText;
          break; // we only care about the first top-level type
        }
      }
      lexer.advance();
    }

    if( className == null )
    {
      return null;
    }

    if( pkg != null && !pkg.isEmpty() )
    {
      return pkg + "." + className;
    }

    return className;
  }
}

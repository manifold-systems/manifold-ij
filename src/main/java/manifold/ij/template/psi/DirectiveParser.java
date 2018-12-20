package manifold.ij.template.psi;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.ReferenceParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;


import static com.intellij.lang.PsiBuilderUtil.expect;
import static com.intellij.lang.java.parser.JavaParserUtil.*;

public class DirectiveParser
{
  private static final DirectiveParser INSTANCE = new DirectiveParser();

  public static final String PARAMS = "params";
  public static final String EXTENDS = "extends";
  public static final String IMPORT = "import";
  public static final String INCLUDE = "include";
  public static final String SECTION = "section";
  public static final String END = "end";
  public static final String LAYOUT = "layout";
  public static final String CONTENT = "content";

  private static final TokenSet TYPE_START = TokenSet.orSet(
    ElementType.PRIMITIVE_TYPE_BIT_SET, TokenSet.create( JavaTokenType.IDENTIFIER ) );

  private final JavaParser _javaParser;

  public static DirectiveParser instance()
  {
    return INSTANCE;
  }

  private DirectiveParser()
  {
    _javaParser = new JavaParser();
  }

  public void parse( PsiBuilder builder )
  {
    builder.setDebugMode( ApplicationManager.getApplication().isUnitTestMode() );

    String directiveName = builder.getTokenText();
    if( (builder.getTokenType() != JavaTokenType.IDENTIFIER &&
         builder.getTokenType() != JavaTokenType.IMPORT_KEYWORD &&
         builder.getTokenType() != JavaTokenType.EXTENDS_KEYWORD)
        || directiveName == null )
    {
      builder.error( JavaErrorMessages.message( "expected.identifier" ) );
      builder.advanceLexer();
      return;
    }

    switch( directiveName )
    {
      case PARAMS:
        parseParamsDirective( builder );
        break;

      case EXTENDS:
        parseExtendsDirective( builder );
        break;

      case IMPORT:
        parseImportDirective( builder );
        break;

      case INCLUDE:
        parseIncludeDirective( builder );
        break;

      case SECTION:
        parseSectionDirective( builder );
        break;

      case END:
        parseEndDirective( builder );
        break;

      case LAYOUT:
        parseLayoutDirective( builder );
        break;

      case CONTENT:
        parseContentDirective( builder );
        break;

      default:
        builder.advanceLexer();
        builder.error( "Unknown directive: '" + directiveName + "'" );
    }
  }

  private void parseContentDirective( PsiBuilder builder )
  {
    builder.advanceLexer();
  }

  private void parseLayoutDirective( PsiBuilder builder )
  {
    builder.advanceLexer();
    ReferenceParser.TypeInfo type = parseType( builder );
    if( type == null )
    {
      PsiBuilder.Marker error = builder.mark();
      error.error( JavaErrorMessages.message( "expected.identifier.or.type" ) );
    }
  }

  private void parseSectionDirective( PsiBuilder builder )
  {
    builder.advanceLexer();

    expectOrError( builder, JavaTokenType.IDENTIFIER, "expected.identifier" );
    if( builder.getTokenType() == JavaTokenType.LPARENTH )
    {
      builder.advanceLexer();
      parseParameterList( builder, true );
      expectOrError( builder, JavaTokenType.RPARENTH, "expected.rparen" );
    }
  }

  private void parseEndDirective( PsiBuilder builder )
  {
    builder.advanceLexer();

    final IElementType tokenType = builder.getTokenType();
    if( tokenType == null )
    {
      return;
    }

    String directiveName = builder.getTokenText();
    if( tokenType != JavaTokenType.IDENTIFIER )
    {
      builder.error( JavaErrorMessages.message( "expected.identifier" ) );
      return;
    }

    if( directiveName == null || !directiveName.equals( SECTION ) )
    {
      builder.error( "Expecting 'section' keyword" );
    }
    builder.advanceLexer();
  }

  private void parseIncludeDirective( PsiBuilder builder )
  {
    builder.advanceLexer();
    ReferenceParser.TypeInfo type = parseType( builder );
    if( type == null )
    {
      PsiBuilder.Marker error = builder.mark();
      error.error( JavaErrorMessages.message( "expected.identifier.or.type" ) );
    }
  }

  private void parseImportDirective( PsiBuilder builder )
  {
    PsiBuilder.Marker importStatement = builder.mark();
    builder.advanceLexer();
    _javaParser.getReferenceParser().parseImportCodeReference( builder, false );
    done( importStatement, JavaElementType.IMPORT_STATEMENT );
  }

  private void parseExtendsDirective( PsiBuilder builder )
  {
    PsiBuilder.Marker extendsClause = builder.mark();
    builder.advanceLexer();
    if( _javaParser.getReferenceParser().parseJavaCodeReference( builder, true, true, false, false ) == null )
    {
      PsiBuilder.Marker error = builder.mark();
      error.error( JavaErrorMessages.message( "expected.identifier.or.type" ) );
    }
    done( extendsClause, JavaElementType.EXTENDS_LIST );
  }

  private void parseParamsDirective( PsiBuilder builder )
  {
    builder.advanceLexer();
    expectOrError( builder, JavaTokenType.LPARENTH, "expected.lparen" );
    parseParameterList( builder );
    expectOrError( builder, JavaTokenType.RPARENTH, "expected.rparen" );
  }

  private void parseParameterList( PsiBuilder builder )
  {
    parseParameterList( builder, false );
  }
  private void parseParameterList( PsiBuilder builder, boolean allowEmpty )
  {
    parseParam( builder, allowEmpty );
    while( builder.getTokenType() == JavaTokenType.COMMA )
    {
      builder.advanceLexer();
      parseParam( builder );
    }
  }

  private void parseParam( PsiBuilder builder )
  {
    parseParam( builder, false );
  }
  private void parseParam( PsiBuilder builder, boolean allowEmpty )
  {
    final IElementType tokenType = builder.getTokenType();
    if( tokenType == null )
    {
      return;
    }

    PsiBuilder.Marker declStatement = builder.mark();
    PsiBuilder.Marker localVariableDecl = builder.mark();

//    Pair<PsiBuilder.Marker, Boolean> modListInfo = _javaParser.getDeclarationParser().parseModifierList( builder );
//    PsiBuilder.Marker modList = modListInfo.first;

    ReferenceParser.TypeInfo type = parseType( builder );
    if( type == null )
    {
      if( !allowEmpty )
      {
        PsiBuilder.Marker error = builder.mark();
        error.error( JavaErrorMessages.message( "expected.identifier.or.type" ) );
      }
      localVariableDecl.drop();
      declStatement.drop();
      return;
    }

    if( !expect( builder, JavaTokenType.IDENTIFIER ) )
    {
      builder.error( JavaErrorMessages.message( "expected.identifier" ) );
      localVariableDecl.drop();
      declStatement.drop();
      return;
    }

    eatBrackets( builder, null );

    done( localVariableDecl, JavaElementType.LOCAL_VARIABLE );
    done( declStatement, JavaElementType.DECLARATION_STATEMENT );
  }

  @Nullable
  private ReferenceParser.TypeInfo parseType( PsiBuilder builder )
  {
    ReferenceParser.TypeInfo type = null;
    if( TYPE_START.contains( builder.getTokenType() ) )
    {
      PsiBuilder.Marker pos = builder.mark();

      int flags = ReferenceParser.EAT_LAST_DOT | ReferenceParser.WILDCARD;
      flags |= ReferenceParser.VAR_TYPE;

      type = _javaParser.getReferenceParser().parseTypeInfo( builder, flags );

      if( type == null )
      {
        pos.rollbackTo();
      }
      else
      {
        pos.drop();
      }
    }

    return type;
  }

  private boolean eatBrackets( PsiBuilder builder, @Nullable @PropertyKey(resourceBundle = JavaErrorMessages.BUNDLE) String errorKey )
  {
    IElementType tokenType = builder.getTokenType();
    if( tokenType != JavaTokenType.LBRACKET && tokenType != JavaTokenType.AT )
    {
      return true;
    }

    PsiBuilder.Marker marker = builder.mark();

    int count = 0;
    while( true )
    {
      //parseAnnotations( builder );
      if( !expect( builder, JavaTokenType.LBRACKET ) )
      {
        break;
      }
      ++count;
      if( !expect( builder, JavaTokenType.RBRACKET ) )
      {
        break;
      }
      ++count;
    }

    if( count == 0 )
    {
      // just annotation, most probably belongs to a next declaration
      marker.rollbackTo();
      return true;
    }

    if( errorKey != null )
    {
      marker.error( JavaErrorMessages.message( errorKey ) );
    }
    else
    {
      marker.drop();
    }

    boolean paired = count % 2 == 0;
    if( !paired )
    {
      error( builder, JavaErrorMessages.message( "expected.rbracket" ) );
    }
    return paired;
  }

}

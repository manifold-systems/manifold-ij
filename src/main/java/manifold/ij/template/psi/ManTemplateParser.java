package manifold.ij.template.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;


import static manifold.ij.template.psi.ManTemplateTokenType.*;


public class ManTemplateParser implements PsiParser
{
  public static final IElementType Directive = new ManDirectiveTemplateElementType( "Directive" );
  public static final IElementType Statement = new ManTemplateElementType( "Statement" );
  public static final IElementType Expression = new ManTemplateElementType( "Expression" );
  public static final IElementType Comment = new ManTemplateElementType( "Comment" );

  private IElementType _tokenType;
  private PsiBuilder _builder;

  @NotNull
  @Override
  public ASTNode parse( @NotNull IElementType root, @NotNull PsiBuilder builder )
  {
    _builder = builder;

    builder.setDebugMode( ApplicationManager.getApplication().isUnitTestMode() );

    PsiBuilder.Marker rootMarker = builder.mark();
    PsiBuilder.Marker marker = builder.mark();
    _tokenType = builder.getTokenType();
    while( !builder.eof() && _tokenType != null )
    {
      parseTemplateItem();
    }
    marker.done( ManTemplateElementType.ALL );
    rootMarker.done( root );

    return builder.getTreeBuilt();
  }

  private void parseTemplateItem()
  {
    if( peek( DIR_ANGLE_BEGIN ) )
    {
      parseDirectiveItem();
    }
    else if( peek( STMT_ANGLE_BEGIN ) )
    {
      parseStatementItem();
    }
    else if( peek( EXPR_BRACE_BEGIN ) ||
             peek( EXPR_ANGLE_BEGIN ) )
    {
      parseExpressionItem();
    }
    else if( peek( COMMENT_BEGIN ) )
    {
      parseCommentItem();
    }
    else if( peek( CONTENT ) )
    {
      match( CONTENT );
    }
    else
    {
      advance();
      _builder.error( "Unexpected token: " + _builder.getTokenType() );
    }
  }

  private void parseDirectiveItem()
  {
    PsiBuilder.Marker marker = _builder.mark();
    match( DIR_ANGLE_BEGIN );
    match( DIRECTIVE, "Expecting a template directive" );
    match( ANGLE_END, "Expecting '%>' to close the template directive" );
    marker.done( Directive );
  }

  private void parseStatementItem()
  {
    PsiBuilder.Marker marker = _builder.mark();
    match( STMT_ANGLE_BEGIN );
    match( STMT, "Expecting Java statement code" );
    match( ANGLE_END, "Expecting '%>' to close the Java statement" );
    marker.done( Statement );
  }

  private void parseExpressionItem()
  {
    PsiBuilder.Marker marker = _builder.mark();
    boolean brace = _tokenType == EXPR_BRACE_BEGIN;
    match( brace ? EXPR_BRACE_BEGIN : EXPR_ANGLE_BEGIN );
    match( EXPR, "Expecting a Java expression" );
    match( brace ? EXPR_BRACE_END : ANGLE_END, "Expecting " + (brace ? "}" : "%>") + " to close the Java expression" );
    marker.done( Expression );
  }

  private void parseCommentItem()
  {
    PsiBuilder.Marker marker = _builder.mark();
    match( COMMENT_BEGIN );
    if( peek( COMMENT ) )
    {
      match( COMMENT );
    }
    match( COMMENT_END, "Expecting '--%>' to close the comment" );
    marker.done( Comment );
  }

  private boolean peek( IElementType test )
  {
    return _tokenType == test;
  }

  private boolean match( IElementType tokenType )
  {
    return match( tokenType, null );
  }
  private boolean match( IElementType tokenType, String msg )
  {
    return match( tokenType, msg, false );
  }
  private boolean match( IElementType tokenType, String msg, boolean advance )
  {
    boolean match = _builder.getTokenType() == tokenType;
    if( !match )
    {
      if( msg != null )
      {
        _builder.error( msg );
      }
      else
      {
        _builder.error( "Unexpected token. Expecting " +
                        tokenType + ", found " + _builder.getTokenType() );
      }
    }
    if( match || advance )
    {
      advance();
    }
    return match;
  }

  private void advance()
  {
    _builder.advanceLexer();
    _tokenType = _builder.getTokenType();
  }
}

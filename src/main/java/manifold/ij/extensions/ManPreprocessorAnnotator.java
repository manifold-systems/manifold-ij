package manifold.ij.extensions;

import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtils;
import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import manifold.preprocessor.PreprocessorParser;
import manifold.preprocessor.TokenType;
import manifold.preprocessor.Tokenizer;
import manifold.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * For the Preprocessor.  Highlights preprocessor directives and shades masked out code (which are PsiComments).
 */
public class ManPreprocessorAnnotator extends ExternalAnnotator<PsiFile, ManPreprocessorAnnotator.Info>
{
  private static final TextAttributesKey KEY_DIRECTIVE_KEYWORD = ObjectUtils.notNull( TextAttributesKey.find( "JAVA_KEYWORD" ),
    DefaultLanguageHighlighterColors.KEYWORD );
  private static final Color COLOR_DIRECTIVE_KEYWORD = KEY_DIRECTIVE_KEYWORD.getDefaultAttributes().getForegroundColor();

  private static final TextAttributesKey KEY_DIRECTIVE_BACKGROUND = ObjectUtils.notNull( TextAttributesKey.find( "INLINE_PARAMETER_HINT" ),
    DefaultLanguageHighlighterColors.BLOCK_COMMENT );
  private static final Color COLOR_DIRECTIVES_BACKGROUND = KEY_DIRECTIVE_BACKGROUND.getDefaultAttributes().getBackgroundColor();

  private final TextAttributes _directiveKeyword =
    new TextAttributes( COLOR_DIRECTIVE_KEYWORD, null, null, null, Font.BOLD );
  private final TextAttributes _notCompiled =
    new TextAttributes( null, COLOR_DIRECTIVES_BACKGROUND, COLOR_DIRECTIVES_BACKGROUND, EffectType.WAVE_UNDERSCORE, Font.PLAIN );

  @Nullable
  @Override
  public PsiFile collectInformation( @NotNull PsiFile file )
  {
    return file;
  }

  @Nullable
  @Override
  public PsiFile collectInformation( @NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors )
  {
    return file;
  }

  @Nullable
  @Override
  public Info doAnnotate( PsiFile file )
  {
    Info info = new Info();
    String source = file.getText();
    PreprocessorParser parser = new PreprocessorParser( source, t -> addDirective( t, info ) );
    parser.parseFile( (message, pos) -> {
      int errorPos = ensureErrorPosNotOnNewLine( source, pos );
      info.addToIssues( message, errorPos );
    } );
    return info;
  }

  private int ensureErrorPosNotOnNewLine( String source, int pos )
  {
    char c = pos < source.length() ? source.charAt( pos ) : '\0';
    if( pos > 0 && c == '\0' || c == '\r' || c == '\n' )
    {
      pos--;
    }
    return pos;
  }


  @Override
  public void apply( @NotNull PsiFile file, Info info, @NotNull AnnotationHolder holder )
  {
    info.getDirectives().forEach( d -> {
      Annotation annotation = holder.createAnnotation( HighlightSeverity.INFORMATION, TextRange.create( d[0], d[1] ), null );
      annotation.setEnforcedTextAttributes( _directiveKeyword );
      PsiElement comment = file.findElementAt( d[0] + 1 );
      if( comment instanceof PsiComment )
      {
        if( comment.getText().startsWith( "#" + TokenType.Error.getDirective() ) )
        {
          holder.createAnnotation( HighlightSeverity.ERROR, TextRange.create( d[0], d[1] ), null );
        }
        else if( comment.getText().startsWith( "#" + TokenType.Warning.getDirective() ) )
        {
          holder.createAnnotation( HighlightSeverity.WARNING, TextRange.create( d[0], d[1] ), null );
        }
        Annotation unseen = holder.createInfoAnnotation( comment, null );
        unseen.setEnforcedTextAttributes( _notCompiled );
      }
    } );
    info.getIssues().forEach(
      issue -> holder.createAnnotation( HighlightSeverity.ERROR,
        TextRange.create( issue.getSecond(), issue.getSecond()+1 ), issue.getFirst() ) );
  }

  private void addDirective( Tokenizer tokenizer, Info info )
  {
    if( tokenizer.getTokenType() == null )
    {
      return;
    }

    switch( tokenizer.getTokenType() )
    {
      case If:
      case Elif:
      case Else:
      case Endif:
      case Define:
      case Undef:
      case Error:
      case Warning:
        info.addToDirectives( tokenizer.getTokenStart(),
          tokenizer.getTokenStart()+1 + tokenizer.getTokenType().getDirective().length() );
        break;
    }
  }

  static class Info
  {
    List<int[]> _directives;
    List<Pair<String, Integer>> _issues;

    Info()
    {
      _directives = new ArrayList<>();
      _issues = new ArrayList<>();
    }

    void addToDirectives( int start, int end )
    {
      _directives.add( new int[] {start, end} );
    }

    void addToIssues( String message, int pos )
    {
      _issues.add( new Pair<>( message, pos ) );
    }

    List<int[]> getDirectives()
    {
      return _directives;
    }

    List<Pair<String, Integer>> getIssues()
    {
      return _issues;
    }
  }
}
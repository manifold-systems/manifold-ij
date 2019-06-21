package manifold.ij.extensions;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.javadoc.PsiDocComment;
import manifold.ij.core.ManProject;
import manifold.internal.javac.CommentProcessor;
import org.jetbrains.annotations.NotNull;


public class ManCommentFragmentInjector implements LanguageInjector
{
  @Override
  public void getLanguagesToInject( @NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces injectionPlacesRegistrar )
  {
    if( !ManProject.isManifoldInUse( host.getProject() ) )
    {
      return;
    }

    PsiComment psiComment = getJavaComment( host );
    if( psiComment == null )
    {
      return;
    }

    String hostText = host.getText();
    CommentProcessor commentProcessor = CommentProcessor.instance();
    CommentProcessor.Fragment fragment = commentProcessor.parseFragment( 0, hostText, makeStyle( psiComment ) );
    if( fragment != null )
    {
      Language language = getLanguageFromExt( fragment.getExt() );
      if( language != null )
      {
        injectionPlacesRegistrar.addPlace( language,
          new TextRange( fragment.getOffset(), fragment.getOffset() + fragment.getContent().length() ), null, null );
      }
    }
  }

  private Language getLanguageFromExt( String ext )
  {
    return LanguageUtil.getFileTypeLanguage( FileTypeManager.getInstance().getFileTypeByExtension( ext ) );
  }

  public static CommentProcessor.Style makeStyle( PsiComment psiComment )
  {
    if( psiComment instanceof PsiDocComment )
    {
      return CommentProcessor.Style.JAVADOC;
    }
    if( psiComment.getTokenType() == JavaTokenType.C_STYLE_COMMENT )
    {
      return CommentProcessor.Style.BLOCK;
    }
    if( psiComment.getTokenType() == JavaTokenType.END_OF_LINE_COMMENT )
    {
      return CommentProcessor.Style.LINE;
    }
    throw new IllegalStateException( "Unexpected comment type: " + psiComment.getTokenType() );
  }

  private PsiComment getJavaComment( @NotNull PsiLanguageInjectionHost host )
  {
    // Only applies to Java string literal expression
    if( host.getLanguage().isKindOf( JavaLanguage.INSTANCE ) &&
        host instanceof PsiComment )
    {
      return (PsiComment)host;
    }
    return null;
  }
}

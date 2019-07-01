package manifold.ij.extensions;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.tree.IElementType;
import manifold.ij.core.ManProject;
import manifold.internal.javac.FragmentProcessor;
import manifold.internal.javac.HostKind;
import org.jetbrains.annotations.NotNull;


public class ManStringFragmentInjector implements LanguageInjector
{
  @Override
  public void getLanguagesToInject( @NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces injectionPlacesRegistrar )
  {
    if( !ManProject.isManifoldInUse( host.getProject() ) )
    {
      return;
    }

    HostKind stringKind = getStringKind( host );
    if( stringKind == null )
    {
      return;
    }

    String hostText = host.getText();
    FragmentProcessor fragmentProcessor = FragmentProcessor.instance();
    FragmentProcessor.Fragment fragment = fragmentProcessor.parseFragment( 0, hostText, stringKind );
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

  private HostKind getStringKind( @NotNull PsiLanguageInjectionHost host )
  {
    // Only applies to Java string literal expression
    if( host.getLanguage().isKindOf( JavaLanguage.INSTANCE ) &&
        host instanceof PsiLiteralExpressionImpl )
    {
      IElementType type = ((PsiLiteralExpressionImpl)host).getLiteralElementType();
      if( type == JavaTokenType.STRING_LITERAL )
      {
        return HostKind.DOUBLE_QUOTE_LITERAL;
      }
      else if( type == JavaTokenType.RAW_STRING_LITERAL )
      {
        return HostKind.BACKTICK_LITERAL;
      }
    }
    return null;
  }
}

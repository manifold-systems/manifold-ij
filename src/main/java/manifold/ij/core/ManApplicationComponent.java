package manifold.ij.core;

import com.intellij.codeInsight.highlighting.PairedBraceMatcherAdapter;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.LanguageBraceMatching;
import com.intellij.openapi.components.ApplicationComponent;
import manifold.ij.extensions.ManifoldPsiClassAnnotator;
import manifold.ij.template.ManTemplateBraceMatcher;
import manifold.ij.template.ManTemplateLanguage;
import manifold.ij.util.ManVersionUtil;
import manifold.util.ReflectUtil;

/**
 */
public class ManApplicationComponent implements ApplicationComponent
{
  @Override
  public void initComponent()
  {
    registerAnnotatorWithAllLanguages();
  }

  @Override
  public void disposeComponent() {
  }

  private void registerAnnotatorWithAllLanguages()
  {
    for( Language lang: Language.getRegisteredLanguages() )
    {
      if( ManVersionUtil.is2018_2_orGreater.get() )
      {
        LanguageAnnotators.INSTANCE.addExplicitExtension( lang, new ManifoldPsiClassAnnotator() );
      }
      else
      {
        ReflectUtil.method( LanguageAnnotators.INSTANCE, "addExplicitExtension", Object.class, Object.class ).invoke( lang, new ManifoldPsiClassAnnotator() );
      }
    }
    if( ManVersionUtil.is2018_2_orGreater.get() )
    {
      LanguageBraceMatching.INSTANCE.addExplicitExtension( ManTemplateLanguage.INSTANCE, new PairedBraceMatcherAdapter( new ManTemplateBraceMatcher(), ManTemplateLanguage.INSTANCE ) );
    }
    else
    {
      ReflectUtil.method( LanguageAnnotators.INSTANCE, "addExplicitExtension", Object.class, Object.class ).invoke( ManTemplateLanguage.INSTANCE, new PairedBraceMatcherAdapter( new ManTemplateBraceMatcher(), ManTemplateLanguage.INSTANCE ) );
    }
  }

}

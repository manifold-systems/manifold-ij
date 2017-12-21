package manifold.ij.core;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.openapi.components.ApplicationComponent;
import manifold.ij.extensions.ManifoldPsiClassAnnotator;

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
      LanguageAnnotators.INSTANCE.addExplicitExtension( lang, new ManifoldPsiClassAnnotator() );
    }
  }

}

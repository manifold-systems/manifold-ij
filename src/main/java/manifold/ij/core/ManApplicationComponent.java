package manifold.ij.core;

import com.intellij.codeInsight.highlighting.PairedBraceMatcherAdapter;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.LanguageBraceMatching;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.psi.impl.java.stubs.JavaLiteralExpressionElementType;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import java.util.function.Supplier;
import manifold.ij.extensions.ManJavaLiteralExpressionElementType;
import manifold.ij.extensions.ManifoldPsiClassAnnotator;
import manifold.ij.template.ManTemplateBraceMatcher;
import manifold.ij.template.ManTemplateLanguage;
import manifold.ij.util.ManVersionUtil;
import manifold.internal.runtime.Bootstrap;
import manifold.util.ReflectUtil;

/**
 *
 */
public class ManApplicationComponent implements ApplicationComponent
{
  @Override
  public void initComponent()
  {
    registerAnnotatorWithAllLanguages();

    turnOnManifoldRuntimeSupport();

    replaceJavaExpressionParser();

    overrideJavaStringLiterals();
  }

  private void replaceJavaExpressionParser()
  {
    ReflectUtil.field( JavaParser.INSTANCE, "myExpressionParser" ).set( new ManExpressionParser( JavaParser.INSTANCE ) );
    ReflectUtil.field( JavaElementType.BINARY_EXPRESSION, "myConstructor" ).set( (Supplier)ManPsiBinaryExpressionImpl::new );

    ReflectUtil.field( JavaParser.INSTANCE, "myStatementParser" ).set( new ManStatementParser( JavaParser.INSTANCE ) );
  }

  private void turnOnManifoldRuntimeSupport()
  {
    if( !ManVersionUtil.is2018_2_orGreater() )
    {
      // Manifold runtime needed to support DarkJ usage to support older versions of IJ
      Bootstrap.init();
    }
  }

  /**
   * Override Java String literals to handle fragments
   */
  private void overrideJavaStringLiterals()
  {
    ManJavaLiteralExpressionElementType override = new ManJavaLiteralExpressionElementType();
    ReflectUtil.field( JavaStubElementTypes.class, "LITERAL_EXPRESSION" ).setStatic( override );

    ApplicationManager.getApplication().runReadAction( () -> {
      IElementType[] registry = (IElementType[])ReflectUtil.field( IElementType.class, "ourRegistry" ).getStatic();
      for( int i = 0; i < registry.length; i++ )
      {
        if( registry[i] instanceof JavaLiteralExpressionElementType )
        {
          // ensure the original JavaLiteralExpressionElementType is replaced with ours
          registry[i] = override;
        }
      }
    } );
  }

  @Override
  public void disposeComponent() {
  }

  private void registerAnnotatorWithAllLanguages()
  {
    for( Language lang: Language.getRegisteredLanguages() )
    {
      if( ManVersionUtil.is2018_2_orGreater() )
      {
        LanguageAnnotators.INSTANCE.addExplicitExtension( lang, new ManifoldPsiClassAnnotator() );
      }
      else
      {
        ReflectUtil.method( LanguageAnnotators.INSTANCE, "addExplicitExtension", Object.class, Object.class ).invoke( lang, new ManifoldPsiClassAnnotator() );
      }
    }
    if( ManVersionUtil.is2018_2_orGreater() )
    {
      LanguageBraceMatching.INSTANCE.addExplicitExtension( ManTemplateLanguage.INSTANCE, new PairedBraceMatcherAdapter( new ManTemplateBraceMatcher(), ManTemplateLanguage.INSTANCE ) );
    }
    else
    {
      ReflectUtil.method( LanguageBraceMatching.INSTANCE, "addExplicitExtension", Object.class, Object.class ).invoke( ManTemplateLanguage.INSTANCE, new PairedBraceMatcherAdapter( new ManTemplateBraceMatcher(), ManTemplateLanguage.INSTANCE ) );
    }
  }

}

package manifold.ij.json;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import java.util.List;
import manifold.ij.AbstractManifoldCodeInsightTest;

/**
 * note there is no Json annotator extension per se, rather there is a general
 * purpose Manifold annotator: ManifoldPsiClassAnnotator;
 * this tests that errors stemming from the json manifold reflect in the editor as error highlights
 */
public class JsonAnnotatorTest extends AbstractManifoldCodeInsightTest
{
  //## todo: make this test comprehensive wrt the issues from the json manifold

  public void testHighlights() throws Exception
  {
    myFixture.configureByFile( "json/highlight/MyError.json" );
    List<HighlightInfo> highlightInfos = myFixture.doHighlighting( HighlightSeverity.WARNING );
    assertEquals( 1, highlightInfos.size() );
    assertEquals( "Invalid URI fragment: /definitions/crawlStepTyp", highlightInfos.get( 0 ).getDescription() );
    assertEquals( "\"#/definitions/crawlStepTyp\"", highlightInfos.get( 0 ).getText() );
  }
}
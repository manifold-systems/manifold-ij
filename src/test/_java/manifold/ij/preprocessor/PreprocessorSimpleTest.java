package manifold.ij.preprocessor;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import java.util.List;
import manifold.ij.AbstractManifoldCodeInsightTest;

public class PreprocessorSimpleTest extends AbstractManifoldCodeInsightTest
{
  public void testHighlights()
  {
    myFixture.configureByFile( "preprocessor/MyPreprocessorClass.java" );
    List<HighlightInfo> highlightInfos = myFixture.doHighlighting( HighlightSeverity.ERROR );
    assertEquals( 6, highlightInfos.size() );
    for( HighlightInfo highlightInfo: highlightInfos )
    {
      assertEquals( "#error", highlightInfo.getText() );
    }
  }
}
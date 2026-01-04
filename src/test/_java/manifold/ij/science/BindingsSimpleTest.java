package manifold.ij.science;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import java.util.List;
import manifold.ij.AbstractManifoldCodeInsightTest;

public class BindingsSimpleTest extends AbstractManifoldCodeInsightTest
{
  public void testPositiveUsage()
  {
    myFixture.configureByFile( "science/ExerciseBindingExpressions.java" );
    List<HighlightInfo> highlightInfos = myFixture.doHighlighting( HighlightSeverity.ERROR );
    assertEmpty( highlightInfos );
  }

  public void testErrorHighlights()
  {
    myFixture.configureByFile( "science/ExerciseBindingExpressionsWithErrors.java" );
    List<HighlightInfo> highlightInfos = myFixture.doHighlighting( HighlightSeverity.ERROR );
    assertEquals( 2, highlightInfos.size() );
  }
}

package manifold.ij.template;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import java.util.List;
import manifold.ij.AbstractManifoldCodeInsightTest;

public class TemplateStructureTest extends AbstractManifoldCodeInsightTest
{
  public void testStructureNoErrors()
  {
    myFixture.configureByFile( "template/sample/TestFile.html.mtl" );
    List<HighlightInfo> highlightInfos = myFixture.doHighlighting( HighlightSeverity.WARNING );
    assertEmpty( highlightInfos );
  }

  public void testStructureOneError()
  {
    myFixture.configureByFile( "template/sample/TestFile2.html.mtl" );
    List<HighlightInfo> highlightInfos = myFixture.doHighlighting( HighlightSeverity.WARNING );
    assertEquals( 1, highlightInfos.size() );
    assertEquals( "Cannot resolve symbol 'param'", highlightInfos.get( 0 ).getDescription() );
  }

}

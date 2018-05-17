package manifold.ij.template;

import com.intellij.openapi.editor.markup.RangeHighlighter;
import manifold.ij.AbstractManifoldCodeInsightTest;

public class TemplateHighlightTest extends AbstractManifoldCodeInsightTest
{
  public void testHighlight()
  {
    RangeHighlighter[] highlightInfos = myFixture.testHighlightUsages( "template/usages/MyTemplate_Usages_Java_1.html.mtl" );
    assertEquals( 6, highlightInfos.length );
  }

}

package manifold.ij.ext;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import java.util.List;
import manifold.ij.AbstractManifoldCodeInsightTest;

public class ExtErrorAnnotationsTest extends AbstractManifoldCodeInsightTest
{
  public void testErrorHighlights()
  {
    myFixture.copyFileToProject( "ext/sample/Foo.java" );
    myFixture.configureByFile( "extensions/ext/sample/Foo/MyFooExt.java" );
    List<HighlightInfo> highlightInfos = myFixture.doHighlighting( HighlightSeverity.WARNING );
    assertEquals( 1, highlightInfos.size() );
    assertEquals( "Extending source file 'ext.sample.Foo' in the same module, consider modifying the file directly.",
      highlightInfos.get( 0 ).getDescription() );
  }

}

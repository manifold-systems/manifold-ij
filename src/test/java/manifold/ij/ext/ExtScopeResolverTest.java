package manifold.ij.ext;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import java.util.List;
import manifold.ij.AbstractManifoldCodeInsightTest;

public class ExtScopeResolverTest extends AbstractManifoldCodeInsightTest
{
  public void testComplicatedGenericsResolve()
  {
    myFixture.configureByFile( "ext/highlight/MyErrors.java" );
    List<HighlightInfo> highlightInfos = myFixture.doHighlighting( HighlightSeverity.ERROR );
    highlightInfos.forEach( hi -> System.out.println(hi.toString()) ); //should be empty, but sometimes fails
    assertEquals( 0, highlightInfos.size() );
  }

}

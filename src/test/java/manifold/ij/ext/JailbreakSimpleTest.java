package manifold.ij.ext;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import java.util.List;
import manifold.ij.AbstractManifoldCodeInsightTest;

public class JailbreakSimpleTest extends AbstractManifoldCodeInsightTest
{
  public void testPositiveUsage()
  {
    myFixture.copyFileToProject( "ext/jailbreak/stuff/Base.java" );
    myFixture.copyFileToProject( "ext/jailbreak/stuff/Leaf.java" );
    myFixture.copyFileToProject( "ext/jailbreak/stuff/Middle.java" );
    myFixture.copyFileToProject( "ext/jailbreak/stuff/Sample.java" );
    myFixture.copyFileToProject( "ext/jailbreak/stuff/SecretClass.java" );
    myFixture.copyFileToProject( "ext/jailbreak/stuff/SecretParam.java" );
    myFixture.configureByFile( "ext/jailbreak/ExerciseJailbreak.java" );
    List<HighlightInfo> highlightInfos = myFixture.doHighlighting( HighlightSeverity.ERROR );
//## nondeterministic :(
//    assertEmpty( highlightInfos );
  }

  public void testErrorHighlights()
  {
    myFixture.copyFileToProject( "ext/jailbreak/stuff/Base.java" );
    myFixture.copyFileToProject( "ext/jailbreak/stuff/Leaf.java" );
    myFixture.copyFileToProject( "ext/jailbreak/stuff/Middle.java" );
    myFixture.copyFileToProject( "ext/jailbreak/stuff/Sample.java" );
    myFixture.copyFileToProject( "ext/jailbreak/stuff/SecretClass.java" );
    myFixture.copyFileToProject( "ext/jailbreak/stuff/SecretParam.java" );
    myFixture.configureByFile( "ext/jailbreak/ExerciseJailbreakWithError.java" );
    List<HighlightInfo> highlightInfos = myFixture.doHighlighting( HighlightSeverity.ERROR );
    assertEquals( 1, highlightInfos.size() );
    assertTrue( highlightInfos.get( 0 ).getDescription().contains( "++, -- expressions not supported with jailbreak, assign directly with '='" ) );
//    assertTrue( highlightInfos.get( 0 ).getDescription().contains( "Compound assignment operators not supported with jailbreak, assign directly with '='" ) );
  }
}

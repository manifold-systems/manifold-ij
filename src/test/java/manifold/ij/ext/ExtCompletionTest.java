package manifold.ij.ext;

import com.intellij.codeInsight.lookup.LookupElement;
import java.util.Arrays;
import java.util.List;
import manifold.ij.AbstractManifoldCodeInsightTest;

public class ExtCompletionTest extends AbstractManifoldCodeInsightTest
{
  public void testCompletionLevel1() throws Exception
  {
    //myFixture.copyFileToProject( "image/sample/Logo.png" );
    myFixture.configureByFile( "ext/completion/TestExtCompletion_1.java" );

    LookupElement[] complete = myFixture.completeBasic();
    List<String> strings = myFixture.getLookupElementStrings();
    assertTrue( strings.containsAll( Arrays.asList( "substringAfter", "substringBefore" ) ) );
  }
}
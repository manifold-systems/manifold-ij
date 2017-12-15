package manifold.ij.js;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import java.util.Arrays;
import java.util.List;
import manifold.ij.AbstractManifoldCodeInsightTest;

public class JsCompletionTest extends AbstractManifoldCodeInsightTest
{
  public void testCompletionLevel1() throws Exception
  {
    myFixture.copyFileToProject( "js/sample/MyJsClass.js" );
    myFixture.configureByFile( "js/completion/TestJsCompletion_1.java" );

    LookupElement[] complete = myFixture.complete( CompletionType.BASIC );
    List<String> strings = myFixture.getLookupElementStrings();
    assertTrue( strings.containsAll( Arrays.asList( "hi", "getYeah", "setYeah" ) ) );
  }
}
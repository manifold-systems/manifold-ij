package manifold.ij.json;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import java.util.Arrays;
import java.util.List;
import manifold.ij.AbstractManifoldCodeInsightTest;

public class JsonCompletionTest extends AbstractManifoldCodeInsightTest
{
  public void testCompletionLevel1() throws Exception
  {
    myFixture.copyFileToProject( "json/sample/Person.json" );
    myFixture.configureByFile( "json/completion/TestJsonCompletion_1.java" );

    LookupElement[] complete = myFixture.complete( CompletionType.BASIC );
    List<String> strings = myFixture.getLookupElementStrings();
    assertTrue( strings.containsAll( Arrays.asList( "getFirstName","setFirstName", "getLastName","setLastName", "getAge","setAge", "toJson", "toXml" ) ) );
  }

  public void testParsing() throws Exception
  {
    myFixture.copyFileToProject( "json/sample/Outside.json" );
    myFixture.copyFileToProject( "json/sample/Junk.json" );
    myFixture.configureByFiles( "json/completion/TestJsonParsing_1.java" );

    myFixture.complete( CompletionType.BASIC );
    List<String> strings = myFixture.getLookupElementStrings();
    assertTrue( strings.containsAll( Arrays.asList( "charAt" ) ) );
  }
}
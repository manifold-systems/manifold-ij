package manifold.ij.properties;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import java.util.Arrays;
import java.util.List;
import manifold.ij.AbstractManifoldCodeInsightTest;

public class PropertiesCompletionTest extends AbstractManifoldCodeInsightTest
{
  public void testCompletionLevel1() throws Exception
  {
    myFixture.copyFileToProject( "properties/sample/MyProperties.properties" );
    myFixture.configureByFile( "properties/completion/TestPropertiesCompletion_Level1.java" );

    LookupElement[] complete = myFixture.complete( CompletionType.BASIC );
    List<String> strings = myFixture.getLookupElementStrings();
    assertTrue( strings.containsAll( Arrays.asList( "bye", "hello", "getValueByName" ) ) );
  }

  public void testCompletionLevel2() throws Exception
  {
    myFixture.copyFileToProject( "properties/sample/MyProperties.properties" );
    myFixture.configureByFile( "properties/completion/TestPropertiesCompletion_Level2.java" );

    LookupElement[] complete = myFixture.complete( CompletionType.BASIC );
    List<String> strings = myFixture.getLookupElementStrings();
    assertTrue( strings.containsAll( Arrays.asList( "good", "getValue", "getValueByName" ) ) );
  }
}
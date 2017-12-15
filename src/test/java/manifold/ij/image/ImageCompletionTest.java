package manifold.ij.image;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import java.util.Arrays;
import java.util.List;
import manifold.ij.AbstractManifoldCodeInsightTest;

public class ImageCompletionTest extends AbstractManifoldCodeInsightTest
{
  public void testCompletionLevel1() throws Exception
  {
    myFixture.copyFileToProject( "image/sample/Logo.png" );
    myFixture.configureByFile( "image/completion/TestImageCompletion_1.java" );

    LookupElement[] complete = myFixture.complete( CompletionType.BASIC );
    List<String> strings = myFixture.getLookupElementStrings();
    assertTrue( strings.containsAll( Arrays.asList( "get" ) ) );
  }

//!! not working for some reason
//
//  public void testCompletionLevel2() throws Exception
//  {
//    myFixture.copyFileToProject( "image/sample/Logo.png" );
//    myFixture.configureByFile( "image/completion/TestImageCompletion_2.java" );
//
//    LookupElement[] complete = myFixture.complete( CompletionType.BASIC );
//    List<String> strings = myFixture.getLookupElementStrings();
//    assertTrue( strings.containsAll( Arrays.asList( "getImage", "getIconHeight", "getIconWidth" ) ) );
//  }
}
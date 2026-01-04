package manifold.ij.image;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.JavaPsiClassReferenceElement;
import com.intellij.codeInsight.lookup.LookupElement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import manifold.ij.AbstractManifoldCodeInsightTest;

public class ImageCompletionTest extends AbstractManifoldCodeInsightTest
{
  public void testCompletionLevel1() throws Exception
  {
    myFixture.copyFileToProject( "image/sample/Logo.png" );
    myFixture.configureByFile( "image/completion/TestImageCompletion_1.java" );

    myFixture.complete( CompletionType.BASIC );
    List<String> strings = myFixture.getLookupElementStrings();
    assertNotNull( strings );
    assertTrue( strings.containsAll( Collections.singletonList( "get" ) ) );
  }

  public void testCompletionLevel2() throws Exception
  {
    myFixture.copyFileToProject( "image/sample/Logo.png" );
    myFixture.configureByFile( "image/completion/TestImageCompletion_2.java" );

    myFixture.complete( CompletionType.BASIC );
    List<String> strings = myFixture.getLookupElementStrings();
    assertNotNull( strings );
    assertTrue( strings.containsAll( Arrays.asList( "getImage", "getIconHeight", "getIconWidth" ) ) );
  }

  public void testCompletion_Type() throws Exception
  {
    myFixture.copyFileToProject( "image/sample/Logo.png" );
    myFixture.configureByFile( "image/completion/TestImageCompletion_Type.java" );

    LookupElement[] complete = myFixture.complete( CompletionType.BASIC );
    List<String> strings = myFixture.getLookupElementStrings();
    assertNotNull( strings );
    assertEquals( "Logo_png", strings.get( 0 ) );
    assertEquals( "image.sample.Logo_png", ((JavaPsiClassReferenceElement)complete[0]).getQualifiedName() );
  }
}
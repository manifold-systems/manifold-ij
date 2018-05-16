package manifold.ij.template;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.JavaPsiClassReferenceElement;
import com.intellij.codeInsight.lookup.LookupElement;
import java.util.Collections;
import java.util.List;
import manifold.ij.AbstractManifoldCodeInsightTest;

public class TemplateCompletionTest extends AbstractManifoldCodeInsightTest
{
  public void testCompletionLevel2()
  {
    myFixture.copyFileToProject( "template/sample/MyTemplate.html.mtl" );
    myFixture.configureByFile( "template/completion/TestTemplateCompletion_1.java" );

    myFixture.complete( CompletionType.BASIC );
    List<String> strings = myFixture.getLookupElementStrings();
    assertNotNull( strings );
    assertTrue( strings.containsAll( Collections.singletonList( "render" ) ) );
  }

  public void testCompletion_Type()
  {
    myFixture.copyFileToProject( "template/sample/MyTemplate.html.mtl" );
    myFixture.configureByFile( "template/completion/TestTemplateCompletion_Type.java" );

    LookupElement[] complete = myFixture.complete( CompletionType.BASIC );
    List<String> strings = myFixture.getLookupElementStrings();
    assertNotNull( strings );
    assertEquals( "MyTemplate", strings.get( 0 ) );
    assertEquals( "template.sample.MyTemplate", ((JavaPsiClassReferenceElement)complete[0]).getQualifiedName() );
  }

  public void testCompletionJava()
  {
    myFixture.configureByFile( "template/completion/MyTemplate_Java.html.mtl" );

    myFixture.complete( CompletionType.BASIC );
    List<String> strings = myFixture.getLookupElementStrings();
    assertNotNull( strings );
    assertTrue( strings.containsAll( Collections.singletonList( "charAt" ) ) );
  }

  public void testCompletionHtml()
  {
    myFixture.configureByFile( "template/completion/MyTemplate_Html.html.mtl" );

    myFixture.complete( CompletionType.BASIC );
    List<String> strings = myFixture.getLookupElementStrings();
    assertNotNull( strings );
    assertTrue( strings.containsAll( Collections.singletonList( "html" ) ) );
  }
}
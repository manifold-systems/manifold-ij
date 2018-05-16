package manifold.ij.template;

import java.util.List;
import manifold.ij.AbstractManifoldCodeInsightTest;
import manifold.ij.extensions.ManifoldPsiClass;

public class TemplateGotoClassTest extends AbstractManifoldCodeInsightTest
{
  public void testCompletion_Type()
  {
    myFixture.copyFileToProject( "template/sample/MyTemplate.html.mtl" );
    List<Object> results = myFixture.getGotoClassResults( "MyTempla", false, null );
    assertTrue( results.get( 0 ) instanceof ManifoldPsiClass );
    ManifoldPsiClass psiClass = (ManifoldPsiClass)results.get( 0 );
    assertEquals( "template.sample.MyTemplate", psiClass.getQualifiedName() );
  }
}
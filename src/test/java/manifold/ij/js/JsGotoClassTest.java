package manifold.ij.js;

import java.util.List;
import manifold.ij.AbstractManifoldCodeInsightTest;
import manifold.ij.extensions.ManifoldPsiClass;

public class JsGotoClassTest extends AbstractManifoldCodeInsightTest
{
  public void testCompletion_Type() throws Exception
  {
    myFixture.copyFileToProject( "js/sample/MyJsClass.js" );
    List<Object> results = myFixture.getGotoClassResults( "MyJsCla", false, null );
    assertTrue( results.get( 0 ) instanceof ManifoldPsiClass );
    ManifoldPsiClass psiClass = (ManifoldPsiClass)results.get( 0 );
    assertEquals( "js.sample.MyJsClass", psiClass.getQualifiedName() );
  }
}
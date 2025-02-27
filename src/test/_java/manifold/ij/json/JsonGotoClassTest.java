package manifold.ij.json;

import java.util.List;
import manifold.ij.AbstractManifoldCodeInsightTest;
import manifold.ij.extensions.ManifoldPsiClass;

public class JsonGotoClassTest extends AbstractManifoldCodeInsightTest
{
  public void testCompletion_Type() throws Exception
  {
    myFixture.copyFileToProject( "json/sample/Person.json" );
    List<Object> results = myFixture.getGotoClassResults( "Perso", false, null );
    assertTrue( results.get( 0 ) instanceof ManifoldPsiClass );
    ManifoldPsiClass psiClass = (ManifoldPsiClass)results.get( 0 );
    assertEquals( "json.sample.Person", psiClass.getQualifiedName() );
  }
}
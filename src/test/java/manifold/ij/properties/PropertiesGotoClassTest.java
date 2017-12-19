package manifold.ij.properties;

import java.util.List;
import manifold.ij.AbstractManifoldCodeInsightTest;
import manifold.ij.extensions.ManifoldPsiClass;

public class PropertiesGotoClassTest extends AbstractManifoldCodeInsightTest
{
  public void testCompletion_Type() throws Exception
  {
    myFixture.copyFileToProject( "properties/sample/MyProperties.properties" );
    List<Object> results = myFixture.getGotoClassResults( "MyProperti", false, null );
    assertTrue( results.get( 0 ) instanceof ManifoldPsiClass );
    ManifoldPsiClass psiClass = (ManifoldPsiClass)results.get( 0 );
    assertEquals( "properties.sample.MyProperties", psiClass.getQualifiedName() );
  }
}
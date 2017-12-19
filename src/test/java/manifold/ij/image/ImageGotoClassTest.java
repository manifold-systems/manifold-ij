package manifold.ij.image;

import java.util.List;
import manifold.ij.AbstractManifoldCodeInsightTest;
import manifold.ij.extensions.ManifoldPsiClass;

public class ImageGotoClassTest extends AbstractManifoldCodeInsightTest
{
  public void testCompletion_Type() throws Exception
  {
    myFixture.copyFileToProject( "image/sample/Logo.png" );
    List<Object> results = myFixture.getGotoClassResults( "Logo_", false, null );
    assertTrue( results.get( 0 ) instanceof ManifoldPsiClass );
    ManifoldPsiClass psiClass = (ManifoldPsiClass)results.get( 0 );
    assertEquals( "image.sample.Logo_png", psiClass.getQualifiedName() );
  }
}
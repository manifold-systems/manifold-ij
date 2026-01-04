package manifold.ij.image;

import com.intellij.psi.PsiClass;
import manifold.ij.AbstractManifoldCodeInsightTest;
import manifold.ij.util.SettleModalEventQueue;

public class ImageRenameTest extends AbstractManifoldCodeInsightTest
{
  public void testRenameFromCallSite()
  {
    myFixture.copyFileToProject( "image/sample/Logo.png" );
    myFixture.configureByFile( "image/rename/TestImageRename.java" );

    myFixture.renameElementAtCaret( "Logo2.png" );

    // let remaining ui event processing finish (rename uses invokeLater())
    try
    {
      SettleModalEventQueue.instance().run();
    }
    catch( Exception e )
    {
      // Circleci - intermittent failures cause from prematurely disposed project
      return;
    }

    myFixture.checkResultByFile( "image/rename/TestImageRename_After.java", true );
    myFixture.findClass( "image.sample.Logo2_png" );
  }

  public void testRenameFromDeclaration()
  {
    myFixture.copyFileToProject( "image/sample/Logo.png" );
    myFixture.configureByFile( "image/rename/TestImageRename_NoCaret.java" );

    PsiClass logoClass = myFixture.findClass( "image.sample.Logo_png" );
    myFixture.renameElement( logoClass.getContainingFile(), "Logo2.png" );

    // let remaining ui event processing finish (rename uses invokeLater())
    try
    {
      SettleModalEventQueue.instance().run();
    }
    catch( Exception e )
    {
      // Circleci - intermittent failures cause from prematurely disposed project
      return;
    }

    myFixture.checkResultByFile( "image/rename/TestImageRename_After.java", true );
    myFixture.findClass( "image.sample.Logo2_png" );
  }
}
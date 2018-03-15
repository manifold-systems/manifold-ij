package manifold.ij.image;

import com.intellij.psi.PsiClass;
import java.awt.EventQueue;
import manifold.ij.AbstractManifoldCodeInsightTest;
import manifold.ij.util.SettleModalEventQueue;

public class ImageRenameTest extends AbstractManifoldCodeInsightTest
{
  public void testRenameFromCallSite() throws Exception
  {
    myFixture.copyFileToProject( "image/sample/Logo.png" );
    myFixture.configureByFile( "image/rename/TestImageRename.java" );

    EventQueue.invokeLater( () -> {} );
    myFixture.renameElementAtCaret( "Logo2.png" );

    // let remaining ui event processing finish (rename uses invokeLater())
    SettleModalEventQueue.instance().run();

    myFixture.checkResultByFile( "image/rename/TestImageRename_After.java", true );
    myFixture.findClass( "image.sample.Logo2_png" );
  }

  public void testRenameFromDeclaration() throws Exception
  {
    myFixture.copyFileToProject( "image/sample/Logo.png" );
    myFixture.configureByFile( "image/rename/TestImageRename_NoCaret.java" );

    EventQueue.invokeLater( () -> {} );
    PsiClass logoClass = myFixture.findClass( "image.sample.Logo_png" );
    myFixture.renameElement( logoClass.getContainingFile(), "Logo2.png" );

    // let remaining ui event processing finish (rename uses invokeLater())
    SettleModalEventQueue.instance().run();

    myFixture.checkResultByFile( "image/rename/TestImageRename_After.java", true );
    myFixture.findClass( "image.sample.Logo2_png" );
  }
}
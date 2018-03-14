package manifold.ij.extensionProducer;

import manifold.ij.AbstractManifoldCodeInsightTest;
import manifold.ij.util.SettleModalEventQueue;

public class ExtensionProducerRenameTest extends AbstractManifoldCodeInsightTest
{
  public void testRenameFromCallSite()
  {
    myFixture.copyFileToProject( "extensionProducer/sample/Sample.favs" );
    myFixture.configureByFile( "extensionProducer/rename/TestRename.java" );

    myFixture.renameElementAtCaret( "Foo" );

    // let remaining ui event processing finish (rename uses invokeLater())
    SettleModalEventQueue.instance().run();

    myFixture.checkResultByFile( "extensionProducer/rename/TestRename_After.java", true );
    myFixture.checkResultByFile( "extensionProducer/sample/Sample.favs", "extensionProducer/rename/Sample_After.favs", true );
  }


  public void testRenameFromDeclaration()
  {
    myFixture.copyFileToProject( "extensionProducer/rename/TestRename_Declaration.java" );
    myFixture.configureByFile( "extensionProducer/rename/Sample_Declaration.favs" );

    myFixture.renameElementAtCaret( "Foo" );

    // let remaining ui event processing finish (rename uses invokeLater())
    SettleModalEventQueue.instance().run();

    myFixture.checkResultByFile( "extensionProducer/rename/Sample_Declaration_After.favs", true );
    myFixture.checkResultByFile( "extensionProducer/rename/TestRename_Declaration.java", "extensionProducer/rename/TestRename_Declaration_After.java", true );
  }

}
package manifold.ij.xml;

import manifold.ij.AbstractManifoldCodeInsightTest;
import manifold.ij.util.SettleModalEventQueue;

public class XmlRenameTest extends AbstractManifoldCodeInsightTest
{
  public void testRenameElementPropertyFromDeclaration()
  {
    myFixture.copyFileToProject( "xml/rename/TestXmlRename_ElementProperty_Declaration.java" );
    myFixture.configureByFile( "xml/rename/Stuff_ElementProperty_Declaration.xml" );

    myFixture.renameElementAtCaret( "thingzzz" );

    // let remaining ui event processing finish (rename uses invokeLater())
    SettleModalEventQueue.instance().run();

    myFixture.checkResultByFile( "xml/rename/Stuff_ElementProperty_Declaration_After.xml", true );
    myFixture.checkResultByFile( "xml/rename/TestXmlRename_ElementProperty_Declaration.java", "xml/rename/TestXmlRename_ElementProperty_Declaration_After.java", true );
  }

}
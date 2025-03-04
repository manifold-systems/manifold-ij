package manifold.ij.json;

import manifold.ij.AbstractManifoldCodeInsightTest;
import manifold.ij.util.SettleModalEventQueue;

public class JsonRenameTest extends AbstractManifoldCodeInsightTest
{
//## todo: make this work somehow
//  public void testRenameUnionPropertyFromCallSite() throws Exception
//  {
//    myFixture.copyFileToProject( "json/sample/StrangeUriFormats.json" );
//    myFixture.configureByFile( "json/rename/TestJsonRename_Union_Property.java" );
//
//    myFixture.renameElementAtCaret( "nc:Vehiclezzz" );
//
//    // let remaining ui event processing finish (rename uses invokeLater())
//    SettleModalEventQueue.instance().run();
//
//    myFixture.checkResultByFile( "json/rename/TestJsonRename_Union_Property_After.java", true );
//    myFixture.checkResultByFile( "json/sample/StrangeUriFormats.json", "json/rename/StrangeUriFormats_After.json", true );
//  }


  public void testRenameUnionPropertyFromDeclaration() throws Exception
  {
    myFixture.copyFileToProject( "json/rename/TestJsonRename_UnionProperty_Declaration.java" );
    myFixture.configureByFile( "json/rename/StrangeUriFormats_UnionProperty_Declaration.json" );

    myFixture.renameElementAtCaret( "nc:Vehiclezzz" );

    // let remaining ui event processing finish (rename uses invokeLater())
    SettleModalEventQueue.instance().run();

    myFixture.checkResultByFile( "json/rename/StrangeUriFormats_UnionProperty_Declaration_After.json", true );
    myFixture.checkResultByFile( "json/rename/TestJsonRename_UnionProperty_Declaration.java", "json/rename/TestJsonRename_UnionProperty_Declaration_After.java", true );
  }

}
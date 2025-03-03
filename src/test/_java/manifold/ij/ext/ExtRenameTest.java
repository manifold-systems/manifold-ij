package manifold.ij.ext;

import manifold.ij.AbstractManifoldCodeInsightTest;

public class ExtRenameTest extends AbstractManifoldCodeInsightTest
{
  public void testRenameFromCallSite() throws Exception
  {
    myFixture.copyFileToProject( "extensions/java/lang/String/MyStringExt.java" );
    myFixture.configureByFile( "ext/rename/TestExtRename.java" );

    myFixture.renameElementAtCaret( "hiWorld" );

    myFixture.checkResultByFile( "ext/rename/TestExtRename_After.java", true );
    myFixture.checkResultByFile( "extensions/java/lang/String/MyStringExt.java",
                                 "ext/rename/MyStringExt_After.java", true );
  }

  public void testRenameFromDeclaration() throws Exception
  {
    myFixture.copyFileToProject( "ext/rename/TestExtRename_NoCaret.java" );
    myFixture.configureByFile( "extensions/java/lang/String/MyStringExt_Caret.java" );

    myFixture.renameElementAtCaret( "hiWorld" );

    myFixture.checkResultByFile( "ext/rename/MyStringExt_Caret_After.java", true );
    myFixture.checkResultByFile( "ext/rename/TestExtRename_NoCaret.java",
      "ext/rename/TestExtRename_After.java", true );
  }
}
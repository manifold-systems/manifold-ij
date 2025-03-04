package manifold.ij.js;

import manifold.ij.AbstractManifoldCodeInsightTest;
import manifold.ij.util.SettleModalEventQueue;

public class JsRenameTest extends AbstractManifoldCodeInsightTest
{
  public void testRenameClassFromCallSite() throws Exception
  {
    myFixture.copyFileToProject( "js/sample/MyJsClass.js" );
    myFixture.configureByFile( "js/rename/TestJsRename_Class.java" );

    myFixture.renameElementAtCaret( "MyJsClass2.js" );

    // let remaining ui event processing finish (rename uses invokeLater())
    SettleModalEventQueue.instance().run();

    myFixture.checkResultByFile( "js/rename/TestJsRename_Class_After.java", true );
    myFixture.findClass( "js.sample.MyJsClass2" );
  }

  public void testRenameMethodFromCallSite() throws Exception
  {
    myFixture.copyFileToProject( "js/sample/MyJsClass.js" );
    myFixture.configureByFile( "js/rename/TestJsRename_Method.java" );

    myFixture.renameElementAtCaret( "hi2" );

    // let remaining ui event processing finish (rename uses invokeLater())
    SettleModalEventQueue.instance().run();

    myFixture.checkResultByFile( "js/rename/TestJsRename_Method_After.java", true );
    myFixture.checkResultByFile( "js/sample/MyJsClass.js", "js/rename/MyJsClass_Method_After.js", true );
  }

  public void testRenameGetterFromCallSite() throws Exception
  {
    myFixture.copyFileToProject( "js/sample/MyJsClass.js" );
    myFixture.configureByFile( "js/rename/TestJsRename_Getter.java" );

    myFixture.renameElementAtCaret( "Yeah2" );

    // let remaining ui event processing finish (rename uses invokeLater())
    SettleModalEventQueue.instance().run();

    myFixture.checkResultByFile( "js/rename/TestJsRename_Getter_After.java", true );
    myFixture.checkResultByFile( "js/sample/MyJsClass.js", "js/rename/MyJsClass_Getter_After.js", true );
  }

  public void testRenameSetterFromCallSite() throws Exception
  {
    myFixture.copyFileToProject( "js/sample/MyJsClass.js" );
    myFixture.configureByFile( "js/rename/TestJsRename_Setter.java" );

    myFixture.renameElementAtCaret( "Yeah2" );

    // let remaining ui event processing finish (rename uses invokeLater())
    SettleModalEventQueue.instance().run();

    myFixture.checkResultByFile( "js/rename/TestJsRename_Setter_After.java", true );
    myFixture.checkResultByFile( "js/sample/MyJsClass.js", "js/rename/MyJsClass_Setter_After.js", true );
  }

  public void testRenameClassFromDeclaration() throws Exception
  {
    myFixture.copyFileToProject( "js/rename/Test_MyJsClass_Caret_ClassDeclaration.java" );
    myFixture.configureByFile( "js/rename/MyJsClass_Caret_ClassDeclaration.js" );

    myFixture.renameElementAtCaret( "MyJsClass_Caret_ClassDeclaration2.js" );

    // let remaining ui event processing finish (rename uses invokeLater())
    SettleModalEventQueue.instance().run();

    myFixture.checkResultByFile( "js/rename/MyJsClass_Caret_ClassDeclaration2.js", true );
    myFixture.checkResultByFile( "js/rename/Test_MyJsClass_Caret_ClassDeclaration.java", "js/rename/Test_MyJsClass_Caret_ClassDeclaration_After.java", true );
    myFixture.findClass( "js.rename.MyJsClass_Caret_ClassDeclaration2" );
  }

  public void testRenameMethodFromDeclaration() throws Exception
  {
    myFixture.copyFileToProject( "js/rename/Test_MyJsClass_Caret_MethodDeclaration.java" );
    myFixture.configureByFile( "js/rename/MyJsClass_Caret_MethodDeclaration.js" );

    myFixture.renameElementAtCaret( "hi2" );

    // let remaining ui event processing finish (rename uses invokeLater())
    SettleModalEventQueue.instance().run();

    myFixture.checkResultByFile( "js/rename/MyJsClass_Caret_MethodDeclaration2.js", true );
    myFixture.checkResultByFile( "js/rename/Test_MyJsClass_Caret_MethodDeclaration.java", "js/rename/Test_MyJsClass_Caret_MethodDeclaration_After.java", true );
  }

  public void testRenameGetterFromDeclaration() throws Exception
  {
    myFixture.copyFileToProject( "js/rename/Test_MyJsClass_Caret_GetterDeclaration.java" );
    myFixture.configureByFile( "js/rename/MyJsClass_Caret_GetterDeclaration.js" );

    myFixture.renameElementAtCaret( "yeah2" );

    // let remaining ui event processing finish (rename uses invokeLater())
    SettleModalEventQueue.instance().run();

    myFixture.checkResultByFile( "js/rename/MyJsClass_Caret_GetterDeclaration2.js", true );
    myFixture.checkResultByFile( "js/rename/Test_MyJsClass_Caret_GetterDeclaration.java", "js/rename/Test_MyJsClass_Caret_GetterDeclaration_After.java", true );
  }
  
  public void testRenameSetterFromDeclaration() throws Exception
  {
    myFixture.copyFileToProject( "js/rename/Test_MyJsClass_Caret_SetterDeclaration.java" );
    myFixture.configureByFile( "js/rename/MyJsClass_Caret_SetterDeclaration.js" );

    myFixture.renameElementAtCaret( "yeah2" );

    // let remaining ui event processing finish (rename uses invokeLater())
    SettleModalEventQueue.instance().run();

    myFixture.checkResultByFile( "js/rename/MyJsClass_Caret_SetterDeclaration2.js", true );
    myFixture.checkResultByFile( "js/rename/Test_MyJsClass_Caret_SetterDeclaration.java", "js/rename/Test_MyJsClass_Caret_SetterDeclaration_After.java", true );
  }
}
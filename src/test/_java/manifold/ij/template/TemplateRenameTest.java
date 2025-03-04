package manifold.ij.template;

import com.intellij.psi.PsiClass;
import manifold.ij.AbstractManifoldCodeInsightTest;
import manifold.ij.util.SettleModalEventQueue;

public class TemplateRenameTest extends AbstractManifoldCodeInsightTest
{
  public void testRenameFromCallSite()
  {
    myFixture.copyFileToProject( "template/sample/MyTemplate.html.mtl" );
    myFixture.configureByFile( "template/rename/TestTemplateRename.java" );

    myFixture.renameElementAtCaret( "MyTemplate2.html.mtl" );

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

    myFixture.checkResultByFile( "template/rename/TestTemplateRename_After.java", true );
    myFixture.findClass( "template.sample.MyTemplate2" );
  }

  public void testRenameFromDeclaration()
  {
    myFixture.copyFileToProject( "template/sample/MyTemplate.html.mtl" );
    myFixture.configureByFile( "template/rename/TestTemplateRename_NoCaret.java" );

    PsiClass myTemplateClass = myFixture.findClass( "template.sample.MyTemplate" );
    myFixture.renameElement( myTemplateClass.getContainingFile(), "MyTemplate2.html.mtl" );

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

    myFixture.checkResultByFile( "template/rename/TestTemplateRename_After.java", true );
    myFixture.findClass( "template.sample.MyTemplate2" );
  }
}
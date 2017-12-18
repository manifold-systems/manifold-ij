package manifold.ij.js;

import com.intellij.usageView.UsageInfo;
import java.util.Collection;
import manifold.ij.AbstractManifoldCodeInsightTest;

public class JsUsagesTest extends AbstractManifoldCodeInsightTest
{
  public void testFindUsages_Class_FromUseSite() throws Exception
  {
    Collection<UsageInfo> usageInfos = myFixture.testFindUsages( "js/usages/TestJsUsages_Class_FromUseSite.java", "js/sample/MyJsClass.js" );
    assertEquals( 3, usageInfos.size() );
  }

  public void testFindUsages_Class_FromDeclaration() throws Exception
  {
    Collection<UsageInfo> usageInfos = myFixture.testFindUsages( "js/usages/MyJsClass_Caret_ClassDeclaration.js", "js/usages/TestJsUsages_Class_FromDeclaration.java" );
    assertEquals( 3, usageInfos.size() );
  }

  public void testFindUsages_Method_FromDeclaration() throws Exception
  {
    Collection<UsageInfo> usageInfos = myFixture.testFindUsages( "js/usages/MyJsClass_Caret_MethodDeclaration.js", "js/usages/TestJsUsages_Method_FromDeclaration.java" );
    assertEquals( 1, usageInfos.size() );
  }
}
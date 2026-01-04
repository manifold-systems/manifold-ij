package manifold.ij.template;

import com.intellij.usageView.UsageInfo;
import java.util.Collection;
import manifold.ij.AbstractManifoldCodeInsightTest;

public class TemplateUsagesTest extends AbstractManifoldCodeInsightTest
{
  public void testUsages()
  {
    Collection<UsageInfo> usageInfos = myFixture.testFindUsages( "template/usages/MyTemplate_Usages_Java_1.html.mtl" );
    assertEquals( 5, usageInfos.size() );
  }

}

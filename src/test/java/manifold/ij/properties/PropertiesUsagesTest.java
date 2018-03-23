package manifold.ij.properties;

import com.intellij.usageView.UsageInfo;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import manifold.ij.AbstractManifoldCodeInsightTest;

public class PropertiesUsagesTest extends AbstractManifoldCodeInsightTest
{
  public void testFindUsages_Simple_FromDeclaration()
  {
    Collection<UsageInfo> usageInfos = myFixture.testFindUsages( "properties/usages/MyProperties_Caret_Simple.properties", "properties/usages/TestPropertiesUsages_Simple.java" );
    assertEquals( 1, usageInfos.size() );
    //noinspection ConstantConditions
    Set<String> usagesText = usageInfos.stream().map( e -> e.getElement().getText() ).collect( Collectors.toSet() );
    assertTrue( usagesText.contains( "MyProperties_Caret_Simple.bye" ) );
  }

  public void testFindUsages_Nested_FromDeclaration()
  {
    Collection<UsageInfo> usageInfos = myFixture.testFindUsages( "properties/usages/MyProperties_Caret_Nested.properties", "properties/usages/TestPropertiesUsages_Nested.java" );
    assertEquals( 1, usageInfos.size() );
    //noinspection ConstantConditions
    Set<String> usagesText = usageInfos.stream().map( e -> e.getElement().getText() ).collect( Collectors.toSet() );
    assertTrue( usagesText.contains( "MyProperties_Caret_Nested.bye.good" ) );
  }
}
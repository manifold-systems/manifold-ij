package manifold.ij.xml;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.usageView.UsageInfo;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import manifold.ij.AbstractManifoldCodeInsightTest;

public class XmlUsagesTest extends AbstractManifoldCodeInsightTest
{
  public void testFindUsages_Class_FromUseSite()
  {
    Collection<UsageInfo> usageInfos = myFixture.testFindUsages(
      "xml/usages/TestXmlUsages_Class_FromUseSite.java", "xml/sample/Stuff.xml" );
    assertEquals( 3, usageInfos.size() );
  }

  public void testFindUsages_Getter_FromUseSite()
  {
    Collection<UsageInfo> usageInfos = myFixture.testFindUsages(
      "xml/usages/TestXmlUsages_Getter_FromUseSite.java", "xml/sample/Stuff.xml" );
    assertEquals( 1, usageInfos.size() );
  }

  public void testFindUsages_Setter_FromUseSite()
  {
    Collection<UsageInfo> usageInfos = myFixture.testFindUsages(
      "xml/usages/TestXmlUsages_Setter_FromUseSite.java", "xml/sample/Stuff.xml" );
    assertEquals( 1, usageInfos.size() );
  }

  public void testFindUsages_Class_FromFile()
  {
    myFixture.copyFileToProject( "xml/sample/Stuff.xml" );
    myFixture.copyFileToProject( "xml/usages/TestXmlUsages_Class_FromUseSite.java" );
    PsiClass psiClass = myFixture.findClass( "xml.sample.Stuff" );
    assertNotNull( psiClass );
    PsiFile containingFile = psiClass.getContainingFile();
    assertNotNull( containingFile );
    Collection<UsageInfo> usageInfos = myFixture.findUsages( containingFile );
    assertEquals( 3, usageInfos.size() );
  }

  public void testFindUsages_Property_FromDeclaration()
  {
    Collection<UsageInfo> usageInfos = myFixture.testFindUsages( "xml/usages/Stuff_Caret_PropertyDeclaration.xml", "xml/usages/TestXmlUsages_Property.java" );
    assertEquals( 2, usageInfos.size() );
    //noinspection ConstantConditions
    Set<String> usagesText = usageInfos.stream().map( e -> e.getElement().getText() ).collect( Collectors.toSet() );
    assertTrue( usagesText.contains( "t.getOne" ) );
    assertTrue( usagesText.contains( "t.setOne" ) );
  }
}
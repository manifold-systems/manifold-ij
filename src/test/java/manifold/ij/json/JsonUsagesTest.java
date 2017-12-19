package manifold.ij.json;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.usageView.UsageInfo;
import java.util.Collection;
import java.util.Iterator;
import manifold.ij.AbstractManifoldCodeInsightTest;

public class JsonUsagesTest extends AbstractManifoldCodeInsightTest
{
  public void testFindUsages_Class_FromUseSite() throws Exception
  {
    Collection<UsageInfo> usageInfos = myFixture.testFindUsages(
      "json/usages/TestJsonUsages_Class_FromUseSite.java", "json/sample/Person.json" );
    assertEquals( 3, usageInfos.size() );
  }

  public void testFindUsages_Getter_FromUseSite() throws Exception
  {
    Collection<UsageInfo> usageInfos = myFixture.testFindUsages(
      "json/usages/TestJsonUsages_Getter_FromUseSite.java", "json/sample/Person.json" );
    assertEquals( 1, usageInfos.size() );
  }

  public void testFindUsages_Setter_FromUseSite() throws Exception
  {
    Collection<UsageInfo> usageInfos = myFixture.testFindUsages(
      "json/usages/TestJsonUsages_Setter_FromUseSite.java", "json/sample/Person.json" );
    assertEquals( 1, usageInfos.size() );
  }

  public void testFindUsages_Class_FromFile() throws Exception
  {
    myFixture.copyFileToProject( "json/sample/Person.json" );
    myFixture.copyFileToProject( "json/usages/TestJsonUsages_Class_FromUseSite.java" );
    PsiClass psiClass = myFixture.findClass( "json.sample.Person" );
    assertNotNull( psiClass );
    PsiFile containingFile = psiClass.getContainingFile();
    assertNotNull( containingFile );
    Collection<UsageInfo> usageInfos = myFixture.findUsages( containingFile );
    assertEquals( 3, usageInfos.size() );
  }

  public void testFindUsages_Property_FromDeclaration() throws Exception
  {
    Collection<UsageInfo> usageInfos = myFixture.testFindUsages( "json/usages/Person_Caret_PropertyDeclaration.json", "json/usages/TestJsonUsages_Property.java" );
    assertEquals( 2, usageInfos.size() );
    //noinspection ConstantConditions
    Iterator<UsageInfo> iterator = usageInfos.iterator();
    assertEquals( "person.getLastName", iterator.next().getElement().getText() );
    assertEquals( "person.setLastName", iterator.next().getElement().getText() );
  }

//  public void testFindUsages_Getter_FromDeclaration() throws Exception
//  {
//    Collection<UsageInfo> usageInfos = myFixture.testFindUsages( "js/usages/MyJsClass_Caret_GetterDeclaration.js", "js/usages/TestJsUsages_Getter_FromDeclaration.java" );
//    assertEquals( 1, usageInfos.size() );
//    //noinspection ConstantConditions
//    assertEquals( "js.getYeah", usageInfos.iterator().next().getElement().getText() );
//  }
//
//  public void testFindUsages_Setter_FromDeclaration() throws Exception
//  {
//    Collection<UsageInfo> usageInfos = myFixture.testFindUsages( "js/usages/MyJsClass_Caret_SetterDeclaration.js", "js/usages/TestJsUsages_Setter_FromDeclaration.java" );
//    assertEquals( 1, usageInfos.size() );
//    //noinspection ConstantConditions
//    assertEquals( "js.setYeah", usageInfos.iterator().next().getElement().getText() );
//  }
}
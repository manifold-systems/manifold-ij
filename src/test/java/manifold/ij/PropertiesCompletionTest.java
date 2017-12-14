package manifold.ij;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class PropertiesCompletionTest extends LightCodeInsightFixtureTestCase
{
  @Override
  protected void setUp() throws Exception
  {
    super.setUp();
    LanguageLevelProjectExtension.getInstance( getProject() ).setLanguageLevel( LanguageLevel.JDK_1_8 );
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor()
  {
    return new MyProjectDescriptor();
  }

  @Override
  protected String getTestDataPath()
  {
    try
    {
      File javaFile = new File( getClass().getResource( "/xyz/MyProperties.properties" ).toURI() );
      return javaFile.getParentFile().getParentFile().getAbsolutePath();
    }
    catch( Exception e )
    {
      throw new RuntimeException( e );
    }
  }

  public void testCompletionLevel1() throws Exception
  {
    myFixture.copyFileToProject( "xyz/MyProperties.properties" );
    myFixture.configureByFile( "xyz/TestPropertiesCompletion_Level1.java" );

    LookupElement[] complete = myFixture.complete( CompletionType.BASIC );
    List<String> strings = myFixture.getLookupElementStrings();
    assertTrue( strings.containsAll( Arrays.asList( "bye", "hello", "getValueByName" ) ) );
  }

  public void testCompletionLevel2() throws Exception
  {
    myFixture.copyFileToProject( "xyz/MyProperties.properties" );
    myFixture.configureByFile( "xyz/TestPropertiesCompletion_Level2.java" );

    LookupElement[] complete = myFixture.complete( CompletionType.BASIC );
    List<String> strings = myFixture.getLookupElementStrings();
    assertTrue( strings.containsAll( Arrays.asList( "good", "getValue", "getValueByName" ) ) );
  }

  public class MyProjectDescriptor extends LightProjectDescriptor
  {
    private Sdk _sdk;

    @NotNull
    @Override
    public ModuleType getModuleType()
    {
      return StdModuleTypes.JAVA;
    }

    @Override
    public Sdk getSdk()
    {
      return _sdk == null
             ? _sdk = JavaSdk.getInstance().createJdk( "java 1.8", "C:\\Program Files\\Java\\jdk1.8.0_131", false )
             : _sdk;
    }

    @Override
    protected Module createModule( @NotNull Project project, @NotNull String moduleFilePath )
    {
      Module module = super.createModule( project, moduleFilePath );
      PsiTestUtil.addLibrary( module, "C:\\manifold-systems\\manifold\\manifold-all\\target\\manifold-all-0.7-SNAPSHOT.jar" );
      PsiTestUtil.addLibrary( module, "C:\\Program Files\\Java\\jdk1.8.0_131\\lib\\tools.jar" );
      return module;
    }
  }

}
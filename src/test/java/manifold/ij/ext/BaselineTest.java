package manifold.ij.ext;

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.LightProjectDescriptor;
import java.io.File;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class BaselineTest extends LightFixtureCompletionTestCase
{
  public void testCompletion()
  {
    myFixture.configureByFile( "Foo.java" );

    complete();
    List<String> strings = myFixture.getLookupElementStrings();
    assertFalse( strings.isEmpty() );
  }

  @Override
  protected String getTestDataPath()
  {
    return "testData";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor()
  {
    return new MyProjectDescriptor();
  }

  protected class MyProjectDescriptor extends LightProjectDescriptor
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
             ? _sdk = JavaSdk.getInstance().createJdk( "1.8", new File( getPath_JdkRoot() + File.separatorChar + getJdkVersion() ).getAbsolutePath(), false )
             : _sdk;
    }


    /**
     * @return The root directory of JDKs
     */
    protected String getPath_JdkRoot()
    {
      return new File( "jdk" ).getAbsolutePath();
    }

    /**
     * @return The directory name of the Java version (a subdirectory of getPath_JdkRoot())
     */
    protected String getJdkVersion()
    {
      return "jdk1.8.0_131";
    }
  }

}

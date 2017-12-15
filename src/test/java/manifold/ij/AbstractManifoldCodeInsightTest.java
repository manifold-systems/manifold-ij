package manifold.ij;

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
import manifold.util.PathUtil;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractManifoldCodeInsightTest extends LightCodeInsightFixtureTestCase
{
  @Override
  protected void setUp() throws Exception
  {
    super.setUp();
    LanguageLevelProjectExtension.getInstance( getProject() ).setLanguageLevel( getLanguageLevel() );
  }

  @NotNull
  protected LanguageLevel getLanguageLevel()
  {
    return LanguageLevel.JDK_1_8;
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
      File javaFile = new File( getClass().getResource( "/root_marker.txt" ).toURI() );
      return javaFile.getParentFile().getAbsolutePath();
    }
    catch( Exception e )
    {
      throw new RuntimeException( e );
    }
  }

  /**
   * @return The root directory containing jar file library dependencies for the test project
   * e.g., manifold-all.jar
   */
  protected String getPath_LibRoot()
  {
    return "." + File.separatorChar + "lib";
  }

  /**
   * @return a list of just the jar file names (no path info)
   */
  protected List<String> getLibs()
  {
    return Arrays.asList( "manifold-all-0.7-SNAPSHOT.jar" );
  }

  /**
   * @return The root directory of JDKs
   */
  protected String getPath_JdkRoot()
  {
    return "." + File.separatorChar + "jdk";
  }

  /**
   * @return The directory name of the Java version (a subdirectory of getPath_JdkRoot())
   */
  protected String getJdkVersion()
  {
    return "jdk1.8.0_131";
  }

  private String findToolsJar()
  {
    String toolsJar = getPath_JdkRoot() + File.separatorChar + getJdkVersion() + File.separator + "lib" + File.separator + "tools.jar";
    if( !PathUtil.isFile( PathUtil.create( toolsJar ) ) )
    {
      throw new RuntimeException( "Could not find tools.jar" );
    }
    return toolsJar;
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
             ? _sdk = JavaSdk.getInstance().createJdk( getJdkVersion(), getPath_JdkRoot(), false )
             : _sdk;
    }

    @Override
    protected Module createModule( @NotNull Project project, @NotNull String moduleFilePath )
    {
      Module module = super.createModule( project, moduleFilePath );
      for( String jarFileName : getLibs() )
      {
        PsiTestUtil.addLibrary( module, getPath_LibRoot() + File.separatorChar + jarFileName );
      }
      PsiTestUtil.addLibrary( module, findToolsJar() );
      return module;
    }
  }
}
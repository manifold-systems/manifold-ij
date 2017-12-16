package manifold.ij;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import manifold.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractManifoldCodeInsightTest extends SomewhatLightCodeInsightFixtureTestCase
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
             ? _sdk = JavaSdk.getInstance().createJdk( getJdkVersion(), getPath_JdkRoot() + File.separatorChar + getJdkVersion(), false )
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

    /**
     * Override to handle real files, not "temp:" files (we use real files, not fake in-memory files,
     * which conflict with some manifold features such as Json where we expect a file to have a valid URL)
     */
    @Nullable
    @Override
    public VirtualFile createSourcesRoot( @NotNull Module module )
    {
      VirtualFile srcRoot = VirtualFileManager.getInstance().refreshAndFindFileByUrl( "file://" + myFixture.getTempDirFixture().getTempDirPath() );
      assert srcRoot != null;
      srcRoot.refresh( false, false );
      cleanSourceRoot( srcRoot );
      registerSourceRoot( module.getProject(), srcRoot );
      return srcRoot;
    }

    private void cleanSourceRoot( @NotNull VirtualFile contentRoot )
    {
      try
      {
        LocalFileSystem tempFs = (LocalFileSystem)contentRoot.getFileSystem();
        for( VirtualFile child : contentRoot.getChildren() )
        {
          if( !tempFs.exists( child ) )
          {
            tempFs.createChildFile( this, contentRoot, child.getName() );
          }
          child.delete( this );
        }
      }
      catch( IOException e )
      {
        throw new RuntimeException( e );
      }
    }

  }
}
package manifold.ij;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;
import java.io.File;

import manifold.util.ReflectUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Exactly the same as LightCodeInsightFixtureTestCase except uses TempDirTestFixtureImpl instead of LightTempDirTestFixtureImpl.
 * This is because the light temp dir stuff fails to work in some cases because it's in-memory file system protocol "temp:" is
 * invalid in the eyes of the URL class, which causes URL exceptions.  For instance, the Json manifold creates a URL from the resource files.
 */
public abstract class SomewhatLightCodeInsightFixtureTestCase extends UsefulTestCase
{
  public static final LightProjectDescriptor JAVA_1_4 = new DefaultLightProjectDescriptor()
  {
    @Override
    public Sdk getSdk()
    {
      return IdeaTestUtil.getMockJdk14();
    }

    @Override
    public void configureModule( @NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry )
    {
      model.getModuleExtension( LanguageLevelModuleExtension.class ).setLanguageLevel( LanguageLevel.JDK_1_4 );
    }
  };

  public static final LightProjectDescriptor JAVA_1_5 = new DefaultLightProjectDescriptor()
  {
    @Override
    public void configureModule( @NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry )
    {
      model.getModuleExtension( LanguageLevelModuleExtension.class ).setLanguageLevel( LanguageLevel.JDK_1_5 );
    }
  };

  public static final LightProjectDescriptor JAVA_1_6 = new DefaultLightProjectDescriptor()
  {
    @Override
    public void configureModule( @NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry )
    {
      model.getModuleExtension( LanguageLevelModuleExtension.class ).setLanguageLevel( LanguageLevel.JDK_1_6 );
    }
  };

  public static final LightProjectDescriptor JAVA_1_7 = new DefaultLightProjectDescriptor()
  {
    @Override
    public void configureModule( @NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry )
    {
      model.getModuleExtension( LanguageLevelModuleExtension.class ).setLanguageLevel( LanguageLevel.JDK_1_7 );
    }
  };

  public static final LightProjectDescriptor JAVA_8 = new DefaultLightProjectDescriptor()
  {
    @Override
    public Sdk getSdk()
    {
      return IdeaTestUtil.getMockJdk18();
    }

    @Override
    public void configureModule( @NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry )
    {
      model.getModuleExtension( LanguageLevelModuleExtension.class ).setLanguageLevel( LanguageLevel.JDK_1_8 );
    }
  };

  public static final LightProjectDescriptor JAVA_9 = new DefaultLightProjectDescriptor()
  {
    @Override
    public Sdk getSdk()
    {
      return IdeaTestUtil.getMockJdk9();
    }

    @Override
    public void configureModule( @NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry )
    {
      model.getModuleExtension( LanguageLevelModuleExtension.class ).setLanguageLevel( LanguageLevel.JDK_1_9 );
    }
  };

  public static final LightProjectDescriptor JAVA_LATEST = new DefaultLightProjectDescriptor();

  protected JavaCodeInsightTestFixture myFixture;
  protected Module myModule;

  @Override
  protected void setUp() throws Exception
  {
    super.setUp();

    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder;
//    if( VersionComparatorUtil.compare( ApplicationInfo.getInstance().getStrictVersion(), "2022.1.1" ) >= 0 )
    try
    {
      // IJ v. 2022.x
      fixtureBuilder = factory.createLightFixtureBuilder( getProjectDescriptor(), "hi" );
    }
    catch( Throwable t )
    {
      fixtureBuilder = (TestFixtureBuilder<IdeaProjectTestFixture>)ReflectUtil.method(
        factory, "createLightFixtureBuilder", LightProjectDescriptor.class )
        .invoke( getProjectDescriptor() );
    }
    IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture( fixture, new TempDirTestFixtureImpl() );

    myFixture.setUp();
    myFixture.setTestDataPath( getTestDataPath() );

    myModule = myFixture.getModule();

    LanguageLevelProjectExtension.getInstance( getProject() ).setLanguageLevel( LanguageLevel.JDK_1_8 );
  }

  @Override
  @SuppressWarnings("Duplicates")
  protected void tearDown() throws Exception
  {
    try
    {
      myFixture.tearDown();
    }
    catch( Throwable e )
    {
      addSuppressedException( e );
    }
    finally
    {
      myFixture = null;
      myModule = null;
      super.tearDown();
    }
  }

  /**
   * Returns relative path to the test data.
   */
  protected String getBasePath()
  {
    return "";
  }

  @NotNull
  protected LightProjectDescriptor getProjectDescriptor()
  {
    return JAVA_LATEST;
  }

  /**
   * Return absolute path to the test data. Not intended to be overridden.
   *
   * @see #getBasePath()
   */
  protected String getTestDataPath()
  {
    String communityPath = PlatformTestUtil.getCommunityPath().replace( File.separatorChar, '/' );
    String path = communityPath + getBasePath();
    return new File( path ).exists() ? path : communityPath + "/../" + getBasePath();
  }

  protected Project getProject()
  {
    return myFixture.getProject();
  }

  protected PsiFile getFile()
  {
    return myFixture.getFile();
  }

  protected Editor getEditor()
  {
    return myFixture.getEditor();
  }

  protected PsiManager getPsiManager()
  {
    return PsiManager.getInstance( getProject() );
  }

  public PsiElementFactory getElementFactory()
  {
    return JavaPsiFacade.getInstance( getProject() ).getElementFactory();
  }

  protected PsiFile createLightFile( FileType fileType, String text )
  {
    return PsiFileFactory.getInstance( getProject() ).createFileFromText( "a." + fileType.getDefaultExtension(), fileType, text );
  }

  public PsiFile createLightFile( String fileName, Language language, String text )
  {
    return PsiFileFactory.getInstance( getProject() ).createFileFromText( fileName, language, text, false, true );
  }
}
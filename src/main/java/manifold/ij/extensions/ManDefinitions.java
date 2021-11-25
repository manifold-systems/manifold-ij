package manifold.ij.extensions;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import java.io.File;
import java.util.Map;

import com.intellij.psi.util.PsiUtil;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.fs.IjFile;
import manifold.ij.util.FileUtil;
import manifold.preprocessor.definitions.Definitions;
import manifold.preprocessor.definitions.EnvironmentDefinitions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * For preprocessor.  Provides a Definitions specific to IDE settings, as opposed to javac settings.
 */
public class ManDefinitions extends Definitions
{
  private static final String MODULE_INFO_FILE = "module-info.java";
  private final ASTNode _chameleon;
  private final PsiJavaFile _psiFile;

  ManDefinitions( ASTNode chameleon, PsiJavaFile psiFile )
  {
    super( getFile( psiFile ) );
    _chameleon = chameleon;
    _psiFile = psiFile;
  }

  private static IjFile getFile( PsiFile psiFile )
  {
    if( psiFile == null )
    {
      return null;
    }
    return FileUtil.toIFile( psiFile.getProject(), FileUtil.toVirtualFile( psiFile ) );
  }

  public PsiJavaFile getPsiFile()
  {
    return _psiFile;
  }

  public ManModule getModule()
  {
    if( _psiFile != null )
    {
      return ManProject.getModule( _psiFile );
    }
    return ManProject.getModule( _chameleon.getPsi() );
  }

  @Override
  protected Map<String, String> loadEnvironmentDefinitions()
  {
    return new IdeEnvironmentDefinitions().getEnv();
  }

  private class IdeEnvironmentDefinitions extends EnvironmentDefinitions
  {
    @Override
    protected void addJavacEnvironment( Map<String, String> map )
    {
      int major = getJavaVersion();
      makeJavaVersionDefinitions( map, major );

      //## todo: see super class, need to get these from IJ's compiler settings
      //addAnnotationOptions();
    }

    @Override
    protected void addJpms( Map<String, String> map )
    {
      if( getJavaVersion() < 9 )
      {
        map.put( EnvironmentDefinitions.JPMS_NONE, "" );
      }
      else
      {
        if( _psiFile != null )
        {
          Project project = _psiFile.getProject();
          VirtualFile sourceRoot = ProjectFileIndex.getInstance( project )
            .getSourceRootForFile( FileUtil.toVirtualFile( _psiFile ) );
          if( sourceRoot != null )
          {
            File moduleInfoFile = findModuleInfoFile( sourceRoot, project );
            if( moduleInfoFile != null )
            {
              map.put( EnvironmentDefinitions.JPMS_NAMED, "" );
              return;
            }
          }
        }
        else
        {
          ManModule module = ManProject.getModule( _chameleon.getPsi() );
          if( module != null )
          {
            for( VirtualFile sourceRoot: ManProject.getSourceRoots( module.getIjModule() ) )
            {
              File moduleInfoFile = findModuleInfoFile( sourceRoot, module.getIjProject() );
              if( moduleInfoFile != null )
              {
                map.put( EnvironmentDefinitions.JPMS_NAMED, "" );
                return;
              }
            }
          }
        }
        map.put( EnvironmentDefinitions.JPMS_UNNAMED, "" );
      }
    }

    private int getJavaVersion()
    {
      LanguageLevel languageLevel = _psiFile != null
        ? _psiFile.getLanguageLevel()
        : PsiUtil.getLanguageLevel( _chameleon.getPsi() );
      return languageLevel.toJavaVersion().feature;
    }

    private File findModuleInfoFile( VirtualFile root, Project project )
    {
      File file = new File( urlToOsPath( root.getUrl() ), MODULE_INFO_FILE );
      if( file.isFile() && !isExcluded( LocalFileSystem.getInstance().findFileByIoFile( file ), project ) )
      {
        return file;
      }
      return null;
    }

    private boolean isExcluded( final VirtualFile vFile, final Project project )
    {
      return vFile != null
             && ServiceManager.getService( project, FileIndexFacade.class ).isInSource( vFile )
             && CompilerConfiguration.getInstance( project ).isExcludedFromCompilation( vFile );
    }

    private String urlToOsPath( @NotNull String url )
    {
      return FileUtilRt.toSystemDependentName( urlToPath( url ) );
    }

    private String urlToPath( @Nullable String url )
    {
      if( url == null )
      {
        return null;
      }
      if( url.startsWith( "file://" ) )
      {
        return url.substring( "file://".length() );
      }
      return url;
    }
  }
}

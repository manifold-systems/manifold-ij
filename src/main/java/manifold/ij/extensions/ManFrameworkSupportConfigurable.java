package manifold.ij.extensions;

import com.intellij.facet.ui.libraries.LibraryDownloadInfo;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurableBase;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportProviderBase;
import com.intellij.ide.util.frameworkSupport.FrameworkVersion;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 */
public class ManFrameworkSupportConfigurable extends FrameworkSupportConfigurableBase
{
  public ManFrameworkSupportConfigurable( FrameworkSupportProviderBase frameworkSupportProvider, FrameworkSupportModel model )
  {
    super( frameworkSupportProvider, model );
  }

  @NotNull
  @Override
  public List<? extends FrameworkVersion> getVersions()
  {
    return makeVersions();
  }

  private List<? extends FrameworkVersion> makeVersions()
  {
    return Collections.singletonList( new FrameworkVersion( "RELEASE", "manifold-all", getManifoldLibraries() ) );
  }

  public LibraryInfo[] getManifoldLibraries()
  {
    LibraryInfo manifoldAll = new LibraryInfo( "manifold-all",
                                               new LibraryDownloadInfo(
                                                 "https://repository.sonatype.org/service/local/artifact/maven/content?r=public&g=systems.manifold&a=manifold-all&v=RELEASE",
                                                 "manifold-all" ) );
    return new LibraryInfo[] {manifoldAll};
  }
}

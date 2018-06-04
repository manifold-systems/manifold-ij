package manifold.ij.jps;

import com.intellij.compiler.server.BuildProcessParametersProvider;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import java.io.File;
import java.net.URISyntaxException;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public class ManBuildClasspathProvider extends BuildProcessParametersProvider
{
  @NotNull
  @Override
  public List<String> getClassPath()
  {
    return ((PluginClassLoader)getClass().getClassLoader()).getUrls().stream().map( e -> {
      try
      {
        return new File( e.toURI() ).getAbsolutePath();
      }
      catch( URISyntaxException ex )
      {
        throw new RuntimeException( ex );
      }
    } ).collect( Collectors.toList() );
  }
}

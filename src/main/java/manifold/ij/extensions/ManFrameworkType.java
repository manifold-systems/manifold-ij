package manifold.ij.extensions;

import com.intellij.framework.FrameworkTypeEx;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.jetbrains.annotations.NotNull;

/**
 * Facilitates adding Manifold manifold-all to a module in an existing project.
 * Similarly, ManSupportProvider supports the same functionality when creating a new project.
 */
public class ManFrameworkType extends FrameworkTypeEx
{
  protected ManFrameworkType()
  {
    super( "manifold" );
  }

  @NotNull
  @Override
  public FrameworkSupportInModuleProvider createProvider()
  {
    return new ManFrameworkSupportProvider();
  }

  @NotNull
  @Override
  public String getPresentableName()
  {
    return "Manifold";
  }

  @NotNull
  @Override
  public Icon getIcon()
  {
    return new ImageIcon( getClass().getResource( "/manifold/ij/icons/manifold_20.png" ) );
  }
}

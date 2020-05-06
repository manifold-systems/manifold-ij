package manifold.ij.util;

import com.intellij.AbstractBundle;
import java.util.ResourceBundle;
import org.jetbrains.annotations.PropertyKey;

/**
 */
public class ManBundle
{
  private static final String BUNDLE = "messages.Manifold";

  public static String message( @PropertyKey(resourceBundle = BUNDLE) String key, Object... params )
  {
    return AbstractBundle.message( ResourceBundle.getBundle( BUNDLE ), key, params );
  }
}

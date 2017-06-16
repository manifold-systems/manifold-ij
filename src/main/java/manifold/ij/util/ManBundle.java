package manifold.ij.util;

import com.intellij.CommonBundle;
import java.util.ResourceBundle;
import org.jetbrains.annotations.PropertyKey;

/**
 */
public class ManBundle
{
  private static final String BUNDLE = "messages.Manifold";

  public static String message( @PropertyKey(resourceBundle = BUNDLE) String key, Object... params )
  {
    return CommonBundle.message( ResourceBundle.getBundle( BUNDLE ), key, params );
  }
}

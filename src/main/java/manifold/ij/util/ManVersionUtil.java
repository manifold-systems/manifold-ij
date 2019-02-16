package manifold.ij.util;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import manifold.util.concurrent.LocklessLazyVar;
import org.jetbrains.annotations.NotNull;

public class ManVersionUtil
{
  private static LocklessLazyVar<Boolean> is2018_1_orGreater =
    LocklessLazyVar.make( () -> isOrGreater( 2018, 1 ) );

  private static LocklessLazyVar<Boolean> is2018_2_orGreater =
    LocklessLazyVar.make( () -> isOrGreater( 2018, 2 ) );

  private static LocklessLazyVar<Boolean> is2019_1_orGreater =
    LocklessLazyVar.make( () -> isOrGreater( 2019, 1 ) );

  public static boolean is2018_1_orGreater()
  {
    return is2018_1_orGreater.get();
  }

  public static boolean is2018_2_orGreater()
  {
    return is2018_2_orGreater.get();
  }

  public static boolean is2019_1_orGreater()
  {
    return is2019_1_orGreater.get();
  }

  @NotNull
  private static Boolean isOrGreater( int primary, int secondary )
  {
    String major = ApplicationInfo.getInstance().getMajorVersion();
    try
    {
      int iMajor = Integer.parseInt( major );
      if( iMajor == primary )
      {
        String minor = getMinorVersionMainPart();
        int iMinor = Integer.parseInt( minor );
        return iMinor >= secondary;
      }
      return iMajor > primary;
    }
    catch( Exception e )
    {
      return true;
    }
  }

  private static String getMinorVersionMainPart()
  {
    String minorVersion = ApplicationInfo.getInstance().getMinorVersion();
    return ObjectUtils.notNull( StringUtil.substringBefore( minorVersion, "." ), minorVersion );
  }
}

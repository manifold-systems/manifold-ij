package manifold.ij.util;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import manifold.util.concurrent.LocklessLazyVar;

public class ManVersionUtil
{
  private static LocklessLazyVar<Boolean> is2018_2_orGreater =
    LocklessLazyVar.make( () -> {
      String major = ApplicationInfo.getInstance().getMajorVersion();
      try
      {
        int iMajor = Integer.parseInt( major );
        if( iMajor == 2018 )
        {
          String minor = getMinorVersionMainPart();
          int iMinor = Integer.parseInt( minor );
          return iMinor >= 2;
        }
        return iMajor > 2018;
      }
      catch( Exception e )
      {
        return true;
      }
    } );

  public static boolean is2018_2_orGreater()
  {
    return is2018_2_orGreater.get();
  }

  private static String getMinorVersionMainPart()
  {
    String minorVersion = ApplicationInfo.getInstance().getMinorVersion();
    return ObjectUtils.notNull( StringUtil.substringBefore( minorVersion, "." ), minorVersion );
  }
}

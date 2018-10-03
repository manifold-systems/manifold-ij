package manifold.ij.util;

import com.intellij.openapi.application.ApplicationInfo;
import manifold.util.concurrent.LocklessLazyVar;

public class ManVersionUtil
{
  public static LocklessLazyVar<Boolean> is2018_2_orGreater =
    LocklessLazyVar.make( () -> {
      String major = ApplicationInfo.getInstance().getMajorVersion();
      try
      {
        int iMajor = Integer.parseInt( major );
        if( iMajor >= 2018 )
        {
          String minor = ApplicationInfo.getInstance().getMinorVersionMainPart();
          int iMinor = Integer.parseInt( minor );
          return iMinor >= 2;
        }
      }
      catch( Exception e )
      {
        return true;
      }
      return false;
    } );
}

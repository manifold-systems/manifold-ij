/*
 *
 *  * Copyright (c) 2022 - Manifold Systems LLC
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package manifold.ij.util;

import com.intellij.openapi.application.ApplicationInfo;

public class ManVersionUtil
{
  public static int getMajorVersion()
  {
    return makeInt( ApplicationInfo.getInstance().getMajorVersion() );
  }

  public static int getMinorVersion()
  {
    return makeInt( ApplicationInfo.getInstance().getMinorVersion() );
  }

  public static int getMicroVersion()
  {
    return makeInt( ApplicationInfo.getInstance().getMicroVersion() );
  }

  public static int makeInt( String part )
  {
    return part == null ? 0 : Integer.parseInt( part );
  }

  public static boolean is( int maj, int min, int mic )
  {
    int major = getMajorVersion();
    int minor = getMinorVersion();
    int micro= getMicroVersion();
    return maj == major && min == minor && mic == micro;
  }

  public static boolean isGreaterThan( int maj, int min, int mic )
  {
    int major = getMajorVersion();
    int minor = getMinorVersion();
    int micro= getMicroVersion();
    return major > maj || major == maj && (minor > min || minor == min && micro > mic);
  }

  public static boolean isAtLeast( int maj, int min, int mic )
  {
    return is( maj, min, mic ) || isGreaterThan( maj, min, mic );
  }
}

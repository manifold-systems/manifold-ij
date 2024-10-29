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
import com.intellij.openapi.util.Version;

import java.util.function.Predicate;

public class ManVersionUtil
{
  public static boolean is( int maj, int min, int mic )
  {
    return testVersion( v ->
     maj == v.major && min == v.minor && mic == v.bugfix );
  }

  public static boolean isGreaterThan( int maj, int min, int mic )
  {
    return testVersion( v ->
      v.major > maj || v.major == maj && (v.minor > min || v.minor == min && v.bugfix > mic) );
  }

  public static boolean isAtLeast( int maj, int min, int mic )
  {
    return is( maj, min, mic ) || isGreaterThan( maj, min, mic );
  }

  public static boolean testVersion( Predicate<Version> test )
  {
    String fullVersion = ApplicationInfo.getInstance().getFullVersion();
    Version version = Version.parseVersion( fullVersion );
    if( version == null )
    {
      // probably an Android Studio build, format is like: "Ladybug | 2024.2.1 Patch 1"

      int ibar = fullVersion.indexOf( " | " );
      if( ibar >= 0 )
      {
        fullVersion = fullVersion.substring( ibar + " | ".length() );
        version = Version.parseVersion( fullVersion );
      }

      if( version == null )
      {
        // no idea what intellij variant this could be, search for the beginning of a year?
        int idate = fullVersion.indexOf( "202" );
        if( idate >= 0 )
        {
          fullVersion = fullVersion.substring( idate );
          version = Version.parseVersion( fullVersion );
        }
      }

      if( version == null )
      {
        throw new RuntimeException( "Could not parse version info from: " + fullVersion );
      }
    }
    return test.test( version );
  }
}

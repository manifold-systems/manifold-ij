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
    return test.test( version );
  }
}

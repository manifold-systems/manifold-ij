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

import com.intellij.openapi.application.AccessToken;
import com.intellij.util.SlowOperations;

import java.util.concurrent.Callable;

public class SlowOperationsUtil
{
  public static void allowSlowOperation( String tag, Runnable operation )
  {
    allowSlowOperation( tag, (Callable<Void>)(()-> {operation.run(); return null;}) );
  }

  public static <T> T allowSlowOperation( String tag, Callable<T> operation )
  {
    try( AccessToken ignore = SlowOperations.startSection( tag ) )
    {
      return operation.call();
    }
    catch( Exception e )
    {
      throw new RuntimeException( e );
    }
  }
}

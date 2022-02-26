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

package manifold.ij.extensions;

import manifold.api.type.ITypeManifold;

/**
 */
public class ConflictingTypeManifoldsException extends RuntimeException
{
  public ConflictingTypeManifoldsException( String fqn, ITypeManifold found, ITypeManifold sp )
  {
    super( "The type, " + fqn + ", has conflicting type manifolds:\n" +
      "'" + found.getClass().getName() + "' and '" + sp.getClass().getName() + "'.\n" +
      "Either two or more resource files have the same base name or the project depends on two or more type manifolds that target the same resource type.\n" +
      "If the former, consider renaming one or more of the resource files.\n" +
      "If the latter, you must remove one or more of the type manifold libraries." );
  }
}

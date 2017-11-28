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

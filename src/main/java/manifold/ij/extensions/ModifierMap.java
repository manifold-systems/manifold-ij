package manifold.ij.extensions;

import com.sun.tools.javac.code.Flags;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum ModifierMap
{
  PUBLIC( "public", Modifier.PUBLIC ),
  PROTECTED( "protected", Modifier.PROTECTED ),
  PRIVATE( "private", Modifier.PRIVATE ),
  PACKAGE_LOCAL( "packageLocal", 0 ),
  STATIC( "static", Modifier.STATIC ),
  ABSTRACT( "abstract", Modifier.ABSTRACT ),
  FINAL( "final", Modifier.FINAL ),
  NATIVE( "native", Modifier.NATIVE ),
  SYNCHRONIZED( "synchronized", Modifier.SYNCHRONIZED ),
  STRICTFP( "strictfp", Modifier.STRICT ),
  TRANSIENT( "transient", Modifier.TRANSIENT ),
  VOLATILE( "volatile", Modifier.VOLATILE ),
  DEFAULT( "default", Flags.DEFAULT );

  private final String _name;
  private final long _mod;

  ModifierMap( String name, long mod )
  {
    _name = name;
    _mod = mod;
  }

  public String getName()
  {
    return _name;
  }

  public long getMod()
  {
    return _mod;
  }

  public static ModifierMap byName( String name )
  {
    return Arrays.stream( ModifierMap.values() )
      .filter( e -> e._name.equals( name ) )
      .findFirst()
      .orElseThrow( () -> new IllegalArgumentException( name ) );
  }

  public static ModifierMap byValue( int value )
  {
    return Arrays.stream( ModifierMap.values() )
      .filter( e -> e._mod == value )
      .findFirst()
      .orElseThrow( () -> new IllegalArgumentException( String.valueOf( value ) ) );
  }

  public static List<String> fromBits( long bits )
  {
    List<String> list = new ArrayList<>();
    for( ModifierMap value: values() )
    {
      if( (bits & value.getMod()) != 0 )
      {
        list.add( value.getName() );
      }
    }
    return list;
  }
}

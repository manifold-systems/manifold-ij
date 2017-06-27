package manifold.ij.util;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 */
public class TypeVarToTypeMap
{
  public static final TypeVarToTypeMap EMPTY_MAP = new TypeVarToTypeMap( Collections.emptyMap() );

  private Map<PsiClassType, Pair<PsiType, Boolean>> _map;
  private Set<PsiClassType> _typesInferredFromCovariance;
  private boolean _bStructural;
  private boolean _bReparsing;

  public TypeVarToTypeMap()
  {
    _map = new LinkedHashMap<>( 2 );
    _typesInferredFromCovariance = new HashSet<>( 2 );
  }

  private TypeVarToTypeMap( Map<PsiClassType, Pair<PsiType, Boolean>> emptyMap )
  {
    _map = emptyMap;
    _typesInferredFromCovariance = new HashSet<>( 2 );
  }

  public TypeVarToTypeMap( TypeVarToTypeMap from )
  {
    this();
    _map.putAll( from._map );
    _typesInferredFromCovariance.addAll( from._typesInferredFromCovariance );
    _bStructural = from._bStructural;
  }

  public PsiType get( PsiClassType tvType )
  {
    Pair<PsiType, Boolean> pair = _map.get( tvType );
    return pair != null ? pair.getFirst() : null;
  }
  public Pair<PsiType, Boolean> getPair( PsiClassType tvType )
  {
    return _map.get( tvType );
  }

  public <E> PsiType getByMatcher( E tv, ITypeVarMatcher<E> matcher )
  {
    for( PsiClassType key : _map.keySet() )
    {
      if( matcher.matches( tv, key ) )
      {
        return get( key );
      }
    }
    return null;
  }

  public PsiType getByString( String tv )
  {
    for( PsiClassType key: _map.keySet() )
    {
      if( tv.equals( key.getCanonicalText() ) || tv.equals( key.getCanonicalText() ) )
      {
        return key;
      }
    }
    return null;
  }

  public boolean containsKey( PsiClassType tvType )
  {
    return _map.containsKey( tvType );
  }

  public PsiType put( PsiClassType tvType, PsiType type )
  {
    return put( tvType, type, false );
  }
  public PsiType put( PsiClassType tvType, PsiType type, boolean bReverse )
  {
    PsiType existing = remove( tvType );
    _map.put( tvType, type == null ? null : new Pair<>( type, bReverse ) );
    return existing;
  }

  public void putAll( TypeVarToTypeMap from )
  {
    for( PsiClassType x : from._map.keySet() )
    {
      put( x, from.get( x ) );
    }
  }

  public void putAllAndInferred( TypeVarToTypeMap from )
  {
    for( PsiClassType x : from._map.keySet() )
    {
      put( x, from.get( x ) );
    }
    _typesInferredFromCovariance.addAll( from._typesInferredFromCovariance );
  }

  public boolean isEmpty()
  {
    return _map.isEmpty();
  }

  public int size()
  {
    return _map.size();
  }

  public Set<PsiClassType> keySet()
  {
    return _map.keySet();
  }

  public Set<Map.Entry<PsiClassType,Pair<PsiType, Boolean>>> entrySet()
  {
    return _map.entrySet();
  }

  public PsiType remove( PsiClassType tvType )
  {
    Pair<PsiType, Boolean> pair = _map.remove( tvType );
    return pair != null ? pair.getFirst() : null;
  }

  public Collection<Pair<PsiType, Boolean>> values()
  {
    return _map.values();
  }

  public boolean isStructural() {
    return _bStructural;
  }
  public void setStructural( boolean bStructural ) {
    _bStructural = bStructural;
  }

  public boolean isInferredForCovariance( PsiClassType tv ) {
    return !isStructural() || _typesInferredFromCovariance.contains( tv );
  }
  public void setInferredForCovariance( PsiClassType tv ) {
    _typesInferredFromCovariance.add( tv );
  }

  public interface ITypeVarMatcher<E> {
    boolean matches( E thisOne, PsiClassType thatOne );
  }

  public boolean isReparsing()
  {
    return _bReparsing;
  }
  public void setReparsing( boolean bReparsing )
  {
    _bReparsing = bReparsing;
  }
}

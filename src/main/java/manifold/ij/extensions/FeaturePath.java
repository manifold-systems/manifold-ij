package manifold.ij.extensions;

import com.intellij.psi.PsiClass;

/**
 */
public class FeaturePath
{
  enum FeatureType
  {
    Class, Method, Field
  }
  private PsiClass _root;
  private FeatureType _featureType;
  private int _count;
  private int _index;
  private FeaturePath _parent;
  private FeaturePath _child;

  public static FeaturePath make( FeaturePath parent, FeatureType featureType, int index, int count )
  {
    FeaturePath path = new FeaturePath( parent, featureType, index, count );
    while( path.getParent() != null )
    {
      path = path.getParent();
    }
    return path;
  }

  public FeaturePath( PsiClass root )
  {
    this( null, FeatureType.Class, -1, 0 );
    _root = root;
  }

  public FeaturePath( FeaturePath parent, FeatureType featureType, int index, int count )
  {
    _featureType = featureType;
    _index = index;
    _count = count;
    if( parent != null )
    {
      _parent = new FeaturePath( parent.getParent(), parent.getFeatureType(), parent.getIndex(), parent.getCount() );
      _parent._root = parent._root;
      _parent._child = this;
    }
  }

  public PsiClass getRoot()
  {
    return _root;
  }

  public FeaturePath getParent()
  {
    return _parent;
  }

  public FeaturePath getChild()
  {
    return _child;
  }

  public FeatureType getFeatureType()
  {
    return _featureType;
  }

  public int getIndex()
  {
    return _index;
  }

  public int getCount()
  {
    return _count;
  }
}

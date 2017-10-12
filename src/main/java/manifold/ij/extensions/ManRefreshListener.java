/*
 * Manifold
 */

package manifold.ij.extensions;

import com.intellij.openapi.project.Project;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import manifold.api.fs.IResource;
import manifold.api.host.ITypeLoaderListener;
import manifold.api.host.RefreshKind;
import manifold.api.host.RefreshRequest;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.fs.IjFile;

public class ManRefreshListener
{
  private final CopyOnWriteArrayList<WeakReference<ITypeLoaderListener>> _listeners;
  private final ManProject _manProject;

  public ManRefreshListener( Project project )
  {
    _manProject = ManProject.manProjectFrom( project );
    _listeners = new CopyOnWriteArrayList<>();
  }

  /**
   * Maintains weak refs to listeners. This is primarily so that tests don't
   * accumulate a bunch of listeners over time. Otherwise this is a potential
   * memory gobbler in tests.
   * <p>
   * Note! Callers must manage the lifecycle of the listener, otherwise since this
   * method creates a weak ref, it will be collected when it goes out of scope.
   *
   * @param l Your type loader listener
   */
  public void addTypeLoaderListenerAsWeakRef( ITypeLoaderListener l )
  {
    if( !hasListener( l ) )
    {
      _listeners.add( new WeakReference<>( l ) );
    }
  }

  @SuppressWarnings("unused")
  public void removeTypeLoaderListener( ITypeLoaderListener l )
  {
    for( WeakReference<ITypeLoaderListener> ref : _listeners )
    {
      if( ref.get() == l )
      {
        _listeners.remove( ref );
        break;
      }
    }
  }

  private List<ITypeLoaderListener> getListeners()
  {
    List<ITypeLoaderListener> listeners = new ArrayList<>( _listeners.size() );
    List<WeakReference<ITypeLoaderListener>> obsoleteListeners = null;
    for( WeakReference<ITypeLoaderListener> ref : _listeners )
    {
      ITypeLoaderListener typeLoaderListener = ref.get();
      if( typeLoaderListener != null )
      {
        listeners.add( typeLoaderListener );
      }
      else
      {
        if( obsoleteListeners == null )
        {
          obsoleteListeners = new ArrayList<>();
        }
        obsoleteListeners.add( ref );
      }
    }
    if( obsoleteListeners != null )
    {
      _listeners.removeAll( obsoleteListeners );
    }

    return listeners;
  }

  private boolean hasListener( ITypeLoaderListener l )
  {
    for( WeakReference<ITypeLoaderListener> ref : _listeners )
    {
      if( ref.get() == l )
      {
        return true;
      }
    }
    return false;
  }

  void modified( IResource file )
  {
    notify( file, RefreshKind.MODIFICATION );
  }
  void created( IResource file )
  {
    notify( file, RefreshKind.CREATION );
  }
  void deleted( IResource file )
  {
    notify( file, RefreshKind.DELETION );
  }
  public void nukeFromOrbit()
  {
    for( ITypeLoaderListener listener : getListeners() )
    {
      listener.refreshed();
    }
  }

  private void notify( IResource res, RefreshKind kind  )
  {
    if( !(res instanceof IjFile) )
    {
      return;
    }

    IjFile file = (IjFile)res;
    for( ManModule module: _manProject.getModules() )
    {
      String[] fqns = module.getTypesForFile( file );
      RefreshRequest request = new RefreshRequest( file, fqns, module, module, kind );
      List<ITypeLoaderListener> listeners = getListeners();
      switch( kind )
      {
        case CREATION:
          // for creation the file system needs to be updated first before other listeners
          notifyEarlyListeners( request, listeners );
          notifyNonearlyListeners( request, listeners );
          break;

        case DELETION:
        case MODIFICATION:
          // for deletion the file system needs to be updated after other listeners
          notifyNonearlyListeners( request, listeners );
          notifyEarlyListeners( request, listeners );
          break;
      }
      notifyEarlyListeners( request, listeners );
      notifyNonearlyListeners( request, listeners );
    }
  }

  private void notifyNonearlyListeners( RefreshRequest request, List<ITypeLoaderListener> listeners )
  {
    for( ITypeLoaderListener listener : listeners )
    {
      if( !listener.notifyEarly() )
      {
        listener.refreshedTypes( request );
      }
    }
  }

  private void notifyEarlyListeners( RefreshRequest request, List<ITypeLoaderListener> listeners )
  {
    for( ITypeLoaderListener listener : listeners )
    {
      if( listener.notifyEarly() )
      {
        listener.refreshedTypes( request );
      }
    }
  }
}

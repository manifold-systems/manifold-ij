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

/*
 * Manifold
 */

package manifold.ij.extensions;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import manifold.api.fs.IFile;
import manifold.api.fs.IResource;
import manifold.api.host.IModule;
import manifold.api.host.ITypeSystemListener;
import manifold.api.host.RefreshKind;
import manifold.api.host.RefreshRequest;
import manifold.api.type.ITypeManifold;
import manifold.ext.IExtensionClassProducer;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.fs.IjFile;
import manifold.ij.util.SlowOperationsUtil;

public class ManRefreshListener
{
  private final CopyOnWriteArrayList<WeakReference<ITypeSystemListener>> _listeners;
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
  public void addTypeSystemListenerAsWeakRef( ITypeSystemListener l )
  {
    if( !hasListener( l ) )
    {
      _listeners.add( new WeakReference<>( l ) );
    }
  }

  @SuppressWarnings("unused")
  public void removeTypeSystemListener( ITypeSystemListener l )
  {
    for( WeakReference<ITypeSystemListener> ref : _listeners )
    {
      if( ref.get() == l )
      {
        _listeners.remove( ref );
        break;
      }
    }
  }

  private List<ITypeSystemListener> getListeners()
  {
    List<ITypeSystemListener> listeners = new ArrayList<>( _listeners.size() );
    List<WeakReference<ITypeSystemListener>> obsoleteListeners = null;
    for( WeakReference<ITypeSystemListener> ref : _listeners )
    {
      ITypeSystemListener typeSystemListener = ref.get();
      if( typeSystemListener != null )
      {
        listeners.add( typeSystemListener );
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

  private boolean hasListener( ITypeSystemListener l )
  {
    for( WeakReference<ITypeSystemListener> ref : _listeners )
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
    for( ITypeSystemListener listener : getListeners() )
    {
      listener.refreshed();
    }
  }

  private void notify( IResource res, RefreshKind kind  )
  {
    SlowOperationsUtil.allowSlowOperation( "manifold.generic", () -> _notify( res, kind ) );
  }

  private void _notify( IResource res, RefreshKind kind  )
  {
    if( !(res instanceof IFile) )
    {
      return;
    }

    if( !(((IFile)res).getPhysicalFile() instanceof IjFile) )
    {
      return;
    }

    if( isModuleInfo( res ) )
    {
      _manProject.reset();
      return;
    }

    IFile file = (IFile)res;
    Set<ITypeManifold> tms = ManModule.findTypeManifoldsForFile( _manProject.getNativeProject(), file, null, null );
    if( tms.isEmpty() )
    {
      Module moduleForFile = ModuleUtilCore.findModuleForFile( ((IjFile)file.getPhysicalFile()).getVirtualFile(), _manProject.getNativeProject() );
      if( moduleForFile != null )
      {
        // at least notify the module containing the file (e.g., for extensions classes)
        Set<String> fqnByModule = new LinkedHashSet<>();
        ManModule manModule = ManProject.getModule( moduleForFile );
        if( manModule != null )
        {
          manModule.addFromPath( file, fqnByModule );
          if( !fqnByModule.isEmpty() )
          {
            notify( manModule, file, fqnByModule, kind );
          }
        }
      }
      return;
    }

    Map<IModule, Set<String>> moduleToFqns = new HashMap<>();
    for( ITypeManifold tm: tms )
    {
      Set<String> fqnByModule = moduleToFqns.computeIfAbsent( tm.getModule(), e -> new LinkedHashSet<>() );
      ((ManModule)tm.getModule()).addFromPath( file, fqnByModule );
      fqnByModule.addAll( Arrays.asList( tm.getTypesForFile( file ) ) );
      if( tm instanceof IExtensionClassProducer )
      {
        fqnByModule.addAll( ((IExtensionClassProducer)tm).getExtendedTypesForFile( file ) );
      }
    }
    moduleToFqns.forEach( (module, fqns) -> notify( module, file, fqns, kind ) );
  }

  private boolean isModuleInfo( IResource res )
  {
    return res instanceof IFile && res.getName().equals( "module-info.java" );
  }

  private void notify( IModule module, IFile file, Set<String> result, RefreshKind kind )
  {
    RefreshRequest request = new RefreshRequest( file, result.toArray( new String[0] ), module, kind );
    List<ITypeSystemListener> listeners = getListeners();
    switch( kind )
    {
      case CREATION:
      case MODIFICATION:
        // for creation the file system needs to be updated *before* other listeners
        notifyEarlyListeners( request, listeners );
        notifyNonearlyListeners( request, listeners );
        break;

      case DELETION:
        // for deletion the file system needs to be updated *after* other listeners
        notifyNonearlyListeners( request, listeners );
        notifyEarlyListeners( request, listeners );
        break;
    }
  }

  private void notifyNonearlyListeners( RefreshRequest request, List<ITypeSystemListener> listeners )
  {
    for( ITypeSystemListener listener : listeners )
    {
      if( !listener.notifyEarly() )
      {
        listener.refreshedTypes( request );
      }
    }
  }

  private void notifyEarlyListeners( RefreshRequest request, List<ITypeSystemListener> listeners )
  {
    for( ITypeSystemListener listener : listeners )
    {
      if( listener.notifyEarly() )
      {
        listener.refreshedTypes( request );
      }
    }
  }
}

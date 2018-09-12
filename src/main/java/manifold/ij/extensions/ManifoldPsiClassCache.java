/*
 * Manifold
 */

package manifold.ij.extensions;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import javax.tools.DiagnosticCollector;
import manifold.api.fs.IFile;
import manifold.api.host.AbstractTypeSystemListener;
import manifold.api.host.RefreshRequest;
import manifold.api.type.ITypeManifold;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.util.cache.FqnCache;
import manifold.util.cache.FqnCacheNode;
import org.jetbrains.annotations.NotNull;


import static manifold.api.type.ContributorKind.*;

/**
 * Caches PsiClasses corresponding with type manifold produced classes
 * as well as PsiClasses extended with manifold extensions (ManifoldPsiClasses and ManifoldExtendedPsiClasses).
 */
public class ManifoldPsiClassCache extends AbstractTypeSystemListener
{
  private static final ManifoldPsiClassCache INSTANCE = new ManifoldPsiClassCache();
  public static ManifoldPsiClassCache instance()
  {
    return INSTANCE;
  }

  private Set<Project> _addedListeners = ContainerUtil.newConcurrentSet();
  private ThreadLocal<Set<String>> _shortcircuit = new ThreadLocal<>();
  private final ConcurrentHashMap<String, PsiClass> _fqnToPsi = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<ManModule, FqnCache<PsiClass>> _moduleToFqnCache = new ConcurrentHashMap<>();

  public synchronized PsiClass getPsiClass( ManModule module, String fqn )
  {
    return getPsiClass( null, module, fqn );
  }
  public synchronized PsiClass getPsiClass( GlobalSearchScope scope, ManModule module, String fqn )
  {
    if( isShortCircuit( fqn ) )
    {
      return null;
    }
    return getPsiClass_NoShortCircuit( scope, module, fqn );
  }
  private PsiClass getPsiClass_NoShortCircuit( GlobalSearchScope scope, ManModule module, String fqn )
  {
    if( !isValidFqn( fqn ) )
    {
      return null;
    }

    addShortCircuit( fqn );
    try
    {
      listenToChanges( module.getProject() );

      // Find cached type...

      FqnCache<PsiClass> map = _moduleToFqnCache.computeIfAbsent( module, k -> new FqnCache<>() );
      FqnCacheNode<PsiClass> node = map.getNode( fqn );
      if( node != null )
      {
        PsiClass psiFacadeClass = node.getUserData();
        if( psiFacadeClass == null || psiFacadeClass.isValid() )
        {
          return psiFacadeClass;
        }
      }


      // Create new module-specific type...

      if( node == null )
      {
        try
        {
          node = createPrimaryType( module, fqn, map );
        }
        catch( ConflictingTypeManifoldsException e )
        {
          return PsiErrorClassUtil.create( module.getIjProject(), e );
        }
      }

      return node == null ? null : node.getUserData();
    }
    finally
    {
      removeShortCircuit( fqn );
    }
  }

  private boolean isValidFqn( String fqn )
  {
    // IJ tries to resolve some pretty strange looking names...

    if( fqn.isEmpty() )
    {
      return false;
    }

    if( !Character.isJavaIdentifierStart( fqn.charAt( 0 ) ) )
    {
      return false;
    }

    for( int i = 1; i < fqn.length(); i++ )
    {
      char c = fqn.charAt( i );
      if( !Character.isJavaIdentifierPart( c ) &&
          c != '<' && c != '>' && c != '.' )
      {
        return false;
      }
    }

    return !fqn.contains( ".<" );
  }

  private void addShortCircuit( String fqn )
  {
    initShortCircuit();
    _shortcircuit.get().add( fqn );
  }

  private void initShortCircuit()
  {
    if( _shortcircuit.get() == null )
    {
      _shortcircuit.set( new HashSet<>() );
    }
  }

  private void removeShortCircuit( String fqn )
  {
    initShortCircuit();
    _shortcircuit.get().remove( fqn );
  }

  private boolean isShortCircuit( String fqn )
  {
    initShortCircuit();
    return _shortcircuit.get().contains( fqn );
  }

  /**
   * Create a type corresponding with a Primary or Partial type manifold, as opposed to a Supplemental one.
   */
  private FqnCacheNode<PsiClass> createPrimaryType( ManModule module, String fqn, FqnCache<PsiClass> map )
  {
    Set<ITypeManifold> sps = module.findTypeManifoldsFor( fqn );
    ITypeManifold found = null;
    if( !sps.isEmpty() )
    {
      String result = "";
      DiagnosticCollector issues = new DiagnosticCollector();
      for( ITypeManifold sp : sps )
      {
        if( sp.getContributorKind() == Primary ||
            sp.getContributorKind() == Partial )
        {
          if( found != null && (found.getContributorKind() == Primary || sp.getContributorKind() == Primary) )
          {
            throw new ConflictingTypeManifoldsException( fqn, found, sp );
          }
          found = sp;
          result = sp.contribute( null, fqn, result, issues );
        }
      }

      if( found != null )
      {
        PsiClass delegate = createPsiClass( module, fqn, result );
        delegate = maybeGetInnerClass( fqn, delegate );
        List<IFile> files = found.findFilesForType( fqn );
        ManifoldPsiClass psiFacadeClass = new ManifoldPsiClass( delegate, files, fqn, issues );
        map.add( fqn, psiFacadeClass );
        for( IFile file : files )
        {
          _fqnToPsi.put( file.getPath().getPathString(), psiFacadeClass );
        }
      }
    }

    if( found == null )
    {
      // cache the miss
      map.add( fqn );
    }
    return map.getNode( fqn );
  }

  private PsiClass maybeGetInnerClass( String fqn, PsiClass delegate )
  {
    String delegateFqn = delegate.getQualifiedName();
    if( delegateFqn.length() < fqn.length() )
    {
      String rest = fqn.substring( delegateFqn.length() + 1 );
      for( StringTokenizer tokenizer = new StringTokenizer( rest, "." ); tokenizer.hasMoreTokens(); )
      {
        String innerName = tokenizer.nextToken();
        PsiClass innerClass = delegate.findInnerClassByName( innerName, false );
        if( innerClass == null )
        {
          break;
        }
        delegate = innerClass;
      }
    }
    return delegate;
  }


  private boolean isUnitTestingMode()
  {
    Application app = ApplicationManager.getApplication();
    return app != null && app.isUnitTestMode();
  }

  private void listenToChanges( ManProject project )
  {
    Project ijProject = project.getNativeProject();
    if( _addedListeners.contains( ijProject ) )
    {
      return;
    }

    _addedListeners.add( ijProject );
    project.getFileModificationManager().getManRefresher().addTypeLoaderListenerAsWeakRef( this );
    PsiManager.getInstance( ijProject ).addPsiTreeChangeListener( new PsiTreeChangeHandler() );
  }

  private PsiClass createPsiClass( ManModule module, String fqn, String source )
  {
    PsiManager manager = PsiManagerImpl.getInstance( module.getIjProject() );
    final PsiJavaFile aFile = createDummyJavaFile( fqn, manager, source );
    final PsiClass[] classes = aFile.getClasses();
    if( classes.length == 0 )
    {
      return PsiErrorClassUtil.create( module.getIjProject(), new RuntimeException( "Invalid class: " + fqn ) );
    }
    return classes[0];
  }

  private PsiJavaFile createDummyJavaFile( String type, PsiManager manager, final String text )
  {
    final FileType fileType = JavaFileType.INSTANCE;
    return (PsiJavaFile)PsiFileFactory.getInstance( manager.getProject() ).createFileFromText( type + '.' + JavaFileType.INSTANCE.getDefaultExtension(), fileType, text );
  }

  @Override
  public synchronized void refreshedTypes( RefreshRequest request )
  {
    final ManModule module = (ManModule)request.module;
    FqnCache<PsiClass> map = _moduleToFqnCache.get( module );

    if( map != null )
    {
      for( String type : request.types )
      {
        //removeDependentTypes( type, map, module );
        map.remove( type );
      }
    }
    if( request.file != null )
    {
      String pathString = request.file.getPath().getPathString();
      PsiClass removedFacade = _fqnToPsi.remove( pathString );
      if( removedFacade != null )
      {
        ((PsiModificationTrackerImpl)removedFacade.getManager().getModificationTracker()).incCounter();
        if( map != null )
        {
          map.remove( removedFacade.getQualifiedName() );
        }
      }
    }
  }

  @Override
  public void refreshed()
  {
    _fqnToPsi.clear();
    _moduleToFqnCache.clear();
  }

  private class PsiTreeChangeHandler extends PsiTreeChangeAdapter
  {
    /**
     * Handle when callers of FileManagerImpl.invalidateAllPsi() send this event.
     * Basically we need to listen to property change events here that are not file
     * oriented, but instead are PSI-oriented, and that pretty much rip out all psi
     * from underneath this cache.
     */
    @Override
    public void propertyChanged( @NotNull PsiTreeChangeEvent event )
    {
      PsiFile file = event.getFile();
      String propertyName = event.getPropertyName();
      if( file == null &&
          (propertyName == null
           || propertyName.equals( PsiTreeChangeEvent.PROP_FILE_TYPES )
           || propertyName.equals( PsiTreeChangeEvent.PROP_ROOTS )) )
      {
        refreshed();
      }
    }
  }
}

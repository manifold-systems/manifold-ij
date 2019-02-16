/*
 * Manifold
 */

package manifold.ij.extensions;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
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
import java.util.concurrent.ConcurrentHashMap;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import manifold.api.fs.IFile;
import manifold.api.host.AbstractTypeSystemListener;
import manifold.api.host.RefreshRequest;
import manifold.api.type.ITypeManifold;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.util.cache.FqnCache;
import manifold.util.cache.FqnCacheNode;
import manifold.util.cache.IllegalTypeNameException;
import org.jetbrains.annotations.NotNull;


import static manifold.api.type.ContributorKind.*;

/**
 * Caches instances of {@link ManifoldPsiClass} corresponding with type manifold
 * {@link manifold.api.type.ContributorKind#Primary} class names.
 * <p/>
 * Note the cache maintained here assumes there is only <i>one</i> type per qualified
 * name.  Even though there could be multiple modules having the same resource files
 * resulting in the same Manifold type names, as a build step resource files are typically
 * copied to the same output directory, thereby resulting in a single logical resource file.
 * And even when separate output directories are specified per module, at runtime, if using
 * a single module, the classpath dictates the file to appear first in the path wins.
 * Regarding a multiple module runtime (Java 9+), package splitting is forbidden, so
 * again only a single resource file can exist for a given name.  This is why the cache
 * here is a simple name-to-class mapping.  As a result it is much faster!
 */
public class ManifoldPsiClassCache extends AbstractTypeSystemListener
{
  private final ManProject _project;
  private Set<Project> _addedListeners = ContainerUtil.newConcurrentSet();
  private ThreadLocal<Set<String>> _shortCircuit = new ThreadLocal<>();
  private ConcurrentHashMap<String, PsiClass> _filePathToPsi = new ConcurrentHashMap<>();
  private final FqnCache<ManifoldPsiClass> _fqnPsiCache;

  public ManifoldPsiClassCache( ManProject project )
  {
    _project = project;
    _fqnPsiCache = new FqnCache<>();
  }

  public ManProject getProject()
  {
    return _project;
  }

  public static PsiClass getPsiClass( ManModule module, String fqn )
  {
    return module.getProject().getPsiClassCache()._getPsiClass( module, fqn );
  }
  /**
   * This method is for internal use, call {@link com.intellij.psi.JavaPsiFacade#findClass(String, GlobalSearchScope)}
   * instead, which will delegate to this method if appropriate.
   */
  synchronized PsiClass _getPsiClass( ManModule module, String fqn )
  {
    if( isShortCircuit( fqn ) )
    {
      return null;
    }
    return getPsiClass_NoShortCircuit( module, fqn );
  }
  private PsiClass getPsiClass_NoShortCircuit( ManModule module, String fqn )
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

      FqnCacheNode<ManifoldPsiClass> node = _fqnPsiCache.getNode( fqn );
      if( node != null )
      {
        ManifoldPsiClass psiFacadeClass = node.getUserData();
        if( psiFacadeClass == null )
        {
          return null;
        }

        if( psiFacadeClass.isValid() )
        {
          Module targetModule = psiFacadeClass.getModule();
          GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesScope( module.getIjModule() );
          if( scope.isSearchInModuleContent( targetModule ) )
          {
            return psiFacadeClass;
          }
        }

        return null;
      }


      // Create new module-specific type...
      try
      {
        node = createPrimaryType( module, fqn );
      }
      catch( IllegalTypeNameException itne )
      {
        // Handle the case where IntelliJ tries to resolve something untype-like
        node = null;
      }
      catch( ConflictingTypeManifoldsException e )
      {
        return PsiErrorClassUtil.create( module.getIjProject(), e );
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
    // IJ tries to resolve some pretty strange looking names,
    // make sure the name format is: <identifier>{'.'<identifier>}

    if( fqn.isEmpty() )
    {
      return false;
    }

    boolean bDot = true;
    for( int i = 0; i < fqn.length(); i++ )
    {
      char c = fqn.charAt( i );

      if( bDot )
      {
        if( Character.isJavaIdentifierStart( c ) )
        {
          bDot = false;
          continue;
        }
      }
      else
      {
        if( Character.isJavaIdentifierPart( c ) )
        {
          continue;
        }

        if( c == '.' && (i != fqn.length()-1) )
        {
          bDot = true;
          continue;
        }
      }
      return false;
    }
    return true;
  }

  private void addShortCircuit( String fqn )
  {
    initShortCircuit();
    _shortCircuit.get().add( fqn );
  }

  private void initShortCircuit()
  {
    if( _shortCircuit.get() == null )
    {
      _shortCircuit.set( new HashSet<>() );
    }
  }

  private void removeShortCircuit( String fqn )
  {
    initShortCircuit();
    _shortCircuit.get().remove( fqn );
  }

  private boolean isShortCircuit( String fqn )
  {
    initShortCircuit();
    return _shortCircuit.get().contains( fqn );
  }

  /**
   * Create a type corresponding with a Primary or Partial type manifold, as opposed to a Supplemental one.
   */
  private FqnCacheNode<ManifoldPsiClass> createPrimaryType( ManModule module, String fqn )
  {
    Set<ITypeManifold> tms = module.findTypeManifoldsFor( fqn, tm -> tm.getContributorKind() == Primary ||
                                                                     tm.getContributorKind() == Partial );
    ITypeManifold found = null;
    if( !tms.isEmpty() )
    {
      String result = "";
      DiagnosticCollector<JavaFileObject> issues = new DiagnosticCollector<>();
      for( ITypeManifold tm : tms )
      {
        if( found != null && (found.getContributorKind() == Primary || tm.getContributorKind() == Primary) )
        {
          throw new ConflictingTypeManifoldsException( fqn, found, tm );
        }
        found = tm;
        result = tm.contribute( null, fqn, result, issues );
      }

      ManModule actualModule = (ManModule)found.getModule();
      PsiClass delegate = createPsiClass( actualModule, fqn, result );
      cacheAll( delegate, actualModule, found, issues );
    }

    if( found == null )
    {
      // cache the miss
      _fqnPsiCache.add( fqn );
    }
    return _fqnPsiCache.getNode( fqn );
  }

  private void cacheAll( PsiClass delegate, ManModule actualModule, ITypeManifold tm, DiagnosticCollector<JavaFileObject> issues )
  {
    String fqn = delegate.getQualifiedName();
    List<IFile> files = tm.findFilesForType( fqn );
    ManifoldPsiClass psiFacadeClass = new ManifoldPsiClass( delegate, actualModule, files, fqn, issues );
    _fqnPsiCache.add( fqn, psiFacadeClass );
    for( IFile file : files )
    {
      _filePathToPsi.put( file.getPath().getPathString(), psiFacadeClass );
    }
    for( PsiClass inner: delegate.getInnerClasses() )
    {
      cacheAll( inner, actualModule, tm, issues );
    }
  }

  private void listenToChanges( ManProject project )
  {
    Project ijProject = project.getNativeProject();
    if( _addedListeners.contains( ijProject ) )
    {
      return;
    }

    _addedListeners.add( ijProject );
    project.getFileModificationManager().getManRefresher().addTypeSystemListenerAsWeakRef( this );
    PsiManager.getInstance( ijProject ).addPsiTreeChangeListener( new PsiTreeChangeHandler() );
  }

  private PsiClass createPsiClass( ManModule module, String fqn, String source )
  {
    //System.out.println( "NEW: " + fqn + "  MODULE: " + module.getName() );
    ////new Exception().fillInStackTrace().printStackTrace();

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
    for( String type : request.types )
    {
      //removeDependentTypes( type, map, module );
      _fqnPsiCache.remove( type );
    }
    if( request.file != null )
    {
      String pathString = request.file.getPath().getPathString();
      PsiClass removedFacade = _filePathToPsi.remove( pathString );
      if( removedFacade != null )
      {
        ((PsiModificationTrackerImpl)removedFacade.getManager().getModificationTracker()).incCounter();
        _fqnPsiCache.remove( removedFacade.getQualifiedName() );
      }
    }
  }

  @Override
  public void refreshed()
  {
    _filePathToPsi = new ConcurrentHashMap<>();
    _fqnPsiCache.clear();
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

/*
 * Manifold
 */

package manifold.ij.extensions;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import manifold.api.fs.IFile;
import manifold.api.fs.IFileFragment;
import manifold.api.host.AbstractTypeSystemListener;
import manifold.api.host.Dependency;
import manifold.api.host.RefreshRequest;
import manifold.api.type.ITypeManifold;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.internal.javac.FragmentProcessor;
import manifold.api.util.cache.FqnCache;
import manifold.api.util.cache.FqnCacheNode;
import manifold.api.util.cache.IllegalTypeNameException;
import manifold.util.concurrent.ConcurrentHashSet;
import manifold.util.concurrent.ConcurrentWeakHashMap;
import org.jetbrains.annotations.NotNull;


import static manifold.api.type.ContributorKind.*;

/**
 * Caches instances of {@link ManifoldPsiClass} corresponding with type manifold
 * {@link manifold.api.type.ContributorKind#Primary} class names.
 */
public class ManifoldPsiClassCache extends AbstractTypeSystemListener
{
  private final ManProject _project;
  private Set<Project> _addedListeners;
  private final ThreadLocal<Set<String>> _shortCircuit;
  private ConcurrentHashMap<String, PsiClass> _filePathToPsi;
  private final Map<ManModule, FqnCache<ManifoldPsiClass>> _fqnPsiCachePerModule;

  public ManifoldPsiClassCache( ManProject project )
  {
    _project = project;
    _addedListeners = new ConcurrentHashSet<>();
    _shortCircuit = ThreadLocal.withInitial( () -> new ConcurrentHashSet<>() );
    _filePathToPsi = new ConcurrentHashMap<>();
    _fqnPsiCachePerModule = new ConcurrentWeakHashMap<>();
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
    PsiClass psiClass = getPsiClass_NoShortCircuit( module, fqn );
    return removeTypeIfStale( psiClass );
  }

  private PsiClass removeTypeIfStale( PsiClass psiClass )
  {
    if( psiClass instanceof ManifoldPsiClass )
    {
      List<IFile> files = ((ManifoldPsiClass)psiClass).getFiles();
      for( IFile file: files )
      {
        if( isStaleFileFragment( file ) )
        {
          getProject().getFileModificationManager().getManRefresher().deleted( file );
          return null;
        }
      }
    }
    return psiClass;
  }

  private boolean isStaleFileFragment( IFile file )
  {
    if( file instanceof IFileFragment )
    {
      SmartPsiElementPointer<?> container = (SmartPsiElementPointer<?>)((IFileFragment)file).getContainer();
      if( container == null )
      {
        return true;
      }
      else
      {
        PsiElement elem = container.getElement();
        if( !(elem instanceof PsiFileFragment) )
        {
          return true;
        }
        FragmentProcessor.Fragment fragment = FragmentProcessor.instance().parseFragment( 0, elem.getText(), ((PsiFileFragment)elem).getStyle() );
        return fragment == null || !fragment.getName().equals( file.getBaseName() ) || !fragment.getExt().equalsIgnoreCase( file.getExtension() );
      }
    }
    return false;
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
      ManifoldPsiClass cached = getCached( module, module, fqn );
      if( cached != null )
      {
        return cached;
      }

      // Create new module-specific type...
      FqnCacheNode<ManifoldPsiClass> node;
      try
      {
        node = createPrimaryType( module, fqn );
      }
      catch( IllegalTypeNameException itne )
      {
        // Handle the case where IntelliJ tries to resolve something untype-like
        node = null;
      }
      catch( Exception e )
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

  private ManifoldPsiClass getCached( ManModule module, ManModule start, String fqn )
  {
    FqnCache<ManifoldPsiClass> fqnPsiCache = _fqnPsiCachePerModule.get( module );
    FqnCacheNode<ManifoldPsiClass> node = fqnPsiCache == null ? null : fqnPsiCache.getNode( fqn );
    if( node != null )
    {
      ManifoldPsiClass psiFacadeClass = node.getUserData();
      if( psiFacadeClass != null && psiFacadeClass.isValid() )
      {
        Module targetModule = psiFacadeClass.getModule();
        GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesScope( module.getIjModule() );
        if( scope.isSearchInModuleContent( targetModule ) )
        {
          return psiFacadeClass;
        }
      }
    }

    for( Dependency d: module.getDependencies() )
    {
      if( module == start || d.isExported() )
      {
        ManifoldPsiClass cached = getCached( (ManModule) d.getModule(), start, fqn );
        if( cached != null )
        {
          return cached;
        }
      }
    }

    return null;
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
    _shortCircuit.get().add( fqn );
  }

  private void removeShortCircuit( String fqn )
  {
    _shortCircuit.get().remove( fqn );
  }

  private boolean isShortCircuit( String fqn )
  {
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
      String topLevelFqn = null;
      for( ITypeManifold tm : tms )
      {
        // MUST start with top-level class, otherwise the classes enclosing an inner class will have null userData
        // cached with their names, preventing the them from ever loading.  So when we cache a class name we always get
        // its outermost enclosing class and cache that and the entire nest of classes it contains, top-down.  See the
        // cacheAll() call following this for loop.
        topLevelFqn = topLevelFqn == null ? findTopLevelFqn( tm, fqn ) : topLevelFqn;

        if( found != null && (found.getContributorKind() == Primary || tm.getContributorKind() == Primary) )
        {
          throw new ConflictingTypeManifoldsException( fqn, found, tm );
        }
        found = tm;
        result = tm.contribute( null, topLevelFqn, false, result, issues );
      }

      ManModule actualModule = (ManModule)found.getModule();
      PsiClass delegate = createPsiClass( actualModule, topLevelFqn, result );
      cacheAll( delegate, actualModule, found, issues );
    }

    FqnCache<ManifoldPsiClass> fqnPsiCache = _fqnPsiCachePerModule.computeIfAbsent( module, key -> new FqnCache<>() );
    if( found == null )
    {
      // cache the miss
      fqnPsiCache.add( fqn );
    }
    return fqnPsiCache.getNode( fqn );
  }

  public static String findTopLevelFqn( ITypeManifold tm, String fqn )
  {
    if( tm.isTopLevelType( fqn ) )
    {
      return fqn;
    }
    int lastDot = fqn.lastIndexOf( '.' );
    if( lastDot <= 0 )
    {
      throw new IllegalStateException();
    }
    fqn = fqn.substring( 0, lastDot );
    return findTopLevelFqn( tm, fqn );
  }

  private void cacheAll( PsiClass delegate, ManModule actualModule, ITypeManifold tm, DiagnosticCollector<JavaFileObject> issues )
  {
    String fqn = delegate.getQualifiedName();
    List<IFile> files = tm.findFilesForType( fqn );
    ManifoldPsiClass psiFacadeClass = new ManifoldPsiClass( delegate, actualModule, files, fqn, issues );
    FqnCache<ManifoldPsiClass> fqnPsiCache = _fqnPsiCachePerModule.computeIfAbsent( actualModule, key -> new FqnCache<>() );
    fqnPsiCache.add( fqn, psiFacadeClass );
    if( psiFacadeClass.getContainingClass() == null ) // associate only top-level class with file
    {
      for( IFile file : files )
      {
        _filePathToPsi.put( file.getPath().getPathString(), psiFacadeClass );
      }
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
    PsiManager.getInstance( ijProject ).addPsiTreeChangeListener( new PsiTreeChangeHandler(), ijProject );
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
    // note, calling version of PsiFileFactory#createFileFromText that takes file content of any size, which is vital
    // for large generated files e.g., graphql files like github.graphql
    return (PsiJavaFile)PsiFileFactory.getInstance( manager.getProject() )
      .createFileFromText( type + '.' + JavaFileType.INSTANCE.getDefaultExtension(), JavaLanguage.INSTANCE, text, false, true, true );
  }

  @Override
  public synchronized void refreshedTypes( RefreshRequest request )
  {
    if( !(request.module instanceof ManModule) )
    {
      throw new IllegalStateException();
    }

    ManModule module = (ManModule)request.module;
    FqnCache<ManifoldPsiClass> fqnPsiCache = _fqnPsiCachePerModule.computeIfAbsent( module, key -> new FqnCache<>() );
    for( String type : request.types )
    {
      //removeDependentTypes( type, map, module );
      fqnPsiCache.remove( type );
    }
    if( request.file != null )
    {
      String pathString = request.file.getPath().getPathString();
      if( _filePathToPsi.containsKey( pathString ) )
      {
        PsiClass facade = _filePathToPsi.get( pathString );
        if( removeFromCache( module, module, facade ) )
        {
          _filePathToPsi.remove( pathString );
          ApplicationManager.getApplication().invokeLater( () ->
            ApplicationManager.getApplication().runWriteAction( () ->
              ((PsiModificationTrackerImpl)facade.getManager().getModificationTracker()).incCounter() ) );
        }
      }
    }
  }

  public boolean removeFromCache( ManModule module, ManModule start, PsiClass removedFacade )
  {
    FqnCache<ManifoldPsiClass> fqnPsiCache =
      _fqnPsiCachePerModule.computeIfAbsent( module, key -> new FqnCache<>() );

    if( fqnPsiCache.remove( removedFacade.getQualifiedName() ) )
    {
      return true;
    }

    for( Dependency d: module.getDependencies() )
    {
      if( module == start || d.isExported() )
      {
        if( removeFromCache( (ManModule) d.getModule(), start, removedFacade ) )
        {
          return true;
        }
      }
    }

    return false;
  }

  @Override
  public void refreshed()
  {
    _filePathToPsi = new ConcurrentHashMap<>();
    _fqnPsiCachePerModule.clear();
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

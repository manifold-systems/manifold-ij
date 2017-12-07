/*
 * Manifold
 */

package manifold.ij.extensions;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
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
import manifold.api.type.ITypeProcessor;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.util.cache.FqnCache;
import manifold.util.cache.FqnCacheNode;
import org.jetbrains.annotations.Nullable;


import static manifold.api.type.ITypeManifold.ProducerKind.*;

/**
 * Caches PsiClasses corresponding with type manifold produced classes
 * as well as PsiClasses extended with manifold extensions (ManifoldPsiClasses and ManifoldExtendedPsiClasses).
 */
public class ManifoldPsiClassCache extends AbstractTypeSystemListener
{
  private static final ManifoldPsiClassCache INSTANCE = new ManifoldPsiClassCache();
  private Set<Project> _addedListeners = ContainerUtil.newConcurrentSet();
  private ThreadLocal<Set<String>> _shortcircuit = new ThreadLocal<>();


  public static ManifoldPsiClassCache instance()
  {
    return INSTANCE;
  }

  private final ConcurrentHashMap<String, PsiClass> _psi2Class = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<ManModule, FqnCache<PsiClass>> _type2Class = new ConcurrentHashMap<>();

  public PsiClass getPsiClass( GlobalSearchScope scope, ManModule module, String fqn )
  {
    if( isShortCircuit( fqn ) )
    {
      return null;
    }

    addShortCircuit( fqn );
    try
    {
      listenToChanges( module.getProject() );

      // Find cached type...

      FqnCache<PsiClass> map = _type2Class.computeIfAbsent( module, k -> new FqnCache<>() );
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


      // Add extensions (and create module-specific type if necessary) ...

      PsiClass psiClass = node == null ? null : node.getUserData();
      psiClass = addExtensions( scope, module, fqn, psiClass );

      return psiClass;
    }
    finally
    {
      removeShortCircuit( fqn );
    }
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
        if( found != null && (found.getProducerKind() == Primary || sp.getProducerKind() == Primary) )
        {
          throw new ConflictingTypeManifoldsException( fqn, found, sp );
        }
        if( sp.getProducerKind() == Primary ||
            sp.getProducerKind() == Partial )
        {
          found = sp;
          result = sp.produce( fqn, result, issues );
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
          _psi2Class.put( file.getPath().getPathString(), psiFacadeClass );
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

  @Nullable
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

  private PsiClass addExtensions( GlobalSearchScope scope, ManModule module, String fqn, PsiClass psiClass )
  {
    if( isExtended( module, fqn ) )
    {
      // Find the class excluding our ManTypeFinder to avoid circularity
      psiClass = psiClass != null ? psiClass : JavaPsiFacade.getInstance( module.getIjProject() ).findClass( fqn, scope );
      if( psiClass != null )
      {
        psiClass = new ManifoldExtendedPsiClass( module.getIjModule(), psiClass );
        psiClass.putUserData( ModuleUtil.KEY_MODULE, module.getIjModule() );
        FqnCache<PsiClass> map = _type2Class.computeIfAbsent( module, k -> new FqnCache<>() );
        map.add( fqn, psiClass );
      }
    }
    return psiClass;
  }

//  private List<IFile> findFilesForType( ManModule module, String fqn )
//  {
//    Set<ITypeManifold> sps = module.findTypeManifoldsFor( fqn );
//    Set<IFile> files = new LinkedHashSet<>();
//    for( ITypeManifold sp : sps )
//    {
//      files.addAll( sp.findFilesForType( fqn ) );
//    }
//    return new ArrayList<>( files );
//  }
//
//  private PsiClass makeCopy( PsiClass psiClass )
//  {
//    final PsiJavaFile copy = (PsiJavaFile)psiClass.getContainingFile().copy();
//    for( PsiClass cls : copy.getClasses() )
//    {
//      if( cls.getQualifiedName().equals( psiClass.getQualifiedName() ) )
//      {
//        return cls;
//      }
//    }
//    throw new IllegalStateException( "Copy class failed for: " + psiClass.getQualifiedName() );
//  }

  private boolean isExtended( ManModule module, String fqn )
  {
    Set<ITypeManifold> sps = module.findTypeManifoldsFor( fqn );
    for( ITypeManifold sp : sps )
    {
      if( sp.getProducerKind() == Supplemental )
      {
        return true;
      }
    }
    return false;
  }

  private void listenToChanges( ManProject project )
  {
    if( _addedListeners.contains( project.getNativeProject() ) )
    {
      return;
    }

    _addedListeners.add( project.getNativeProject() );
    project.getFileModificationManager().getManRefresher().addTypeLoaderListenerAsWeakRef( this );
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
  public void refreshedTypes( RefreshRequest request )
  {
    final ManModule module = (ManModule)request.module;
    FqnCache<PsiClass> map = _type2Class.get( module );

    if( map != null )
    {
      // System.out.println( "Refreshing: " + request.toString() );
      for( ITypeManifold sp : request.module.getTypeManifolds() )
      {
        if( sp instanceof ITypeProcessor )
        {
          for( String fqn : sp.getTypesForFile( request.file ) )
          {
            map.remove( fqn );
            //System.out.println( "REMOVED: " + fqn );
            for( IFile f : sp.findFilesForType( fqn ) )
            {
              String pathString = f.getPath().getPathString();
              _psi2Class.remove( pathString );
              //System.out.println( "REMOVED PSI: " + pathString );
            }
          }
          //System.out.println();
        }
      }

      for( String type : request.types )
      {
        //removeDependentTypes( type, map, module );
        map.remove( type );
      }
    }
    if( request.file != null )
    {
      String pathString = request.file.getPath().getPathString();
      PsiClass removedFacade = _psi2Class.remove( pathString );
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

//  private void removeDependentTypes( String type, FqnCache<PsiClass> map, ManModule module )
//  {
//    GlobalSearchScope scope = GlobalSearchScope.moduleScope( module.getIjModule() );
//    PsiClass psiClass = JavaPsiFacade.getInstance( module.getProject().getNativeProject() ).findClass( type, scope );
//    if( psiClass == null )
//    {
//      return;
//    }
//
//    Query<PsiReference> search = ReferencesSearch.search( psiClass, scope );
//    for( PsiReference ref : search.findAll() )
//    {
//      PsiElement element = ref.getElement();
//      if( element != null )
//      {
//        PsiClass referringClass = ManifoldPsiClassAnnotator.getContainingClass( element );
//        if( referringClass != null )
//        {
//          map.remove( referringClass.getQualifiedName() );
//        }
//      }
//    }
//  }

  @Override
  public void refreshed()
  {
    _psi2Class.clear();
    _type2Class.clear();
  }
}

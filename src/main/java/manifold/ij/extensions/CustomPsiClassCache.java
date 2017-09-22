/*
 * Manifold
 */

package manifold.ij.extensions;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import manifold.api.fs.IFile;
import manifold.api.host.AbstractTypeSystemListener;
import manifold.api.host.RefreshRequest;
import manifold.api.type.ITypeManifold;
import manifold.api.type.ITypeProcessor;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.util.cache.FqnCache;
import manifold.util.cache.FqnCacheNode;
import manifold.util.concurrent.ConcurrentHashSet;


import static manifold.api.type.ITypeManifold.ProducerKind.*;

public class CustomPsiClassCache extends AbstractTypeSystemListener
{
  private static final CustomPsiClassCache INSTANCE = new CustomPsiClassCache();
  private boolean _addedListener;
  private Set<String> _shortCircuitCache = new ConcurrentHashSet<>();


  public static CustomPsiClassCache instance()
  {
    return INSTANCE;
  }

  private final ConcurrentHashMap<String, PsiClass> _psi2Class = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<ManModule, FqnCache<PsiClass>> _type2Class = new ConcurrentHashMap<>();

  public PsiClass getPsiClass( ManModule module, String fqn )
  {
    if( _shortCircuitCache.contains( fqn ) )
    {
      return null;
    }

    PsiClass psiClass = _getPsiClass( module, fqn );
    psiClass = addExtensions( module, fqn, psiClass );
    return psiClass;
  }

  private PsiClass _getPsiClass( ManModule module, String fqn )
  {
    listenToChanges( module.getProject() );

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

    if( node == null )
    {
      Set<ITypeManifold> sps = module.findTypeManifoldsFor( fqn );
      ITypeManifold found = null;
      if( !sps.isEmpty() )
      {
        String result = "";
        for( ITypeManifold sp : sps )
        {
          if( sp.getProducerKind() == Primary ||
              sp.getProducerKind() == Partial )
          {
            if( found != null && (found.getProducerKind() == Primary || sp.getProducerKind() == Primary) )
            {
              //## todo: how better to handle this?
              throw new UnsupportedOperationException( "The type, " + fqn + ", has conflicting source producers: '" +
                                                       found.getClass().getName() + "' and '" + sp.getClass().getName() + "'" );
            }
            found = sp;
            result = sp.produce( fqn, result, null );
          }
        }

        if( found != null )
        {
          PsiClass delegate = createPsiClass( module, fqn, result );
          List<IFile> files = found.findFilesForType( fqn );
          JavaFacadePsiClass psiFacadeClass = new JavaFacadePsiClass( delegate, files, fqn );
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

      node = map.getNode( fqn );
    }

    return node == null ? null : node.getUserData();
  }

  private PsiClass addExtensions( ManModule module, String fqn, PsiClass psiClass )
  {
    JavaFacadePsiClass facade = psiClass instanceof JavaFacadePsiClass ? (JavaFacadePsiClass)psiClass : null;

    if( psiClass == null )
    {
      _shortCircuitCache.add( fqn );
      try
      {
        if( isExtended( module, fqn ) )
        {
          // Find the class excluding our ManTypeFinder to avoid circularity
          psiClass = JavaPsiFacade.getInstance( module.getIjProject() ).findClass( fqn, GlobalSearchScope.allScope( module.getIjProject() ) );
          if( psiClass instanceof ClsClassImpl )
          {
            psiClass = ((ClsClassImpl)psiClass).getSourceMirrorClass();
          }
          if( psiClass != null )
          {
            // Copy the the psi file because we need separate versions per module --
            // we add the module to the psiClass' user-data via the ModuleUtil.KEY_MODULE (see below).
            // In turn ManAugmentProvider get the module from the user-data so it can augment the class
            // in the proper context i.e., we don't actually add the extension methods here, instead
            // they are added later via the ManAugmentProvider.
            psiClass = makeCopy( psiClass );
            //insertInterfaces( module, psiClass );
            FqnCache<PsiClass> map = _type2Class.computeIfAbsent( module, k -> new FqnCache<>() );
            map.add( fqn, psiClass );
          }
        }
      }
      finally
      {
        _shortCircuitCache.remove( fqn );
      }
    }
    else if( facade != null )
    {
      psiClass = ((JavaFacadePsiClass)psiClass).getDelegate();
    }

    if( psiClass != null )
    {
      psiClass.putUserData( ModuleUtil.KEY_MODULE, module.getIjModule() );
    }

    psiClass = facade != null ? facade : psiClass;
    return psiClass;
  }

//  private void insertInterfaces( ManModule module, PsiClass psiClass )
//  {
//    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory( psiClass.getProject() );
//
//    for( String extensionFqn: findAllExtensions( module, psiClass.getQualifiedName() ) )
//    {
//      JavaPsiFacade facade = JavaPsiFacade.getInstance( psiClass.getProject() );
//      PsiClass psiExtensionClass = facade.findClass( extensionFqn, GlobalSearchScope.moduleWithDependenciesScope( module.getIjModule() ) );
//      if( psiExtensionClass != null )
//      {
//        for( PsiClass psiInterface: psiExtensionClass.getInterfaces() )
//        {
//          PsiClassType interfaceType = PsiTypesUtil.getClassType( psiInterface );
//
//          if( psiClass.isInterface() )
//          {
//            psiClass.getExtendsList().add( elementFactory.createReferenceElementByType( interfaceType ) );
//          }
//          else
//          {
//            psiClass.getImplementsList().add( elementFactory.createReferenceElementByType( interfaceType ) );
//          }
//        }
//      }
//    }
//  }
//
//  private Set<String> findAllExtensions( ManModule module, String fqn )
//  {
//    Set<String> allExtensions = new HashSet<>();
//    Set<ITypeManifold> sps = module.findTypeManifoldsFor( fqn );
//    if( !sps.isEmpty() )
//    {
//      for( ITypeManifold sp : sps )
//      {
//        if( sp.getProducerKind() == Supplemental )
//        {
//          allExtensions.addAll( findAllExtensions( sp, module, fqn ) );
//        }
//      }
//    }
//    return allExtensions;
//  }
//
//  private Set<String> findAllExtensions( ITypeManifold sp, ManModule module, String fqn )
//  {
//    Set<String> fqns = new LinkedHashSet<>();
//
//    PathCache pathCache = ModulePathCache.instance().get( module );
//    for( IFile file : sp.findFilesForType( fqn ) )
//    {
//      Set<String> extensions = pathCache.getFqnForFile( file );
//      for( String f : extensions )
//      {
//        if( f != null )
//        {
//          fqns.add( f );
//        }
//      }
//    }
//    return fqns;
//  }

  private PsiClass makeCopy( PsiClass psiClass )
  {
    final PsiJavaFile copy = (PsiJavaFile)psiClass.getContainingFile().copy();
    for( PsiClass cls : copy.getClasses() )
    {
      if( cls.getQualifiedName().equals( psiClass.getQualifiedName() ) )
      {
        return cls;
      }
    }
    throw new IllegalStateException( "Copy class failed for: " + psiClass.getQualifiedName() );
  }

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
    if( _addedListener )
    {
      return;
    }

    _addedListener = true;
    project.getFileModificationManager().getManRefresher().addTypeLoaderListenerAsWeakRef( this );
  }

  private PsiClass createPsiClass( ManModule module, String fqn, String source )
  {
    PsiManager manager = PsiManagerImpl.getInstance( module.getIjProject() );
    final PsiJavaFile aFile = createDummyJavaFile( fqn, manager, source );
    final PsiClass[] classes = aFile.getClasses();
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
      //System.out.println( "Refreshing: " + request.toString() );
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
          System.out.println();
        }
      }

      for( String type : request.types )
      {
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
        map.remove( removedFacade.getQualifiedName() );
      }
    }
  }

  @Override
  public void refreshed()
  {
    _psi2Class.clear();
    _type2Class.clear();
  }
}

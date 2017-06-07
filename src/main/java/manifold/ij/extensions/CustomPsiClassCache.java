/*
 * Manifold
 */

package manifold.ij.extensions;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import manifold.api.fs.IFile;
import manifold.api.host.AbstractTypeSystemListener;
import manifold.api.host.RefreshRequest;
import manifold.api.sourceprod.ISourceProducer;
import manifold.api.sourceprod.ITypeProcessor;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.util.cache.FqnCache;
import manifold.util.cache.FqnCacheNode;

public class CustomPsiClassCache extends AbstractTypeSystemListener
{
  private static final CustomPsiClassCache INSTANCE = new CustomPsiClassCache();
  private boolean _addedListener;


  public static CustomPsiClassCache instance()
  {
    return INSTANCE;
  }

  private final ConcurrentHashMap<String, JavaFacadePsiClass> _psi2Class = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<ManModule, FqnCache<JavaFacadePsiClass>> _type2Class = new ConcurrentHashMap<>();

  public JavaFacadePsiClass getPsiClass( ManModule module, String fqn )
  {
    listenToChanges( module.getProject() );

    FqnCache<JavaFacadePsiClass> map = _type2Class.computeIfAbsent( module, k -> new FqnCache<>() );

    FqnCacheNode<JavaFacadePsiClass> node = map.getNode( fqn );
    if( node != null )
    {
      JavaFacadePsiClass psiFacadeClass = node.getUserData();
      if( psiFacadeClass == null || psiFacadeClass.isValid() )
      {
        return psiFacadeClass;
      }
    }

    if( node == null )
    {
      ISourceProducer sp = module.findSourceProducerFor( fqn );
      if( sp != null && !(sp instanceof ITypeProcessor) )
      {
        PsiClass delegate = createPsiClass( module, fqn, sp );
        List<IFile> files = sp.findFilesForType( fqn );
        JavaFacadePsiClass psiFacadeClass = new JavaFacadePsiClass( delegate, files, fqn );
        map.add( fqn, psiFacadeClass );
        for( IFile file: files )
        {
          _psi2Class.put( file.getPath().getPathString(), psiFacadeClass );
        }
      }
      else
      {
        // cache the miss
        map.add( fqn );
      }
      node = map.getNode( fqn );
    }

    return node.getUserData();
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

  private PsiClass createPsiClass( ManModule module, String fqn, ISourceProducer sp )
  {
    PsiManager manager = PsiManagerImpl.getInstance( module.getIjProject() );
    String source = sp.produce( fqn, null ); //## todo: maybe provide a DiagnosticListener for error handling?
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
    FqnCache<JavaFacadePsiClass> map = _type2Class.get( request.module );

    if( map != null )
    {
      System.out.println( "Refreshing: " + request.toString() );
      for( ISourceProducer sp: request.module.getSourceProducers() )
      {
        if( sp instanceof ITypeProcessor )
        {
          for( String fqn: sp.getTypesForFile( request.file ) )
          {
            map.remove( fqn );
            System.out.println( "REMOVED: " + fqn );
            for( IFile f: sp.findFilesForType( fqn ) )
            {
              String pathString = f.getPath().getPathString();
              System.out.println( _psi2Class.remove( pathString ) );
              System.out.println( "REMOVED PSI: " + pathString );
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
      JavaFacadePsiClass removedFacade = _psi2Class.remove( pathString );
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

package manifold.ij.extensions;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.light.LightClass;
import com.intellij.psi.impl.source.ClassInnerStuffCache;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import java.util.Arrays;
import java.util.List;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import org.jetbrains.annotations.NotNull;


/**
 * Wraps a PsiClass that has Manifold extension methods.
 * <p/>
 * Note there are two ways we can bolt on the extensions.
 * <ol>
 * <li>A PsiAugmentProvider implementation</li>
 * <li>Directly add them here in this class</li>
 * </ol>
 * We'd rather directly add them here and avoid the indirection involved
 * with the PsiAugmentProvider. However, there is at least one bug with
 * this approach involving generics:
 * <pre>
 *   &#64;Extension
 *   public class MyListExt {
 *     public static &lt;E,F extends E> List<E> append(@This List&lt;E> thiz, F thing ) {
 *       ...
 *     }
 *   }
 * </pre>
 * The <code>F</code> type variable is not correctly handled by IJ in a type
 * hierarchy e.g., ArrayList -> List:
 * <pre>
 *   ArrayList&lt;String> arrList = new ArrayList&lt;>();
 *   arrList.append( "hi" ); // error: not in bounds
 * </pre>
 * Whereas this example with just List work fine:
 * <pre>
 *   List&lt;String> list = new ArrayList&lt;>();
 *   list.append( "hi" ); // ok
 * </pre>
 * Therefore, we have to go with option #1 using our ManAugmentProvider. Somehow
 * IJ has worked out the problem with this approach.
 */
public class ManifoldExtendedPsiClass extends LightClass implements PsiExtensibleClass
{
  private ManModule _module;
  private final ClassInnerStuffCache _extensionsCache = new ClassInnerStuffCache( this );

//  private final LocklessLazyVar<List<PsiClass>> _ifaceExt = LocklessLazyVar.make( this::findExtInterfaces );

//## uncomment to support option #2 above:
//  private final LocklessLazyVar<PsiMethod[]> _allMethods = LocklessLazyVar.make( () -> {
//    List<PsiMethod> all = new ArrayList<>( Arrays.asList( ExtendedClass.super.getMethods() ) );
//    ManAugmentProvider augmentor = new ManAugmentProvider();
//    List<PsiMethod> extMethods = augmentor.getAugments( _module, getDelegate(), PsiMethod.class );
//    all.addAll( extMethods );
//    return all.toArray( new PsiMethod[all.size()] );
//  } );

  public ManifoldExtendedPsiClass( Module module, PsiClass delegate )
  {
    super( delegate, delegate.getLanguage() );
    _module = ManProject.getModule( module );
  }


  @Override
  public boolean isPhysical()
  {
    // Returns the delegate's value here so that this type can work with ManShortNamesCache.
    // See DefaultClassNavigationContributor#processElementsWithName(), and its call to isPhysical()
    return getDelegate().isPhysical();
  }

  @Override
  public ItemPresentation getPresentation()
  {
    // Necessary for ManShortNamesCache
    return ItemPresentationProviders.getItemPresentation( getDelegate() );
  }


  @Override
  public PsiMethod[] getMethods()
  {
    // Use the augment cache to get extensions indirectly via ManAugmentProvider
    return _extensionsCache.getMethods();

    //## uncomment this and disable the ManAugmentProvider in plugin.xml to support option #2
    //return _allMethods.get();
  }

//## this just doesn't work; for some reason IJ does not call this for interfaces, so we resort to filtering errors related to extension interfaces (see ManHighlightInfoFilter)
//  @Override
//  public PsiClass[] getInterfaces()
//  {
//    PsiClass[] declaredIfaces = super.getInterfaces();
//    List<PsiClass> all = new ArrayList<>( Arrays.asList( declaredIfaces ) );
//    all.addAll( _ifaceExt.get() );
//    return all.toArray( new PsiClass[all.size()] );
//  }

  @NotNull
  @Override
  public List<PsiField> getOwnFields()
  {
    return Arrays.asList( super.getFields() );
  }

  @NotNull
  @Override
  public List<PsiMethod> getOwnMethods()
  {
    return Arrays.asList( super.getMethods() );
  }

  @NotNull
  @Override
  public List<PsiClass> getOwnInnerClasses()
  {
    return Arrays.asList( super.getInnerClasses() );
  }

  @Override
  @NotNull
  public PsiMethod[] findMethodsByName( String name, boolean checkBases )
  {
    return _extensionsCache.findMethodsByName( name, checkBases );
  }

  @Override
  public boolean isValid()
  {
    return true;
  }

  //## see comment on getInterfaces() above
//
//    private List<PsiClass> findExtInterfaces()
//    {
//      Set<PsiClass> interfaces = new HashSet<>();
//      for( String extensionFqn: findAllExtensions() )
//      {
//        JavaPsiFacade facade = JavaPsiFacade.getInstance( getProject() );
//        PsiClass psiExtensionClass = facade.findClass( extensionFqn, GlobalSearchScope.moduleWithDependenciesScope( _module.getIjModule() ) );
//        if( psiExtensionClass != null )
//        {
//          Collections.addAll( interfaces, psiExtensionClass.getInterfaces() );
//        }
//      }
//      return new ArrayList<>( interfaces );
//    }
//
//    private Set<String> findAllExtensions()
//    {
//      Set<String> allExtensions = new HashSet<>();
//      Set<ITypeManifold> sps = _module.findTypeManifoldsFor( getQualifiedName() );
//      if( !sps.isEmpty() )
//      {
//        for( ITypeManifold sp : sps )
//        {
//          if( sp.getProducerKind() == Supplemental )
//          {
//            allExtensions.addAll( findAllExtensions( sp ) );
//          }
//        }
//      }
//      return allExtensions;
//    }
//
//    private Set<String> findAllExtensions( ITypeManifold sp )
//    {
//      Set<String> fqns = new LinkedHashSet<>();
//
//      PathCache pathCache = ModulePathCache.instance().get( _module );
//      for( IFile file : sp.findFilesForType( getQualifiedName() ) )
//      {
//        Set<String> extensions = pathCache.getFqnForFile( file );
//        for( String f : extensions )
//        {
//          if( f != null )
//          {
//            fqns.add( f );
//          }
//        }
//      }
//      return fqns;
//    }

}

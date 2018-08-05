package manifold.ij.extensions;

import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import manifold.api.type.ITypeManifold;
import manifold.api.type.TypeName;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import org.jetbrains.annotations.NotNull;


import static manifold.api.type.ContributorKind.Supplemental;


/**
 */
public class ManTypeFinder extends PsiElementFinder
{
  private final Project _project;


  public ManTypeFinder( Project project )
  {
    _project = project;
  }

  @Override
  public PsiClass[] findClasses( String fqn, GlobalSearchScope globalSearchScope )
  {
    //System.out.println( "PsiClass[] findClasses() : " + fqn + " : " + globalSearchScope );

    if( DumbService.getInstance( globalSearchScope.getProject() ).isDumb() )
    {
      // skip processing during index rebuild
      return PsiClass.EMPTY_ARRAY;
    }

    Set<PsiClass> psiClasses = new LinkedHashSet<>();
    List<ManModule> modules = findModules( globalSearchScope );
    for( ManModule m : modules )
    {
      PsiClass psiClass = ManifoldPsiClassCache.instance().getPsiClass( globalSearchScope, m, fqn );
      if( psiClass != null )
      {
        psiClasses.add( psiClass );
      }
    }
    return psiClasses.toArray( new PsiClass[0] );
  }

  @Override
  public PsiClass findClass( String fqn, GlobalSearchScope globalSearchScope )
  {
    //System.out.println( "findClass() : " + fqn + " : " + globalSearchScope );

    if( DumbService.getInstance( globalSearchScope.getProject() ).isDumb() )
    {
      // skip processing during index rebuild
      return null;
    }

    List<ManModule> modules = findModules( globalSearchScope );

    for( ManModule m : modules )
    {
      PsiClass psiClass = ManifoldPsiClassCache.instance().getPsiClass( globalSearchScope, m, fqn );
      if( psiClass != null )
      {
        return psiClass;
      }
    }
    return null;
  }

  public static List<ManModule> findModules( GlobalSearchScope scope )
  {
    if( scope instanceof ModuleWithDependenciesScope )
    {
      Module module = ((ModuleWithDependenciesScope)scope).getModule();
      return Collections.singletonList( ManProject.getModule( module ) );
    }

    ManProject manProject = ManProject.manProjectFrom( scope.getProject() );
    List<ManModule> modules = new ArrayList<>( manProject.getModules() );
    modules.removeIf( module -> !scope.isSearchInModuleContent( module.getIjModule() ) );
    return modules;
  }

  @Override
  public PsiClass[] getClasses( PsiPackage psiPackage, GlobalSearchScope scope )
  {
    //System.out.println( "getClasses() : " + psiPackage + " : " + scope );

    if( DumbService.getInstance( scope.getProject() ).isDumb() )
    {
      // skip processing during index rebuild
      return PsiClass.EMPTY_ARRAY;
    }

    List<ManModule> modules = findModules( scope );

    String parentPackage = psiPackage.getQualifiedName();
    Set<PsiClass> children = new HashSet<>();
    for( ManModule mm : modules )
    {
      for( ITypeManifold sp : mm.getTypeManifolds() )
      {
        if( sp.getContributorKind() == Supplemental )
        {
          continue;
        }

        Collection<TypeName> typeNames = sp.getTypeNames( parentPackage );
        for( TypeName child : typeNames )
        {
          if( child.kind == TypeName.Kind.TYPE )
          {
            PsiClass psiClass = ManifoldPsiClassCache.instance().getPsiClass( scope, mm, child.name );
            if( psiClass != null )
            {
              children.add( psiClass );
            }
          }
        }
      }
    }
    if( !children.isEmpty() )
    {
      return children.toArray( new PsiClass[children.size()] );
    }
    return super.getClasses( psiPackage, scope );
  }

  @Override
  public PsiClass[] getClasses( String className, PsiPackage psiPackage, GlobalSearchScope scope )
  {
    return super.getClasses( className, psiPackage, scope );
  }

  @Override
  public PsiPackage[] getSubPackages( PsiPackage psiPackage, GlobalSearchScope scope )
  {
    //System.out.println( "getSubPackages() : " + psiPackage + " : " + scope );

    List<ManModule> modules = findModules( scope );
    if( modules.isEmpty() )
    {
      return PsiPackage.EMPTY_ARRAY;
    }

    String parentPackage = psiPackage.getQualifiedName();
    Set<PsiPackage> children = new HashSet<>();
    PsiManager manager = PsiManagerImpl.getInstance( scope.getProject() );
    for( ManModule mm : modules )
    {
      for( ITypeManifold sp : mm.getTypeManifolds() )
      {
        if( sp.getContributorKind() == Supplemental )
        {
          continue;
        }

        Collection<TypeName> typeNames = sp.getTypeNames( parentPackage );
        for( TypeName child : typeNames )
        {
          if( child.kind == TypeName.Kind.NAMESPACE )
          {
            children.add( new NonDirectoryPackage( manager, parentPackage + '.' + child.name ) );
          }
        }
      }
    }
    if( !children.isEmpty() )
    {
      return children.toArray( new PsiPackage[children.size()] );
    }
    return PsiPackage.EMPTY_ARRAY;
  }

  @Override
  public boolean processPackageDirectories(@NotNull PsiPackage psiPackage,
                                           @NotNull final GlobalSearchScope scope,
                                           @NotNull final Processor<PsiDirectory> consumer,
                                           boolean includeLibrarySources) {
    //System.out.println( "processDirectories() : " + psiPackage + " : " + scope );

    final PsiManager psiManager = PsiManager.getInstance( _project );
    return PackageIndex.getInstance( _project )
      .getDirsByPackageName(psiPackage.getQualifiedName(), includeLibrarySources)
      .forEach(new ReadActionProcessor<VirtualFile>() {
        @Override
        public boolean processInReadAction(final VirtualFile dir) {
          if (!scope.contains(dir)) return true;
          PsiDirectory psiDir = psiManager.findDirectory(dir);
          return psiDir == null || consumer.process(psiDir);
        }
      });
  }

  @Override
  public PsiPackage findPackage( String fqn )
  {
    //System.out.println( "findPackage() : " + fqn );

    List<ManModule> modules = ManProject.manProjectFrom( _project ).getModules();
    PsiManager manager = PsiManagerImpl.getInstance( _project );
    for( ManModule mm : modules )
    {
      for( ITypeManifold sp : mm.getTypeManifolds() )
      {
        if( sp.getContributorKind() != Supplemental && sp.isPackage( fqn ) )
        {
          return new NonDirectoryPackage( manager, fqn );
        }
      }
    }
    return null;
  }
}

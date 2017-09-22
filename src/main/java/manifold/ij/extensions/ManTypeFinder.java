package manifold.ij.extensions;

import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import manifold.api.type.ITypeManifold;
import manifold.api.type.ITypeProcessor;
import manifold.api.type.TypeName;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;

/**
 */
public class ManTypeFinder extends PsiElementFinder
{
  @Override
  public PsiClass findClass( String fqn, GlobalSearchScope globalSearchScope )
  {
    List<ManModule> modules = findModules( globalSearchScope );
    for( ManModule m : modules )
    {
      PsiClass psiClass = CustomPsiClassCache.instance().getPsiClass( m, fqn );
      if( psiClass != null )
      {
        return psiClass;
      }
    }
    return null;
  }

  public static List<ManModule> findModules( GlobalSearchScope globalSearchScope )
  {
    List<ManModule> modules;
    if( globalSearchScope instanceof ModuleWithDependenciesScope )
    {
      ManModule manModule = ManProject.getModule( ((ModuleWithDependenciesScope)globalSearchScope).getModule() );
      modules = Collections.singletonList( manModule );
    }
    else
    {
      ManProject manProject = ManProject.manProjectFrom( globalSearchScope.getProject() );
      modules = manProject.getModules();
    }
    return modules;
  }

  @Override
  public PsiClass[] getClasses( PsiPackage psiPackage, GlobalSearchScope scope )
  {
    List<ManModule> modules = findModules( scope );
    String parentPackage = psiPackage.getQualifiedName();
    Set<PsiClass> children = new HashSet<>();
    for( ManModule mm : modules )
    {
      for( ITypeManifold sp : mm.getTypeManifolds() )
      {
        if( sp instanceof ITypeProcessor )
        {
          continue;
        }

        Collection<TypeName> typeNames = sp.getTypeNames( parentPackage );
        for( TypeName child : typeNames )
        {
          if( child.kind == TypeName.Kind.TYPE )
          {
            PsiClass psiClass = CustomPsiClassCache.instance().getPsiClass( mm, child.name );
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
    List<ManModule> modules = findModules( scope );
    String parentPackage = psiPackage.getQualifiedName();
    Set<PsiPackage> children = new HashSet<>();
    PsiManager manager = PsiManagerImpl.getInstance( scope.getProject() );
    for( ManModule mm : modules )
    {
      for( ITypeManifold sp : mm.getTypeManifolds() )
      {
        if( sp instanceof ITypeProcessor )
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
    return super.getSubPackages( psiPackage, scope );
  }

  @Override
  public boolean processPackageDirectories( PsiPackage psiPackage, GlobalSearchScope scope, Processor<PsiDirectory> consumer )
  {
    return super.processPackageDirectories( psiPackage, scope, consumer );
  }

  @Override
  public PsiPackage findPackage( String fqn )
  {
    //!! For some reason this method has no Scope argument, so we include all projects in the search :/
    for( ManProject manProject : ManProject.getAllProjects() )
    {
      List<ManModule> modules = manProject.getModules();
      PsiManager manager = PsiManagerImpl.getInstance( manProject.getNativeProject() );
      for( ManModule mm : modules )
      {
        for( ITypeManifold sp : mm.getTypeManifolds() )
        {
          if( sp instanceof ITypeProcessor )
          {
            continue;
          }

          if( sp.isPackage( fqn ) )
          {
            return new NonDirectoryPackage( manager, fqn );
          }
        }
      }
    }
    return null;
  }

  @Override
  public PsiClass[] findClasses( String s, GlobalSearchScope globalSearchScope )
  {
    PsiClass gsType = findClass( s, globalSearchScope );
    if( gsType != null )
    {
      return new PsiClass[]{gsType};
    }
    return new PsiClass[0];
  }
}

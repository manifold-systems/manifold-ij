package manifold.ij.extensions;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import manifold.api.host.Dependency;
import manifold.api.type.ITypeManifold;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 */
public class ManShortNamesCache extends PsiShortNamesCache
{
  private final PsiManagerEx _psiManager;

  public ManShortNamesCache( PsiManagerEx manager )
  {
    _psiManager = manager;
  }

  @NotNull
  @Override
  public PsiClass[] getClassesByName( @NotNull @NonNls String name, @NotNull GlobalSearchScope scope )
  {
    Set<PsiClass> psiClasses = new HashSet<>();
    for( ManModule module: ManTypeFinder.findModules( scope ) )
    {
      findPsiClasses( name, scope, psiClasses, module );
    }
    return psiClasses.toArray( new PsiClass[psiClasses.size()] );
  }

  private void findPsiClasses( @NotNull @NonNls String name, @NotNull GlobalSearchScope scope, Set<PsiClass> psiClasses, ManModule module )
  {
    findPsiClasses( name, scope, psiClasses, module, module );
  }
  private void findPsiClasses( @NotNull @NonNls String name, @NotNull GlobalSearchScope scope, Set<PsiClass> psiClasses, ManModule start, ManModule module )
  {
    for( ITypeManifold tm: module.getTypeManifolds() )
    {
      if( tm.getProducerKind() == ITypeManifold.ProducerKind.Supplemental )
      {
        continue;
      }
      for( String fqn: tm.getAllTypeNames() )
      {
        String simpleName = ClassUtil.extractClassName( fqn );
        if( simpleName.equals( name ) )
        {
          PsiClass psiClass = ManifoldPsiClassCache.instance().getPsiClass( scope, module, fqn );
          psiClasses.add( psiClass );
        }
      }
    }
    for( Dependency d : module.getDependencies() )
    {
      if( module == start || d.isExported() )
      {
        findPsiClasses( name, scope, psiClasses, start, (ManModule)d.getModule() );
      }
    }
  }

  @NotNull
  @Override
  public String[] getAllClassNames()
  {
    HashSet<String> names = new HashSet<>();
    getAllClassNames( names );
    return names.toArray( new String[names.size()] );
  }

  @Override
  public void getAllClassNames( @NotNull HashSet<String> dest )
  {
    final ManProject manProject = ManProject.manProjectFrom( _psiManager.getProject() );
    for( ManModule module: manProject.findRootModules() )
    {
      findClassFqns( dest, module );
    }
  }

  private void findClassFqns( @NotNull HashSet<String> dest, ManModule module )
  {
    findClassFqns( dest, module, module );
  }
  private void findClassFqns( @NotNull HashSet<String> dest, ManModule start, ManModule module )
  {
    for( ITypeManifold tm: module.getTypeManifolds() )
    {
      if( tm.getProducerKind() == ITypeManifold.ProducerKind.Supplemental )
      {
        continue;
      }
      dest.addAll( tm.getAllTypeNames().stream().map( ClassUtil::extractClassName ).collect( Collectors.toList() ) );
    }
    for( Dependency d : module.getDependencies() )
    {
      if( module == start || d.isExported() )
      {
        findClassFqns( dest, start, (ManModule)d.getModule() );
      }
    }
  }

  @NotNull
  @Override
  public PsiMethod[] getMethodsByName( @NonNls @NotNull String name, @NotNull GlobalSearchScope scope )
  {
    return new PsiMethod[0];
  }

  @NotNull
  @Override
  public PsiMethod[] getMethodsByNameIfNotMoreThan( @NonNls @NotNull String name, @NotNull GlobalSearchScope scope, int maxCount )
  {
    return new PsiMethod[0];
  }

  @NotNull
  @Override
  public PsiField[] getFieldsByNameIfNotMoreThan( @NonNls @NotNull String name, @NotNull GlobalSearchScope scope, int maxCount )
  {
    return new PsiField[0];
  }

  @Override
  public boolean processMethodsWithName( @NonNls @NotNull String name, @NotNull GlobalSearchScope scope, @NotNull Processor<PsiMethod> processor )
  {
    return true;
  }

  @NotNull
  @Override
  public String[] getAllMethodNames()
  {
    return new String[0];
  }

  @Override
  public void getAllMethodNames( @NotNull HashSet<String> set )
  {

  }

  @NotNull
  @Override
  public PsiField[] getFieldsByName( @NotNull @NonNls String name, @NotNull GlobalSearchScope scope )
  {
    return new PsiField[0];
  }

  @NotNull
  @Override
  public String[] getAllFieldNames()
  {
    return new String[0];
  }

  @Override
  public void getAllFieldNames( @NotNull HashSet<String> set )
  {

  }
}

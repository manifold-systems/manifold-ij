/*
 *
 *  * Copyright (c) 2022 - Manifold Systems LLC
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package manifold.ij.extensions;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import manifold.api.host.Dependency;
import manifold.api.type.ContributorKind;
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

//  public ManShortNamesCache( PsiManagerEx manager )
//  {
//    _psiManager = manager;
//    _project = _psiManager.getProject();
//  }

  public ManShortNamesCache( Project project )
  {
    _psiManager = (PsiManagerEx)PsiManager.getInstance( project );
  }

  @NotNull
  @Override
  public PsiClass[] getClassesByName( @NotNull @NonNls String name, @NotNull GlobalSearchScope scope )
  {
    if( !ManProject.isManifoldInUse( _psiManager.getProject() ) )
    {
      return PsiClass.EMPTY_ARRAY;
    }

    Set<PsiClass> psiClasses = new HashSet<>();
    for( ManModule module: ManTypeFinder.findModules( scope ) )
    {
      findPsiClasses( name, scope, psiClasses, module );
    }
    return psiClasses.toArray( new PsiClass[0] );
  }

  private void findPsiClasses( @NotNull @NonNls String name, @NotNull GlobalSearchScope scope, Set<PsiClass> psiClasses, ManModule module )
  {
    findPsiClasses( name, scope, psiClasses, module, module, new HashSet<>() );
  }
  private void findPsiClasses( @NotNull @NonNls String name, @NotNull GlobalSearchScope scope, Set<PsiClass> psiClasses,
                               ManModule start, ManModule module, HashSet<ManModule> visited )
  {
    if( visited.contains( module ) )
    {
      return;
    }
    visited.add( module );

    for( ITypeManifold tm: module.getTypeManifolds() )
    {
      for( String fqn: tm.getAllTypeNames() )
      {
        String simpleName = ClassUtil.extractClassName( fqn );
        if( simpleName.equals( name ) )
        {
          PsiClass psiClass = ManifoldPsiClassCache.getPsiClass( module, fqn );
          if( psiClass == null )
          {
            return;
          }
          psiClasses.add( psiClass );
        }
      }
    }
    for( Dependency d: module.getDependencies() )
    {
      if( module == start || d.isExported() )
      {
        findPsiClasses( name, scope, psiClasses, start, (ManModule)d.getModule(), visited );
      }
    }
  }

  @NotNull
  @Override
  public String[] getAllClassNames()
  {
    if( !ManProject.isManifoldInUse( _psiManager.getProject() ) )
    {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    HashSet<String> names = new HashSet<>();
    getAllClassNames( names );
    return names.toArray( new String[0] );
  }

  private void getAllClassNames( HashSet<String> dest )
  {
    final ManProject manProject = ManProject.manProjectFrom( _psiManager.getProject() );
    for( ManModule module: manProject.getRootModules() )
    {
      findClassFqns( dest, module );
    }
  }

  private void findClassFqns( @NotNull HashSet<String> dest, ManModule module )
  {
    findClassFqns( dest, module, module, new HashSet<>() );
  }
  private void findClassFqns( @NotNull HashSet<String> dest, ManModule start, ManModule module, HashSet<ManModule> visited )
  {
    if( visited.contains( module ) )
    {
      return;
    }
    visited.add( module );

    for( ITypeManifold tm: module.getTypeManifolds() )
    {
      if( tm.getContributorKind() == ContributorKind.Supplemental )
      {
        continue;
      }
      dest.addAll( tm.getAllTypeNames().stream().map( ClassUtil::extractClassName ).collect( Collectors.toList() ) );
    }
    for( Dependency d : module.getDependencies() )
    {
      if( module == start || d.isExported() )
      {
        findClassFqns( dest, start, (ManModule)d.getModule(), visited );
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
  public boolean processMethodsWithName( @NotNull String name, @NotNull GlobalSearchScope scope, @NotNull Processor<? super PsiMethod> processor )
  {
    return true;
  }

  @NotNull
  @Override
  public String[] getAllMethodNames()
  {
    return new String[0];
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
}

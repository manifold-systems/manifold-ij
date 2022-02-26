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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.file.PsiPackageImpl;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import manifold.api.type.ITypeManifold;
import manifold.api.type.TypeName;
import manifold.ij.core.ManModule;
import org.jetbrains.annotations.NotNull;

/**
 */
public class NonDirectoryPackage extends PsiPackageImpl
{
  NonDirectoryPackage( PsiManager manager, String qualifiedName )
  {
    super( manager, qualifiedName );
  }

  @Override
  public PsiElement copy()
  {
    return new NonDirectoryPackage( getManager(), getQualifiedName() );
  }

  @Override
  public boolean isValid()
  {
    return true;
  }

  @NotNull
  @Override
  public PsiClass[] getClasses()
  {
    return getClasses( allScope() );
  }

  @NotNull
  @Override
  public PsiClass[] getClasses( @NotNull GlobalSearchScope scope )
  {
    PsiClass[] fromSuper = super.getClasses( scope );
    Map<String, PsiClass> all = new LinkedHashMap<>();
    Arrays.stream( fromSuper ).forEach( cls -> all.put( cls.getQualifiedName(), cls ) );
    List<ManModule> modules = ManTypeFinder.findModules( scope );
    for( ManModule module: modules )
    {
      for( ITypeManifold tm: module.getTypeManifolds() )
      {
        Collection<TypeName> typeNames = tm.getTypeNames( getQualifiedName() );
        for( TypeName typeName: typeNames )
        {
          if( typeName.kind != TypeName.Kind.NAMESPACE )
          {
            PsiClass psiClass = ManifoldPsiClassCache.getPsiClass( module, typeName.name );
            if( psiClass != null )
            {
              all.put( typeName.name, psiClass );
            }
          }
        }
      }
    }
    return all.values().toArray( new PsiClass[0] );
  }
}
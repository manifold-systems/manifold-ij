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


import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.SlowOperations;
import manifold.api.fs.IFileFragment;
import manifold.util.concurrent.ConcurrentHashSet;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FragmentCache
{
  private static FragmentCache INSTANCE;

  private final ConcurrentHashMap<Project, ConcurrentHashMap<String, MaybeSmartPsiElementPointer<PsiFileFragment>>> _cache;
  private final ConcurrentHashSet<CacheAdder> _queuedAdds = new ConcurrentHashSet<>();
  private final Set<Project> _reparsingAll = new ConcurrentHashSet<>();


  public static FragmentCache instance()
  {
    return INSTANCE == null ? INSTANCE = new FragmentCache() : INSTANCE;
  }

  public FragmentCache()
  {
    _cache = new ConcurrentHashMap<>();
  }

  public void add( MaybeSmartPsiElementPointer<PsiFileFragment> psiFileFragmentPointer )
  {
    _queuedAdds.add( new CacheAdder( psiFileFragmentPointer ) );
  }
  private class CacheAdder
  {
    private final MaybeSmartPsiElementPointer<PsiFileFragment> _psiFileFragmentPointer;

    public CacheAdder( MaybeSmartPsiElementPointer<PsiFileFragment> psiFileFragmentPointer )
    {
      _psiFileFragmentPointer = psiFileFragmentPointer;
    }

    public boolean process()
    {
      if( _psiFileFragmentPointer.isPermanentlyInvalid() )
      {
        // done trying to add this (it stayed invalid and never became wrapped in a smart pointer)
        return true;
      }

      PsiFileFragment psiFileFragment = _psiFileFragmentPointer.getElement();
      if( psiFileFragment == null )
      {
        // invalid, done with this
        return true;
      }

      if( !psiFileFragment.isValid() )
      {
        // fragment may not be connected to a psi file yet, keep this adder queued for another attempt
        return false;
      }

      IFileFragment fragment = psiFileFragment.getFragment();
      String fqn = FragmentCache.this.getFragmentClassName( fragment, psiFileFragment.getContainingFile() );
      _cache.computeIfAbsent( psiFileFragment.getProject(), __ -> new ConcurrentHashMap<>() )
        .put( fqn, _psiFileFragmentPointer );
      // added to queue, done with this
      return true;
    }
  }

  /**
   * If necessary recreate the fragment file corresponding with the {@code fqn}, which puts it in manifold's file cache, which
   * enables the ManifoldPsiClassCache to resolve the {@code fqn}.
   */
  public void shakeBake( Project project, String fqn )
  {
    if( project.isDisposed() )
    {
      _cache.remove( project );
      return;
    }

    updateCache();

    ConcurrentHashMap<String, MaybeSmartPsiElementPointer<PsiFileFragment>> projCache =
      _cache.computeIfAbsent( project, __ -> new ConcurrentHashMap<>() );
    MaybeSmartPsiElementPointer<PsiFileFragment> smartPointer = projCache.get( fqn );
    if( smartPointer == null )
    {
      return;
    }

    PsiFileFragment psiFileFragment = smartPointer.getElement();
    if( psiFileFragment != null && psiFileFragment.isValid() )
    {
      psiFileFragment.handleFragments( (PsiJavaFile)psiFileFragment.getContainingFile() );
    }
    else
    {
      projCache.remove( fqn );
    }
  }

  private void updateCache()
  {
    expungeDisposedProjects();
    flushQueuedAdds();
  }

  private void expungeDisposedProjects()
  {
    _cache.keySet().removeIf( Project::isDisposed );
  }

  private void flushQueuedAdds()
  {
    for( Iterator<CacheAdder> iterator = _queuedAdds.iterator(); iterator.hasNext(); )
    {
      CacheAdder queuedAdd = iterator.next();
      try
      {
        if( queuedAdd.process() )
        {
          iterator.remove();
        }
      }
      catch( Throwable t )
      {
        iterator.remove();
        throw t;
      }
    }
  }

  /**
   * Reparse all parsed files that have at least one fragment
   */
  public void reparseAll( Project project )
  {
    if( _reparsingAll.contains( project ) )
    {
      return;
    }

    _reparsingAll.add( project );
    try
    {
      ApplicationManager.getApplication().invokeLater( () ->
      {
        try
        {
          ApplicationManager.getApplication().runReadAction( () ->
          {
            if( project.isDisposed() )
            {
              _cache.remove( project );
              return;
            }

            updateCache();

            // note see ide.slow.operations.assertion.manifold.fragments registrykey defined in plugin.xml
            try( AccessToken ignore = SlowOperations.allowSlowOperations( "manifold.fragments" ) )
            {
              Set<PsiFile> files = new HashSet<>();
              ConcurrentHashMap<String, MaybeSmartPsiElementPointer<PsiFileFragment>> projCache = _cache.get( project );
              for( MaybeSmartPsiElementPointer<PsiFileFragment> value : projCache.values() )
              {
                PsiFileFragment psiFileFragment = value.getElement();
                if( psiFileFragment != null )
                {
                  PsiFile containingFile = psiFileFragment.getContainingFile();
                  if( containingFile.getProject() == project )
                  {
                    files.add( containingFile );
                  }
                }
              }
              FileContentUtilCore.reparseFiles( files.stream().map( f -> f.getVirtualFile() ).toList() );
            }
          } );
        }
        finally
        {
          _reparsingAll.remove( project );
        }
      } );
    }
    catch( Throwable t )
    {
      _reparsingAll.remove( project );
    }
  }

  private String getFragmentClassName( IFileFragment fragment, PsiFile containingFile )
  {
    String fragClass = null;
    if( containingFile instanceof PsiJavaFile )
    {
      fragClass = ((PsiJavaFile)containingFile).getPackageName() + '.' + fragment.getBaseName();
    }
    else
    {
      MaybeSmartPsiElementPointer container = (MaybeSmartPsiElementPointer)fragment.getContainer();
      if( container != null )
      {
        PsiElement elem = container.getElement();
        containingFile = elem != null ? elem.getContainingFile() : null;
        if( containingFile != null )
        {
          fragClass = ((PsiJavaFile)containingFile).getPackageName() + '.' + fragment.getBaseName();
        }
      }
    }
    return fragClass;
  }

  public Set<String> getAllClassNames( Project project )
  {
    return _cache.get( project ).keySet();
  }
}

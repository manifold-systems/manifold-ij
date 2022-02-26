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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.lang.DefaultASTFactoryImpl;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.PsiCommentImpl;
import com.intellij.psi.tree.IElementType;
import manifold.api.fs.IFileFragment;
import manifold.internal.javac.HostKind;
import org.jetbrains.annotations.NotNull;

/**
 * Overrides default to handle fragments in comments.  Handling this during parsing as opposed to annotation/resolve
 * phase so that annotation can be marked "dirty" after the comment is created. See usage of {@link DaemonCodeAnalyzer}
 * from {@link PsiFileFragment}.
 * <p/>
 * See {@link ManPsiBuilderImpl} for the same treatment for String literals.
 */
public class ManDefaultASTFactoryImpl extends DefaultASTFactoryImpl
{
  @NotNull
  @Override
  public LeafElement createComment( @NotNull IElementType type, @NotNull CharSequence text )
  {
    if( type.getLanguage() instanceof JavaLanguage )
    {
      return new ManPsiCommentImpl( type, text );
    }

    return super.createComment( type, text );
  }

  static class ManPsiCommentImpl extends PsiCommentImpl implements PsiFileFragment
  {
    private IFileFragment _fragment;

    ManPsiCommentImpl( @NotNull IElementType type, @NotNull CharSequence text )
    {
      super( type, text );
      if( type.getLanguage() instanceof JavaLanguage )
      {
        handleFragments();
      }
    }

    @Override
    public IFileFragment getFragment()
    {
      return _fragment;
    }

    @Override
    public void setFragment( IFileFragment fragment )
    {
      _fragment = fragment;
    }

    @Override
    public HostKind getStyle()
    {
      return ManCommentFragmentInjector.makeStyle( this );
    }
  }
}

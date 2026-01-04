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

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.platform.syntax.psi.PsiSyntaxBuilderFactory;
import com.intellij.platform.syntax.psi.PsiSyntaxBuilderFactoryHook;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import java.util.Set;

import manifold.api.fs.IFileFragment;
import manifold.api.fs.def.FileFragmentImpl;
import manifold.api.type.ITypeManifold;
import manifold.ij.core.ManApplicationLoadListener;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.util.FileUtil;
import manifold.ij.util.ReparseUtil;
import manifold.ij.util.SlowOperationsUtil;
import manifold.internal.javac.FragmentProcessor;
import manifold.internal.javac.HostKind;
import manifold.util.ReflectUtil;
import org.jetbrains.annotations.NotNull;


import static manifold.api.type.ContributorKind.Supplemental;

interface PsiFileFragment extends ASTNode, PsiElement
{
  IFileFragment getFragment();

  void setFragment( IFileFragment fragment );

  HostKind getStyle();

  default void handleFragments()
  {
    handleFragments( null );
  }
  default void handleFragments( PsiJavaFile containingFile )
  {
    if( !getText().contains( FragmentProcessor.FRAGMENT_START ) ||
      !getText().contains( FragmentProcessor.FRAGMENT_END ) )
    {
      // not a fragment
      return;
    }

    if( getText().contains( "IntellijIdeaRulezzz" ) )
    {
      // from completion, ignore this change
      return;
    }

    if( containingFile == null )
    {
      // containingFile is null when tokenizing

      PsiSyntaxBuilderFactoryHook hook = (PsiSyntaxBuilderFactoryHook)ReflectUtil.method( ManApplicationLoadListener.typePsiSyntaxBuilderFactoryKt, "getHook" ).invokeStatic();
      ASTNode buildingNode = hook.peekNode();
      if( buildingNode == null )
      {
        return;
      }

      containingFile = ManPsiBuilderFactoryHook.getPsiFile( buildingNode );
      if( containingFile == null )
      {
        return;
      }
    }

    String hostText = getText();
    FragmentProcessor fragmentProcessor = FragmentProcessor.instance();
    HostKind style = getStyle();
    FragmentProcessor.Fragment f = fragmentProcessor.parseFragment( 0, hostText, style );
    if( f != null )
    {
      Project project = containingFile.getProject();
      FileFragmentImpl fragment = new FileFragmentImpl( f.getScope(), f.getName(), f.getExt(), style,
        FileUtil.toIFile( project, FileUtil.toVirtualFile( containingFile ) ),
        f.getOffset(), f.getContent().length(), f.getContent() );

      // must add a callback for the offset because this element's parent chain is not connected yet
      fragment.setOffset( () -> getStartOffset() + f.getOffset() );

      ManModule module = ManProject.getModule( containingFile );
      if( module == null )
      {
        return;
      }
      Set<ITypeManifold> tms = module.findTypeManifoldsFor( fragment, t -> t.getContributorKind() != Supplemental );
      ITypeManifold tm = tms.stream().findFirst().orElse( null );
      if( tm == null )
      {
        //## todo: add compile warning in annotator
        return;
      }

      setFragment( fragment );
      MaybeSmartPsiElementPointer<PsiFileFragment> psiFileFragmentPointer = new MaybeSmartPsiElementPointer<>( this );
      fragment.setContainer( psiFileFragmentPointer );

      // Cache this to handle cases where the ManifoldPsiClassCache is refreshed e.g., after a maven/gradle refresh.
      // Since the token for the string literal or comment must be reparsed/tokenized when changed, the cache will always
      // be current.
      FragmentCache.instance().add( psiFileFragmentPointer );

      PsiJavaFile finalContainingFile = containingFile;
      // note, this must be posted to the event thread so as not to hold this element's lock while indirectly accessing
      // ManifoldPsiClassCache's monitor, otherwise deadlock will result
      ApplicationManager.getApplication().invokeLater( () ->
        ApplicationManager.getApplication().runReadAction( () -> {
          // note see ide.slow.operations.assertion.manifold.fragments registrykey defined in plugin.xml
          SlowOperationsUtil.allowSlowOperation( "manifold.fragments", () -> {
              deletedFragment( ManProject.manProjectFrom( finalContainingFile.getProject() ), fragment );
              createdFragment( ManProject.manProjectFrom( finalContainingFile.getProject() ), fragment );
            } );
        } ) );
//todo: need to do this, but it bogs down the editor's background processing, cpu fan is constant, takes a while to finish error feedback traffic light, etc.
      if( this instanceof ManDefaultASTFactoryImpl.ManPsiCommentImpl )
      {
        // necessary when renaming a file fragment's type
        ReparseUtil.instance().rerunAnnotators( containingFile );
      }
    }
  }

  default void createdFragment( ManProject project, IFileFragment file )
  {
    project.getFileModificationManager().getManRefresher().created( file );
  }

  default void deletedFragment( ManProject project, IFileFragment file )
  {
    project.getFileModificationManager().getManRefresher().deleted( file );
  }

  default void modifiedFragment( ManProject project, IFileFragment file )
  {
    project.getFileModificationManager().getManRefresher().modified( file );
  }

  default int getStartOffsetInParent()
  {
    return ASTNode.super.getStartOffsetInParent();
  }
}

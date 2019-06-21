package manifold.ij.extensions;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.lang.ASTNode;
import com.intellij.lang.DefaultASTFactoryImpl;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.PsiCommentImpl;
import com.intellij.psi.text.BlockSupport;
import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.LightVirtualFile;
import java.util.Set;
import manifold.api.fs.IFileFragment;
import manifold.api.fs.def.FileFragmentImpl;
import manifold.api.type.ITypeManifold;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.util.FileUtil;
import manifold.internal.javac.CommentProcessor;
import org.jetbrains.annotations.NotNull;


import static manifold.api.type.ContributorKind.Supplemental;

/**
 * Overrides default to handle fragments in comments.  Handling this during parsing as opposed to annotation/resolve
 * phase so that annotation can be marked "dirty" after the comment is created. See usage of DaemonCodeAnalyzer.
 */
public class ManDefaultASTFactoryImpl extends DefaultASTFactoryImpl
{
  @NotNull
  @Override
  public LeafElement createComment( @NotNull IElementType type, @NotNull CharSequence text )
  {
    return new ManPsiCommentImpl( type, text );
  }

  static class ManPsiCommentImpl extends PsiCommentImpl
  {
    private IFileFragment _fragment;

    ManPsiCommentImpl( @NotNull IElementType type, @NotNull CharSequence text )
    {
      super( type, text );
      if( type.getLanguage() instanceof JavaLanguage )
      {
        handleCommentFragments();
      }
    }

    public IFileFragment getFragment()
    {
      return _fragment;
    }

    private void handleCommentFragments()
    {
      ManPsiBuilderFactoryImpl manBuilderFactory = (ManPsiBuilderFactoryImpl)PsiBuilderFactory.getInstance();
      ASTNode buildingNode = manBuilderFactory.peekNode();
      if( buildingNode == null )
      {
        return;
      }

      PsiFile containingFile = null;
      if( buildingNode.getPsi() != null && buildingNode.getPsi().getContainingFile() != null )
      {
        containingFile = buildingNode.getPsi().getContainingFile();
        if( containingFile.getVirtualFile() == null || containingFile.getVirtualFile() instanceof LightVirtualFile )
        {
          containingFile = null;
        }
      }
      if( containingFile == null )
      {
        ASTNode originalNode = Pair.getFirst( buildingNode.getUserData( BlockSupport.TREE_TO_BE_REPARSED ) );
        if( originalNode == null )
        {
          return;
        }

        containingFile = originalNode.getPsi().getContainingFile();
      }

      if( containingFile == null || containingFile.getVirtualFile() == null || containingFile.getVirtualFile() instanceof LightVirtualFile )
      {
        return;
      }

      String hostText = getText();
      CommentProcessor commentProcessor = CommentProcessor.instance();
      CommentProcessor.Style style = ManCommentFragmentInjector.makeStyle( this );
      CommentProcessor.Fragment f = commentProcessor.parseFragment( 0, hostText, style );
      if( f != null )
      {
        FileFragmentImpl fragment = new FileFragmentImpl( f.getName(), f.getExt(),
          style == CommentProcessor.Style.LINE ? FileFragmentImpl.Place.LineComment : FileFragmentImpl.Place.BlockComment,
          FileUtil.toIFile( containingFile.getProject(), FileUtil.toVirtualFile( containingFile ) ),
          f.getOffset(), f.getContent().length(), f.getContent() );

        // must add a callback for the offset because it this comment's parent chain not connected yet
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
          //## todo: add compile warning
          return;
        }

        _fragment = fragment;
        _fragment.setContainer( this );
        deletedFragment( ManProject.manProjectFrom( containingFile.getProject() ), _fragment );
        createdFragment( ManProject.manProjectFrom( containingFile.getProject() ), _fragment );
        rerunAnnotators( containingFile );
      }
    }

    private void rerunAnnotators( PsiFile containingFile )
    {
      ApplicationManager.getApplication().invokeLater(
        () -> {
          try
          {
            DaemonCodeAnalyzer.getInstance( getProject() ).restart( containingFile );
          }
          catch( PsiInvalidElementAccessException ieae )
          {
            System.out.println( ieae );
          }
        } );
    }

    void createdFragment( ManProject project, IFileFragment file )
    {
      project.getFileModificationManager().getManRefresher().created( file );
    }

    void deletedFragment( ManProject project, IFileFragment file )
    {
      project.getFileModificationManager().getManRefresher().deleted( file );
    }

    void modifiedFragment( ManProject project, IFileFragment file )
    {
      project.getFileModificationManager().getManRefresher().modified( file );
    }
  }
}

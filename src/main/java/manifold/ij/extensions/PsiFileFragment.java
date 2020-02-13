package manifold.ij.extensions;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import java.util.Set;
import manifold.api.fs.IFileFragment;
import manifold.api.fs.def.FileFragmentImpl;
import manifold.api.type.ITypeManifold;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.util.FileUtil;
import manifold.ij.util.ReparseUtil;
import manifold.internal.javac.FragmentProcessor;
import manifold.internal.javac.HostKind;


import static manifold.api.type.ContributorKind.Supplemental;

interface PsiFileFragment extends ASTNode, PsiElement
{
  IFileFragment getFragment();

  void setFragment( IFileFragment fragment );

  HostKind getStyle();

  default void handleFragments()
  {
    if( !getText().contains( "[>" ) )
    {
      // not a fragment
      return;
    }

    if( getText().contains( "IntellijIdeaRulezzz" ) )
    {
      // from completion, ignore this change
      return;
    }

    ManPsiBuilderFactoryImpl manBuilderFactory = (ManPsiBuilderFactoryImpl)PsiBuilderFactory.getInstance();
    ASTNode buildingNode = manBuilderFactory.peekNode();
    if( buildingNode == null )
    {
      return;
    }

    PsiJavaFile containingFile = ManPsiBuilderFactoryImpl.getPsiFile( buildingNode );
    if( containingFile == null )
    {
      return;
    }

    String hostText = getText();
    FragmentProcessor fragmentProcessor = FragmentProcessor.instance();
    HostKind style = getStyle();
    FragmentProcessor.Fragment f = fragmentProcessor.parseFragment( 0, hostText, style );
    if( f != null )
    {
      FileFragmentImpl fragment = new FileFragmentImpl( f.getName(), f.getExt(), style,
        FileUtil.toIFile( containingFile.getProject(), FileUtil.toVirtualFile( containingFile ) ),
        f.getOffset(), f.getContent().length(), f.getContent() );

      // must add a callback for the offset because it this element's parent chain not connected yet
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

      setFragment( fragment );
      fragment.setContainer( this );
      deletedFragment( ManProject.manProjectFrom( containingFile.getProject() ), fragment );
      createdFragment( ManProject.manProjectFrom( containingFile.getProject() ), fragment );
      ReparseUtil.rerunAnnotators( containingFile );
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

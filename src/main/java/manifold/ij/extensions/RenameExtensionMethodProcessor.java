package manifold.ij.extensions;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import manifold.ij.core.ManProject;
import manifold.ij.psi.ManLightMethodBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 */
public class RenameExtensionMethodProcessor extends RenamePsiElementProcessor
{
  @Override
  public boolean canProcessElement( @NotNull PsiElement elem )
  {
    if( !ManProject.isManifoldInUse( elem ) )
    {
      return false;
    }

    return elem instanceof ManLightMethodBuilder;
  }

  @Nullable
  @Override
  public PsiElement substituteElementToRename( @NotNull PsiElement elem, @Nullable Editor editor )
  {
    if( !ManProject.isManifoldInUse( elem ) )
    {
      return null;
    }

    return elem.getNavigationElement();
  }
}

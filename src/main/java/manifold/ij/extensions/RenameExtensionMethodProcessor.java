package manifold.ij.extensions;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
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
    return elem instanceof ManLightMethodBuilder;
  }

  @Nullable
  @Override
  public PsiElement substituteElementToRename( PsiElement elem, @Nullable Editor editor )
  {
    return elem.getNavigationElement();
  }
}

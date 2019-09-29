package manifold.ij.extensions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.search.PsiSearchHelperImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiNonJavaFileReferenceProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ManPsiSearchHelperImpl extends PsiSearchHelperImpl
{
  public ManPsiSearchHelperImpl( @NotNull PsiManagerEx manager )
  {
    super( manager );
  }

  @Override
  public boolean processUsagesInNonJavaFiles( @Nullable final PsiElement originalElement,
                                              @NotNull String qName,
                                              @NotNull final PsiNonJavaFileReferenceProcessor processor,
                                              @NotNull final GlobalSearchScope initialScope )
  {
    if( qName.isEmpty() )
    {
      // There is a bug in IJ dealing with PsiMethod with empty name coming from Manifold Templates.
      // We avoid the super class throwing an exception if empty string.
      return false;
    }

    return super.processUsagesInNonJavaFiles( originalElement, qName, processor, initialScope );
  }
}

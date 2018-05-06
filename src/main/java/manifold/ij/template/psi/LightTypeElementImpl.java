package manifold.ij.template.psi;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LightTypeElementImpl extends CompositePsiElement implements PsiTypeElement
{
  private final PsiType _type;

  protected LightTypeElementImpl( PsiType type )
  {
    super( JavaElementType.TYPE );
    _type = type;
  }

  @NotNull
  @Override
  public PsiType getType()
  {
    return _type;
  }

  @Nullable
  @Override
  public PsiJavaCodeReferenceElement getInnermostComponentReferenceElement()
  {
    return null;
  }

  @NotNull
  @Override
  public PsiAnnotation[] getAnnotations()
  {
    return new PsiAnnotation[0];
  }

  @NotNull
  @Override
  public PsiAnnotation[] getApplicableAnnotations()
  {
    return new PsiAnnotation[0];
  }

  @Nullable
  @Override
  public PsiAnnotation findAnnotation( @NotNull String qualifiedName )
  {
    return null;
  }

  @NotNull
  @Override
  public PsiAnnotation addAnnotation( @NotNull String qualifiedName )
  {
    throw new UnsupportedOperationException();
  }
}

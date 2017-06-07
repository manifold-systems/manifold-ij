package manifold.ij.psi;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.psi.impl.light.LightIdentifier;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.util.IncorrectOperationException;

/**
 */
public class ManifoldLightFieldBuilderImpl extends LightFieldBuilder implements ManifoldLightFieldBuilder
{
  protected final LightIdentifier _nameIdentifier;

  public ManifoldLightFieldBuilderImpl( PsiManager manager, String name, PsiType type )
  {
    super( manager, name, type );
    _nameIdentifier = new LightIdentifier( manager, name );
  }

  @Override
  public ManifoldLightFieldBuilder withContainingClass( PsiClass psiClass )
  {
    setContainingClass( psiClass );
    return this;
  }

  @Override
  public ManifoldLightFieldBuilder withModifier( @PsiModifier.ModifierConstant String modifier )
  {
    ((LightModifierList)getModifierList()).addModifier( modifier );
    return this;
  }

  @Override
  public ManifoldLightFieldBuilder withNavigationElement( PsiElement navigationElement )
  {
    setNavigationElement( navigationElement );
    return this;
  }

  @Override
  public PsiIdentifier getNameIdentifier()
  {
    return _nameIdentifier;
  }

  public String toString()
  {
    return "ManifoldLightFieldBuilder: " + getName();
  }

  @Override
  public PsiElement replace( PsiElement newElement ) throws IncorrectOperationException
  {
    // just add new element to the containing class
    final PsiClass containingClass = getContainingClass();
    if( null != containingClass )
    {
      CheckUtil.checkWritable( containingClass );
      return containingClass.add( newElement );
    }
    return null;
  }

  @Override
  public void delete() throws IncorrectOperationException
  {
    // simple do nothing
  }

  @Override
  public void checkDelete() throws IncorrectOperationException
  {
    // simple do nothing
  }
}

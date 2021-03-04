package manifold.ij.psi;

import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.psi.impl.light.LightIdentifier;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.util.IncorrectOperationException;

/**
 */
public class ManLightFieldBuilderImpl extends LightFieldBuilder implements ManLightFieldBuilder
{
  protected final LightIdentifier _nameIdentifier;

  public ManLightFieldBuilderImpl( PsiManager manager, String name, PsiType type )
  {
    super( manager, name, type );
    _nameIdentifier = new LightIdentifier( manager, name );
  }

  @Override
  public ManLightFieldBuilder withContainingClass( PsiClass psiClass )
  {
    setContainingClass( psiClass );
    return this;
  }

  @Override
  public ManLightFieldBuilder withModifier( @PsiModifier.ModifierConstant String modifier )
  {
    ((LightModifierList)getModifierList()).addModifier( modifier );
    return this;
  }

  @Override
  public ManLightFieldBuilder withModifierList( LightModifierList modifierList )
  {
    setModifierList( modifierList );
    return this;
  }

  @Override
  public ManLightFieldBuilder withNavigationElement( PsiElement navigationElement )
  {
    setNavigationElement( navigationElement );
    return this;
  }

  @Override
  public PsiIdentifier getNameIdentifier()
  {
    return _nameIdentifier;
  }

  @Override
  public PsiFile getContainingFile()
  {
    PsiClass psiClass = getContainingClass();
    return psiClass == null ? null : psiClass.getContainingFile();
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
  }

  @Override
  public void checkDelete() throws IncorrectOperationException
  {
  }
}

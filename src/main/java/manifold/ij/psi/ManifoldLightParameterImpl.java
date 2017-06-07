package manifold.ij.psi;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightIdentifier;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.impl.light.LightParameter;
import com.intellij.psi.impl.light.LightVariableBuilder;

/**
 */
public class ManifoldLightParameterImpl extends LightParameter
{
  private final LightIdentifier myNameIdentifier;

  public ManifoldLightParameterImpl( String name, PsiType type, PsiElement declarationScope, Language language )
  {
    super( name, type, declarationScope, language );
    PsiManager manager = declarationScope.getManager();
    myNameIdentifier = new LightIdentifier( manager, name );
    ReflectionUtil.setFinalFieldPerReflection( LightVariableBuilder.class, this, LightModifierList.class,
                                               new ManifoldLightModifierListImpl( manager, language ) );
  }

  @Override
  public PsiIdentifier getNameIdentifier()
  {
    return myNameIdentifier;
  }

  public ManifoldLightParameterImpl setModifiers( String... modifiers )
  {
    ManifoldLightModifierListImpl modifierList = new ManifoldLightModifierListImpl( getManager(), getLanguage(), modifiers );
    ReflectionUtil.setFinalFieldPerReflection( LightVariableBuilder.class, this, LightModifierList.class, modifierList );
    return this;
  }
}

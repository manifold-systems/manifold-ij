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
public class ManLightParameterImpl extends LightParameter
{
  private final LightIdentifier myNameIdentifier;

  public ManLightParameterImpl( String name, PsiType type, PsiElement declarationScope, Language language )
  {
    super( name, type, declarationScope, language );
    PsiManager manager = declarationScope.getManager();
    myNameIdentifier = new LightIdentifier( manager, name );
    ReflectionUtil.setFinalFieldPerReflection( LightVariableBuilder.class, this, LightModifierList.class,
                                               new ManLightModifierListImpl( manager, language ) );
  }

  @Override
  public PsiIdentifier getNameIdentifier()
  {
    return myNameIdentifier;
  }

  public ManLightParameterImpl setModifiers( String... modifiers )
  {
    ManLightModifierListImpl modifierList = new ManLightModifierListImpl( getManager(), getLanguage(), modifiers );
    ReflectionUtil.setFinalFieldPerReflection( LightVariableBuilder.class, this, LightModifierList.class, modifierList );
    return this;
  }
}

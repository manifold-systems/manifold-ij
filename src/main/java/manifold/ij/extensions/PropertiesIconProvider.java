package manifold.ij.extensions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IconProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static manifold.ij.extensions.PropertyInference.*;

public class PropertiesIconProvider extends IconProvider
{
  @Override
  public @Nullable Icon getIcon( @NotNull PsiElement element, int flags )
  {
    if( !(element instanceof PsiField) || !isPropertyField( (PsiField)element ) )
    {
      return null;
    }

    PsiField field = (PsiField)element;

    PsiModifierList modifierList = field.getModifierList();
    boolean isStatic = modifierList != null && modifierList.hasExplicitModifier( PsiModifier.STATIC );
    if( isReadOnlyProperty( field ) )
    {
      return isStatic ? AllIcons.Nodes.PropertyReadStatic : AllIcons.Nodes.PropertyRead;
    }
    else if( isWriteOnlyProperty( field ) )
    {
      return isStatic ? AllIcons.Nodes.PropertyWriteStatic : AllIcons.Nodes.PropertyWrite;
    }
    else
    {
      return isStatic ? AllIcons.Nodes.PropertyReadWriteStatic : AllIcons.Nodes.PropertyReadWrite;
    }
  }
}

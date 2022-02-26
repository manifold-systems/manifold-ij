/*
 *
 *  * Copyright (c) 2022 - Manifold Systems LLC
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

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

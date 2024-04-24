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

package manifold.ij.psi;

import com.intellij.psi.*;
import com.intellij.psi.augment.PsiExtensionMethod;
import manifold.ext.rt.api.This;
import manifold.ext.rt.api.ThisClass;
import manifold.ij.core.ManModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Instances of this class are fake methods added to an extended class. Note, they must implement PsiExtensionMethod to
 * support some ij internal behavior e.g., handling of parameter mapping from fake method to extension method such as
 * NotNull etc. on extension method parameters.
 */
public class ManExtensionMethodBuilder extends ManLightMethodBuilderImpl implements PsiExtensionMethod
{
    private final PsiMethod _target;

    public ManExtensionMethodBuilder( ManModule manModule, PsiManager manager, String name, PsiModifierList modifierList, PsiMethod target )
    {
        super( manModule, manager, name, modifierList );
        _target = target;
    }

    @Override
    public @NotNull PsiMethod getTargetMethod()
    {
        return _target;
    }

    @Override
    public @Nullable PsiParameter getTargetReceiverParameter()
    {
        for( PsiParameter param: _target.getParameterList().getParameters() )
        {
            PsiModifierList modifierList = param.getModifierList();
            if( modifierList != null && modifierList.findAnnotation( This.class.getName() ) != null )
            {
                return param;
            }
        }
        return null;
    }

    @Override
    public @Nullable PsiParameter getTargetParameter( int index )
    {
        if( index >= _target.getParameterList().getParametersCount() )
        {
            return null;
        }

        for( PsiParameter param: _target.getParameterList().getParameters() )
        {
            PsiModifierList modifierList = param.getModifierList();
            if( modifierList != null &&
                (modifierList.findAnnotation( This.class.getName() ) != null ||
                 modifierList.findAnnotation( ThisClass.class.getName() ) != null) )
            {
                index++;
                break;
            }
        }
        return _target.getParameterList().getParameter( index );
    }
}

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

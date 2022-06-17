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

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import manifold.ExtIssueMsg;
import manifold.ext.rt.api.Jailbreak;
import manifold.ij.core.ManProject;
import org.jetbrains.annotations.NotNull;

public class AnonymousTypeUsageAnnotator implements Annotator
{
  @Override
  public void annotate( @NotNull PsiElement element, @NotNull AnnotationHolder holder )
  {
    if( !ManProject.isManifoldInUse( element ) )
    {
      // Manifold jars are not used in the project
      return;
    }

    prohibitPostfixAssignmentUse( element, holder );
    prohibitPrefixAssignmentUse( element, holder );
  }

  private void prohibitPostfixAssignmentUse( PsiElement element, AnnotationHolder holder )
  {
    if( !(element instanceof PsiPostfixExpression) )
    {
      return;
    }

    PsiExpression lhs = ((PsiPostfixExpression)element).getOperand();
    if( !(lhs instanceof PsiReferenceExpression) )
    {
      return;
    }

    PsiExpression qualifier = ((PsiReferenceExpression)lhs).getQualifierExpression();
    if( qualifier == null || qualifier.getType() == null ||
        qualifier.getType().findAnnotation( Jailbreak.class.getTypeName() ) == null )
    {
      return;
    }

    holder.newAnnotation( HighlightSeverity.ERROR, ExtIssueMsg.MSG_INCREMENT_OP_NOT_ALLOWED_REFLECTION.get() )
      .range( ((PsiPostfixExpression)element).getOperationSign().getTextRange() )
      .create();
  }

  private void prohibitPrefixAssignmentUse( PsiElement element, AnnotationHolder holder )
  {
    if( !(element instanceof PsiPrefixExpression) )
    {
      return;
    }

    PsiExpression lhs = ((PsiPrefixExpression)element).getOperand();
    if( !(lhs instanceof PsiReferenceExpression) )
    {
      return;
    }

    PsiExpression qualifier = ((PsiReferenceExpression)lhs).getQualifierExpression();
    if( qualifier == null || qualifier.getType() == null ||
        qualifier.getType().findAnnotation( Jailbreak.class.getTypeName() ) == null )
    {
      return;
    }

    IElementType op = ((PsiPrefixExpression)element).getOperationTokenType();
    if( op == JavaTokenType.PLUSPLUS || op == JavaTokenType.MINUSMINUS )
    {
      holder.newAnnotation( HighlightSeverity.ERROR, ExtIssueMsg.MSG_INCREMENT_OP_NOT_ALLOWED_REFLECTION.get() )
        .range( ((PsiPrefixExpression)element).getOperationSign().getTextRange() )
        .create();
    }
  }

}

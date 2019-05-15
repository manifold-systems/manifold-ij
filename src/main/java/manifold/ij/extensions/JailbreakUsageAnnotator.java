package manifold.ij.extensions;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiPostfixExpression;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.tree.IElementType;
import manifold.ExtIssueMsg;
import manifold.ext.api.Jailbreak;
import manifold.ij.core.ManProject;
import org.jetbrains.annotations.NotNull;

public class JailbreakUsageAnnotator implements Annotator
{
  @Override
  public void annotate( @NotNull PsiElement element, @NotNull AnnotationHolder holder )
  {
    if( !ManProject.isManifoldInUse( element ) )
    {
      // Manifold jars are not used in the project
      return;
    }

    prohibitCompoundAssignmentUse( element, holder );
    prohibitPostfixAssignmentUse( element, holder );
    prohibitPrefixAssignmentUse( element, holder );
  }

  private void prohibitCompoundAssignmentUse( PsiElement element, AnnotationHolder holder )
  {
    if( !(element instanceof PsiAssignmentExpression) )
    {
      return;
    }

    PsiExpression lhs = ((PsiAssignmentExpression)element).getLExpression();
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

    if( ((PsiAssignmentExpression)element).getOperationTokenType() != JavaTokenType.EQ )
    {
      holder.createAnnotation( HighlightSeverity.ERROR, ((PsiAssignmentExpression)element).getOperationSign().getTextRange(),
        ExtIssueMsg.MSG_COMPOUND_OP_NOT_ALLOWED_REFLECTION.get() );
    }
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

    holder.createAnnotation( HighlightSeverity.ERROR, ((PsiPostfixExpression)element).getOperationSign().getTextRange(),
      ExtIssueMsg.MSG_INCREMENT_OP_NOT_ALLOWED_REFLECTION.get() );
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
      holder.createAnnotation( HighlightSeverity.ERROR, ((PsiPrefixExpression)element).getOperationSign().getTextRange(),
        ExtIssueMsg.MSG_INCREMENT_OP_NOT_ALLOWED_REFLECTION.get() );
    }
  }

}

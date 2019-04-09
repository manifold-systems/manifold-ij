package manifold.ij.extensions;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import manifold.ExtIssueMsg;
import manifold.ext.api.Extension;
import manifold.ext.api.This;
import org.jetbrains.annotations.NotNull;

/**
 * Annotator for miscellaneous errors & warnings
 */
public class MiscAnnotator implements Annotator
{
  @Override
  public void annotate( @NotNull PsiElement element, @NotNull AnnotationHolder holder )
  {
    verifyMethodRefNotExtension( element, holder );
  }

  private void verifyMethodRefNotExtension( PsiElement element, AnnotationHolder holder )
  {
    if( element instanceof PsiMethodReferenceExpression )
    {
      // Extension method ref not allowed on an extension method

      PsiElement psiMethod = ((PsiMethodReferenceExpression)element).resolve();
      if( psiMethod instanceof PsiMethod && isExtensionMethod( (PsiMethod)psiMethod ) )
      {
        holder.createAnnotation( HighlightSeverity.ERROR, element.getTextRange(),
          ExtIssueMsg.MSG_EXTENSION_METHOD_REF_NOT_SUPPORTED.get( ((PsiMethod)psiMethod).getName() ) );
      }
    }
  }

  private boolean isExtensionMethod( PsiMethod method )
  {
    PsiElement navigationElement = method.getNavigationElement();
    if( navigationElement instanceof PsiMethod && navigationElement != method )
    {
      method = (PsiMethod)navigationElement;
    }
    
    PsiModifierList methodMods = method.getModifierList();
    if( methodMods.findAnnotation( Extension.class.getName() ) != null )
    {
      return true;
    }

    for( PsiParameter param: method.getParameterList().getParameters() )
    {
      PsiModifierList modifierList = param.getModifierList();
      if( modifierList != null &&
          modifierList.findAnnotation( This.class.getName() ) != null )
      {
        return true;
      }
    }
    return false;
  }
}

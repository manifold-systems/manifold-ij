package manifold.ij.extensions;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.*;
import manifold.ExtIssueMsg;
import manifold.api.util.IssueMsg;
import manifold.ext.rt.api.Extension;
import manifold.ext.rt.api.This;
import manifold.ext.rt.api.ThisClass;
import manifold.ij.core.ManProject;
import manifold.ij.util.ManPsiUtil;
import manifold.internal.javac.ManAttr;
import manifold.rt.api.util.ManClassUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Annotator for miscellaneous errors & warnings
 */
public class MiscAnnotator implements Annotator
{
  @Override
  public void annotate( @NotNull PsiElement element, @NotNull AnnotationHolder holder )
  {
    if( !ManProject.isManifoldInUse( element ) )
    {
      return;
    }

    verifyMethodRefNotExtension( element, holder );
    verifyMethodDefNotAbstractAuto( element, holder );
  }

  private void verifyMethodDefNotAbstractAuto( PsiElement element, AnnotationHolder holder )
  {
    if( !(element instanceof PsiMethod) )
    {
      return;
    }

    PsiMethod meth = (PsiMethod)element;
    if( isAutoMethod( meth.getReturnTypeElement() ) &&
      meth.getModifierList().hasModifierProperty( PsiModifier.ABSTRACT ) )
    {
      // 'auto' return type inference not allowed on abstract methods

      holder.newAnnotation( HighlightSeverity.ERROR,
          IssueMsg.MSG_AUTO_CANNOT_RETURN_AUTO_FROM_ABSTRACT_METHOD.get() )
        .range( meth.getReturnTypeElement().getTextRange() )
        .create();
    }
  }

  private boolean isAutoMethod( PsiTypeElement typeElement )
  {
    if( typeElement == null )
    {
      return false;
    }
    String fqn = typeElement.getText();
    return fqn.equals( ManClassUtil.getShortClassName( ManAttr.AUTO_TYPE ) ) ||
      fqn.equals( ManAttr.AUTO_TYPE );
  }

  private void verifyMethodRefNotExtension( PsiElement element, AnnotationHolder holder )
  {
    if( element instanceof PsiMethodReferenceExpression )
    {

      PsiElement maybeMethod = ((PsiMethodReferenceExpression)element).resolve();
      if( maybeMethod instanceof PsiMethod )
      {
        PsiMethod psiMethod = (PsiMethod)maybeMethod;
        if( isExtensionMethod( psiMethod ) )
        {
          // Method ref not allowed on an extension method
          holder.newAnnotation( HighlightSeverity.ERROR,
              ExtIssueMsg.MSG_EXTENSION_METHOD_REF_NOT_SUPPORTED.get( psiMethod.getName() ) )
            .range( element.getTextRange() )
            .create();
        }
        else if( isStructuralInterfaceMethod( psiMethod ) )
        {
          // Method ref not allowed on a structural interface method
          holder.newAnnotation( HighlightSeverity.ERROR,
              ExtIssueMsg.MSG_STRUCTURAL_METHOD_REF_NOT_SUPPORTED.get( psiMethod.getName() ) )
            .range( element.getTextRange() )
            .create();
        }
      }
    }
  }

  private boolean isStructuralInterfaceMethod( PsiMethod psiMethod )
  {
    return ManPsiUtil.isStructuralInterface( psiMethod.getContainingClass() );
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
        (modifierList.findAnnotation( This.class.getName() ) != null ||
          modifierList.findAnnotation( ThisClass.class.getName() ) != null) )
      {
        return true;
      }
    }
    return false;
  }
}

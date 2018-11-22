package manifold.ij.extensions;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.PsiTreeUtil;
import manifold.ExtIssueMsg;
import manifold.ext.api.Self;
import manifold.ext.api.This;
import org.jetbrains.annotations.NotNull;


import static com.intellij.refactoring.util.RefactoringUtil.getEnclosingMethod;

public class SelfUsageAnnotator implements Annotator
{
  @Override
  public void annotate( @NotNull PsiElement element, @NotNull AnnotationHolder holder )
  {
    if( !(element instanceof PsiAnnotation) )
    {
      return;
    }

    PsiAnnotation psiAnno = (PsiAnnotation)element;
    if( !Self.class.getTypeName().equals( psiAnno.getQualifiedName() ) )
    {
      return;
    }

    verifySelfAnnotation( psiAnno, holder );
    
  }

  private void verifySelfAnnotation( PsiAnnotation psiAnno, AnnotationHolder holder )
  {
    PsiElement parent = psiAnno.getParent();
    if( parent instanceof PsiModifierList )
    {
      // @Self is directly on a Method (implies on the return type)

      PsiElement m = parent.getParent();
      if( m instanceof PsiMethod )
      {
        PsiMethod method = (PsiMethod)m;
        if( !method.hasModifier( JvmModifier.STATIC ) )
        {
          // @Self is on an Instance method

          PsiType returnType = method.getReturnType();
          PsiClass containingClass = ManifoldPsiClassAnnotator.getContainingClass( psiAnno );
          verifyTypeSameAsEnclosingClass( psiAnno, returnType, containingClass.getQualifiedName(), holder );
          return;
        }
        else if( isExtensionMethod( method ) )
        {
          // @Self is on an Extension method

          PsiType returnType = method.getReturnType();

          verifyTypeSameAsEnclosingClass( psiAnno, returnType, findExtendedClass( method ), holder );
          return;
        }
      }
    }
    else if( parent instanceof PsiTypeElement )
    {
      // @Self is on a Type

      PsiMethod method = getEnclosingMethod( parent );
      if( method != null )
      {
        PsiTypeElement returnTypeElement = method.getReturnTypeElement();
        if( PsiTreeUtil.isAncestor( returnTypeElement, parent, false ) )
        {
          // @Self is on the Return type

          if( !method.hasModifier( JvmModifier.STATIC ) )
          {
            // @Self is on an Instance method

            PsiType type = ((PsiTypeElement)parent).getType();
            PsiClass containingClass = ManifoldPsiClassAnnotator.getContainingClass( psiAnno );
            verifyTypeSameAsEnclosingClass( psiAnno, type, containingClass.getQualifiedName(), holder );
            return;
          }
          else if( isExtensionMethod( method ) )
          {
            // @Self is on an Extension method

            PsiType type = ((PsiTypeElement)parent).getType();
            verifyTypeSameAsEnclosingClass( psiAnno, type, findExtendedClass( method ), holder );
            return;
          }
        }
      }
    }
    holder.createAnnotation( HighlightSeverity.ERROR, psiAnno.getTextRange(),
      ExtIssueMsg.MSG_SELF_NOT_ALLOWED_HERE.get() );
  }

  private String findExtendedClass( PsiMethod method )
  {
    PsiClass extensionClass = ExtensionClassAnnotator.findExtensionClass( method );
    if( extensionClass == null )
    {
      return null;
    }
    return ExtensionClassAnnotator.getExtendedClassName( ((PsiJavaFile)extensionClass.getContainingFile()).getPackageName() );
  }

  private boolean isExtensionMethod( PsiMethod method )
  {
    if( ExtensionClassAnnotator.findExtensionClass( method ) != null )
    {
      for( PsiParameter param: method.getParameterList().getParameters() )
      {
        PsiModifierList modifierList = param.getModifierList();
        if( modifierList != null &&
            modifierList.findAnnotation( This.class.getName() ) != null )
        {
          return true;
        }
      }
    }
    return false;
  }

  private void verifyTypeSameAsEnclosingClass( PsiAnnotation psiAnno, PsiType returnType, String enclosingClass, AnnotationHolder holder )
  {
    PsiClassType classType = getClassType( returnType );
    if( classType != null )
    {
      //noinspection ConstantConditions
      if( enclosingClass == null || !classType.equalsToText( enclosingClass ) )
      {
        holder.createAnnotation( HighlightSeverity.ERROR, psiAnno.getTextRange(),
          ExtIssueMsg.MSG_SELF_NOT_ON_CORRECT_TYPE.get( classType.getPresentableText(),
            enclosingClass == null ? "unknown" : enclosingClass ) );
      }
    }
  }

  private PsiClassType getClassType( PsiType returnType )
  {
    if( returnType instanceof PsiClassType )
    {
      return ((PsiClassType)returnType).rawType();
    }
    if( returnType instanceof PsiArrayType )
    {
      return getClassType( ((PsiArrayType)returnType).getComponentType() );
    }
    return null;
  }
}

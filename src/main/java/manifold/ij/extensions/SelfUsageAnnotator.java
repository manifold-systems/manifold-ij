package manifold.ij.extensions;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import java.util.ArrayList;
import java.util.List;
import manifold.ExtIssueMsg;
import manifold.ext.rt.api.Self;
import manifold.ext.rt.api.This;
import manifold.ext.rt.api.ThisClass;
import manifold.ij.core.ManProject;
import org.jetbrains.annotations.NotNull;


import static com.intellij.refactoring.util.RefactoringUtil.getEnclosingMethod;

public class SelfUsageAnnotator implements Annotator
{
  @Override
  public void annotate( @NotNull PsiElement element, @NotNull AnnotationHolder holder )
  {
    if( !ManProject.isManifoldInUse( element ) )
    {
      return;
    }

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
    PsiModifierList modifierList = getInModifierList( parent );

    if( modifierList != null )
    {
      PsiElement modifierListOnwer = modifierList.getParent();
      if( modifierListOnwer instanceof PsiMethod )
      {
        // @Self is directly on a Method (implies on the return type)

        PsiMethod method = (PsiMethod)modifierListOnwer;
        if( !method.isConstructor() && verifySelfTypeSameAsEnclosing( psiAnno, holder, method.getReturnType(), method ) )
        {
          return;
        }
      }
      else if( modifierListOnwer instanceof PsiParameter )
      {
        // @Self is on a Parameter

        if( verify( psiAnno, holder, modifierListOnwer, ((PsiParameter)modifierListOnwer).getType() ) )
        {
          return;
        }
      }
      else if( modifierListOnwer instanceof PsiField )
      {
        // @Self is on a Field

        if( verifySelfTypeSameAsEnclosing( psiAnno, holder, ((PsiField)modifierListOnwer).getType(), (PsiField)modifierListOnwer ) )
        {
          return;
        }
      }
    }
    else if( parent instanceof PsiTypeElement )
    {
      // @Self is on a Type

      if( verify( psiAnno, holder, parent, ((PsiTypeElement)parent).getType() ) )
      {
        return;
      }
    }
    holder.newAnnotation( HighlightSeverity.ERROR, ExtIssueMsg.MSG_SELF_NOT_ALLOWED_HERE.get() )
      .range( psiAnno.getTextRange() )
      .create();
  }

  private PsiModifierList getInModifierList( PsiElement parent )
  {
    while( !(parent instanceof PsiModifierList) )
    {
      if( parent == null )
      {
        return null;
      }
      parent = parent.getParent();
    }
    return (PsiModifierList)parent;
  }

  private boolean verify( PsiAnnotation psiAnno, AnnotationHolder holder, PsiElement parent, PsiType type )
  {
    PsiMethod method = getEnclosingMethod( parent );
    if( method != null )
    {
      PsiTypeElement returnTypeElement = method.getReturnTypeElement();
      if( PsiTreeUtil.isAncestor( returnTypeElement, parent, false ) ||
          PsiTreeUtil.isAncestor( method.getParameterList(), parent, false ) )
      {
        // @Self is on the method's Return type or a Parameter

        return verifySelfTypeSameAsEnclosing( psiAnno, holder, type, method );
      }
    }
    else
    {
      PsiField field = getDeclaringField( parent );
      if( field != null )
      {
        // @Self is on/in the field's type

        return verifySelfTypeSameAsEnclosing( psiAnno, holder, type, field );
      }
    }
    return false;
  }

  private PsiField getDeclaringField( PsiElement elem )
  {
    while( !(elem instanceof PsiField) )
    {
      if( elem == null )
      {
        return null;
      }
      elem = elem.getParent();
    }
    return (PsiField)elem;
  }

  private boolean verifySelfTypeSameAsEnclosing( PsiAnnotation psiAnno, AnnotationHolder holder, PsiType type, PsiMember member )
  {
    if( member.hasModifier( JvmModifier.STATIC ) && member instanceof PsiMethod &&
      isExtensionMethod( (PsiMethod)member ) )
    {
      // @Self is on an Extension method

      verifyTypeSameAsEnclosingClass( psiAnno, type, findExtendedClass( (PsiMethod)member ), holder, true );
    }
    else
    {
      // @Self is on a normal method or field

      PsiClass containingClass = ManifoldPsiClassAnnotator.getContainingClass( psiAnno );
      verifyTypeSameAsEnclosingClass( psiAnno, type, containingClass, holder, false );
    }
      return true;
    }

  private PsiClass findExtendedClass( PsiMethod method )
  {
    PsiClass extensionClass = ExtensionClassAnnotator.findExtensionClass( method );
    if( extensionClass == null )
    {
      return null;
    }
    String fqnExtended = ExtensionClassAnnotator.getExtendedClassName(
      ((PsiJavaFile)extensionClass.getContainingFile()).getPackageName() );
    return JavaPsiFacade.getInstance( method.getProject() )
      .findClass( fqnExtended, GlobalSearchScope.allScope( method.getProject() ) );
  }

  private boolean isExtensionMethod( PsiMethod method )
  {
    if( ExtensionClassAnnotator.findExtensionClass( method ) != null )
    {
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
    }
    return false;
  }

  private void verifyTypeSameAsEnclosingClass( PsiAnnotation psiAnno, PsiType type, PsiClass enclosingClass, AnnotationHolder holder, boolean isExtension )
  {
    PsiClassType classType = createParameterizedType( enclosingClass );
    if( classType != null )
    {
      type = type.getDeepComponentType();
      if( !(type instanceof PsiClassType) ||
          ((PsiClassType)type).hasParameters() != classType.hasParameters() ||
          (isExtension
           ? !type.isAssignableFrom( TypeConversionUtil.erasure( classType ) )
           : !type.isAssignableFrom( classType )) )
      {
        holder.newAnnotation( HighlightSeverity.ERROR,
            ExtIssueMsg.MSG_SELF_NOT_ON_CORRECT_TYPE.get( type.getPresentableText(), classType.getPresentableText() ) )
          .range( psiAnno.getTextRange() )
          .create();
      }
    }
  }

  // Foo -> Foo<T>
  private static PsiClassType createParameterizedType( PsiClass psiClass )
  {
    PsiClassType type = PsiTypesUtil.getClassType( psiClass );
    if( !psiClass.hasTypeParameters() )
    {
      return type;
    }

    List<PsiType> typeParams = new ArrayList<>();
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance( psiClass.getManager().getProject() ).getElementFactory();
    for( PsiTypeParameter p: psiClass.getTypeParameters() )
    {
      typeParams.add( elementFactory.createType( p ) );
    }
    return elementFactory.createType( psiClass, typeParams.toArray( new PsiType[0] ) );
  }
}

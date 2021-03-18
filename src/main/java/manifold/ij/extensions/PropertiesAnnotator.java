package manifold.ij.extensions;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import manifold.ext.props.rt.api.*;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.Objects;

import static java.lang.reflect.Modifier.*;
import static manifold.ext.props.PropIssueMsg.*;
import static manifold.ij.extensions.PropertyInference.*;

/**
 *
 */
public class PropertiesAnnotator implements Annotator
{
  @Override
  public void annotate( @NotNull PsiElement element, @NotNull AnnotationHolder holder )
  {
    if( !ManProject.isManifoldInUse( element ) )
    {
      // Manifold jars are not used in the project
      return;
    }

    if( DumbService.getInstance( element.getProject() ).isDumb() )
    {
      // skip processing during index rebuild
      return;
    }

    ManModule module = ManProject.getModule( element );
    if( module != null && !module.isPropertiesEnabled() )
    {
      // project/module not using properties
      return;
    }

    handle_MSG_CANNOT_ASSIGN_READONLY_PROPERTY( element, holder );
    handle_MSG_CANNOT_ACCESS_WRITEONLY_PROPERTY( element, holder );

    checkProperty( element, holder );

    checkDirectUseOfAccessor( element, holder );
  }

  private void checkDirectUseOfAccessor( PsiElement element, AnnotationHolder holder )
  {
    if( element instanceof PsiReferenceExpression )
    {
      PsiElement method = ((PsiReferenceExpression)element).resolve();
      if( method instanceof PsiMethod )
      {
        PsiField field = getPropertyFieldFrom( (PsiMethod)method );
        if( field != null )
        {
          PsiElement refElem = ((PsiReferenceExpression)element).getReferenceNameElement();
          refElem = refElem == null ? ((PsiReferenceExpression)element).getElement() : refElem;
          TextRange range = new TextRange( refElem.getTextRange().getStartOffset(), refElem.getTextRange().getEndOffset() );
          holder.newAnnotation( HighlightSeverity.WARNING,
            "Use property '" + field.getName() + "' here" )
            .range( range )
            .create();
        }
      }
    }
  }

  private void checkProperty( PsiElement element, AnnotationHolder holder )
  {
    if( element instanceof PsiField )
    {
      PropertyInference.VarTagInfo tag = element.getCopyableUserData( VAR_TAG );
      if( tag != null )
      {
        // inferred property field should not be error checked
        return;
      }

      PsiClass psiClass = ((PsiField)element).getContainingClass();
      if( psiClass instanceof PsiExtensibleClass )
      {
        PropertyMaker.checkProperty( (PsiField)element, (PsiExtensibleClass)psiClass, holder );
      }
    }
  }

  private void handle_MSG_CANNOT_ASSIGN_READONLY_PROPERTY( PsiElement element, AnnotationHolder holder )
  {
    if( !(element instanceof PsiAssignmentExpression) )
    {
      return;
    }
    PsiAssignmentExpression expr = (PsiAssignmentExpression)element;
    PsiExpression lhs = expr.getLExpression();
    if( lhs instanceof PsiReferenceExpression )
    {
      PsiElement resolve = ((PsiReferenceExpression)lhs).resolve();
      if( resolve instanceof PsiField )
      {
        if( isReadOnlyProperty( lhs, (PsiField)resolve, PsiUtil.getTopLevelClass( element ) ) &&
          (!inOwnConstructor( expr ) || ((PsiField)resolve).hasInitializer()) )
        {
          TextRange range = new TextRange( lhs.getTextRange().getStartOffset(),
            lhs.getTextRange().getEndOffset() );
          holder.newAnnotation( HighlightSeverity.ERROR,
            MSG_CANNOT_ASSIGN_READONLY_PROPERTY.get( ((PsiField)resolve).getName() ) )
            .range( range )
            .create();
        }
      }
    }
  }

  private void handle_MSG_CANNOT_ACCESS_WRITEONLY_PROPERTY( PsiElement element, AnnotationHolder holder )
  {
    if( !(element instanceof PsiReferenceExpression) )
    {
      return;
    }
    PsiReferenceExpression expr = (PsiReferenceExpression)element;
    if( !(expr.getParent() instanceof PsiAssignmentExpression) ||
      ((PsiAssignmentExpression)expr.getParent()).getLExpression() != expr )
    {
      PsiElement resolve = expr.resolve();
      if( resolve instanceof PsiField )
      {
        if( isWriteOnlyProperty( expr, (PsiField)resolve, PsiUtil.getTopLevelClass( element ) ) )
        {
          TextRange range = new TextRange( expr.getTextRange().getStartOffset(),
            expr.getTextRange().getEndOffset() );
          holder.newAnnotation( HighlightSeverity.ERROR,
            MSG_CANNOT_ACCESS_WRITEONLY_PROPERTY.get( ((PsiField)resolve).getName() ) )
            .range( range )
            .create();
        }
      }
    }
  }

  private boolean inOwnConstructor( PsiExpression element )
  {
    PsiClass psiClass = ManifoldPsiClassAnnotator.getContainingClass( element );
    PsiMethod m = PsiTreeUtil.getParentOfType( element, PsiMethod.class );
    return m != null && m.isConstructor() && m.getContainingClass() == psiClass;
  }

  private boolean isReadOnlyProperty( PsiExpression place, PsiField field, PsiClass topLevelClass )
  {
    return (isVal( field ) ||
      (field.hasAnnotation( get.class.getTypeName() ) &&
        !field.hasAnnotation( set.class.getTypeName() ) &&
        !isVar( field ))) &&
        (!hasVarTag( field, val.class ) || !keepRefToField( place, field, topLevelClass ));
  }

  private boolean isWriteOnlyProperty( PsiExpression place, PsiField field, @Nullable PsiClass topLevelClass )
  {
    return (hasVarTag( field, set.class ) || field.hasAnnotation( set.class.getTypeName() )) &&
      !isVar( field ) &&
      !field.hasAnnotation( get.class.getTypeName() ) &&
      !isVal( field ) &&
      (!hasVarTag( field, set.class ) || !keepRefToField( place, field, topLevelClass ));
  }

  /**
   * Keep field refs to *inferred* prop fields as-is when they have access to the existing field as it was originally
   * declared. Basically, inferred properties are for the convenience of *consumers* of the declaring class. If the
   * author of the class wants to access stuff inside his implementation using property syntax, he should explicitly
   * declare properties.
   */
  static boolean keepRefToField( PsiElement place, PsiField psiField, PsiClass psiClass )
  {
    PsiClass fieldsClass = psiField.getContainingClass();
    if( fieldsClass == null )
    {
      return false;
    }

    if( place instanceof  PsiExpression && ManPsiResolveHelperImpl.isJailbreakType( ((PsiExpression)place).getType() ) )
    {
      return true;
    }

    PropertyInference.VarTagInfo tag = psiField.getCopyableUserData( VAR_TAG );
    if( tag == null )
    {
      return false;
    }

    int declaredAccess = tag.declaredAccess;
    switch( declaredAccess )
    {
      case PRIVATE:
        // same class as field
        return PsiUtil.getTopLevelClass( psiClass ) == PsiUtil.getTopLevelClass( psiField );
      case 0: // PACKAGE
        // same package as field's class
        return Objects.equals( PsiUtil.getPackageName( psiClass ),
          PsiUtil.getPackageName( fieldsClass ) );
      case PROTECTED:
        // sublcass of field's class
        return psiClass.isInheritor( fieldsClass, true );
      case PUBLIC:
        // field is public, no dice
        return true;
      case -1:
        // indicates no existing field to worry about
        return false;
      default:
        throw new IllegalStateException( "Unknown or invalid access privilege: " + declaredAccess );
    }
  }

  @SuppressWarnings( "BooleanMethodIsAlwaysInverted" )
  private boolean isVar( PsiField field )
  {
    return hasVarTag( field, var.class ) || field.hasAnnotation( var.class.getTypeName() );
  }

  private boolean isVal( PsiField field )
  {
    return hasVarTag( field, val.class ) || field.hasAnnotation( val.class.getTypeName() );
  }

  private boolean hasVarTag( PsiField field, Class<? extends Annotation> varClass )
  {
    PropertyInference.VarTagInfo tag = field.getCopyableUserData( VAR_TAG );
    return tag != null && tag.varClass == varClass;
  }

}

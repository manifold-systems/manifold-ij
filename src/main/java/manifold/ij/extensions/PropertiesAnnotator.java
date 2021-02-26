package manifold.ij.extensions;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.util.PsiTreeUtil;
import manifold.ext.props.rt.api.*;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.rt.api.util.ManStringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

import static manifold.ext.props.PropIssueMsg.*;
import static manifold.ij.extensions.ManPropertiesAugmentProvider.ACCESSOR_TAG;

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
      if( method instanceof PsiMethod && method.getCopyableUserData( ACCESSOR_TAG ) != null )
      {
        TextRange range = new TextRange( element.getTextRange().getStartOffset(),
          element.getTextRange().getEndOffset() );
        holder.newAnnotation( HighlightSeverity.WARNING,
          "Use property identifier '" + derivePropertyName( (PsiMethod)method ) + "' here" )
          .range( range )
          .create();
      }
    }
  }

  private String derivePropertyName( PsiMethod knownAccessor )
  {
    String name = knownAccessor.getName();
    for( String prefix: Arrays.asList( "get", "set", "is" ) )
    {
      if( name.startsWith( prefix ) )
      {
        return ManStringUtil.uncapitalize( name.substring( 3 ) );
      }
    }
    return name; // should throw?
  }

  private void checkProperty( PsiElement element, AnnotationHolder holder )
  {
    if( element instanceof PsiField )
    {
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
        if( isReadOnlyProperty( (PsiField)resolve ) && (!inOwnConstructor( expr ) || ((PsiField)resolve).hasInitializer()) )
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
        if( isWriteOnlyProperty( (PsiField)resolve ) )
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

  private boolean isReadOnlyProperty( PsiField field )
  {
    return field.hasAnnotation( val.class.getTypeName() ) ||
      (field.hasAnnotation( get.class.getTypeName() ) &&
        !field.hasAnnotation( set.class.getTypeName() ) &&
        !field.hasAnnotation( var.class.getTypeName() ));
  }

  private boolean isWriteOnlyProperty( PsiField field )
  {
    return field.hasAnnotation( set.class.getTypeName() ) &&
      !field.hasAnnotation( var.class.getTypeName() ) &&
      !field.hasAnnotation( get.class.getTypeName() ) &&
      !field.hasAnnotation( val.class.getTypeName() );
  }
}

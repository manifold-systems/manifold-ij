package manifold.ij.extensions;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.PsiUtil;
import manifold.api.sourceprod.ISourceProducer;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.util.TypeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


import static manifold.api.sourceprod.ISourceProducer.ProducerKind.Supplemental;

/**
 * Unfortunately IJ doesn't provide a way to augment a type with interfaces, so we are stuck with filtering errors
 */
public class ManHighlightInfoFilter implements HighlightInfoFilter
{
  /**
   * Override to filter errors related to type incompatibilities arising from a
   * manifold extension adding an interface to an existing classpath class (as opposed
   * to a source file).  Basically suppress "incompatible type errors" or similar
   * involving a structural interface extension.
   */
  @Override
  public boolean accept( @NotNull HighlightInfo hi, @Nullable PsiFile file )
  {
    if( hi.getDescription() == null ||
        hi.getSeverity() != HighlightSeverity.ERROR ||
        file == null )
    {
      return true;
    }

    PsiElement firstElem = file.findElementAt( hi.getStartOffset() );
    if( firstElem == null )
    {
      return true;
    }

    PsiElement elem = firstElem.getParent();
    if( elem == null )
    {
      return true;
    }

    if( elem instanceof PsiTypeCastExpression )
    {
      PsiTypeElement castType = ((PsiTypeCastExpression)elem).getCastType();
      if( isStructuralType( castType ) )
      {
        // ignore incompatible cast type involving structure
        return false;
      }
    }
    else if( firstElem instanceof PsiIdentifier )
    {
      if( isStructuralType( findTypeElement( firstElem ) ) )
      {
        // ignore incompatible type in assignment involving structure
        return false;
      }
    }
    else if( hi.getDescription().contains( "cannot be applied to" ) )
    {
      PsiMethodCallExpression methodCall = findMethodCall( firstElem );
      if( methodCall != null )
      {
        PsiMethod psiMethod = methodCall.resolveMethod();
        if( psiMethod != null )
        {
          PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
          for( int i = 0; i < parameters.length; i++ )
          {
            PsiParameter param = parameters[i];
            if( !isStructuralType( param.getTypeElement() ) )
            {
              if( !param.getType().isAssignableFrom( methodCall.getArgumentList().getExpressionTypes()[i] ) )
              {
                return true;
              }
            }
            else
            {
              boolean nominal = false;//typeExtensionNominallyExtends( methodCall.getArgumentList().getExpressionTypes()[i], param.getTypeElement() );
              if( !TypeUtil.isStructurallyAssignable( param.getType(), methodCall.getArgumentList().getExpressionTypes()[i], !nominal ) )
              {
                return true;
              }
            }
          }
          return false;
        }
      }
    }
    return true;
  }

//## todo: implementing this is not efficient to say the least, so for now we will always check for structural assignability
//  private boolean typeExtensionNominallyExtends( PsiType psiType, PsiTypeElement typeElement )
//  {
//    if( !(psiType instanceof PsiClassType) )
//    {
//      return false;
//    }
//
//    PsiClassType rawType = ((PsiClassType)psiType).rawType();
//    rawType.getSuperTypes()
//    ManModule module = ManProject.getModule( typeElement );
//    for( ISourceProducer sp : module.getSourceProducers() )
//    {
//      if( sp.getProducerKind() == Supplemental )
//      {
//
//      }
//    }
//  }

  private int findArgPos( PsiMethodCallExpression methodCall, PsiElement firstElem )
  {
    PsiExpression[] args = methodCall.getArgumentList().getExpressions();
    for( int i = 0; i < args.length; i++ )
    {
      PsiExpression arg = args[i];
      PsiElement csr = firstElem;
      while( csr != null && csr != firstElem )
      {
        csr = csr.getParent();
      }
      if( csr == firstElem )
      {
        return i;
      }
    }
    throw new IllegalStateException();
  }

  private boolean isStructuralType( PsiTypeElement typeElem )
  {
    if( typeElem != null )
    {
      PsiClass psiClass = PsiUtil.resolveClassInType( typeElem.getType() );
      PsiAnnotation structuralAnno = psiClass == null ? null : psiClass.getModifierList().findAnnotation( "manifold.ext.api.Structural" );
      if( structuralAnno != null )
      {
        return true;
      }
    }
    return false;
  }

  private PsiTypeElement findTypeElement( PsiElement elem )
  {
    PsiElement csr = elem;
    while( csr != null && !(csr instanceof PsiTypeElement) )
    {
      csr = csr.getParent();
    }
    return (PsiTypeElement)csr;
  }

  private PsiMethodCallExpression findMethodCall( PsiElement elem )
  {
    PsiElement csr = elem;
    while( csr != null && !(csr instanceof PsiMethodCallExpression) )
    {
      csr = csr.getParent();
    }
    return (PsiMethodCallExpression)csr;
  }
}

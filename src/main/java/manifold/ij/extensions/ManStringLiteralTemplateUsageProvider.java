package manifold.ij.extensions;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;

/**
 * For Manifold's StringLiteral LanguageInjector, prevents "assigned but never accessed" warnings when there is a usage
 * in a String literal via Manifold string interpolation.
 */
public class ManStringLiteralTemplateUsageProvider implements ImplicitUsageProvider
{
  @Override
  public boolean isImplicitUsage( PsiElement element )
  {
    return isReferencedFromManStringLiteral( element );
  }

  @Override
  public boolean isImplicitRead( PsiElement element )
  {
    return isReferencedFromManStringLiteral( element );
  }

  @Override
  public boolean isImplicitWrite( PsiElement element )
  {
    return false;
  }

  private boolean isReferencedFromManStringLiteral( PsiElement elem )
  {
    PsiNamedElement namedElement = findNamedElement( elem );
    if( namedElement != null )
    {
      PsiFile containingFile = elem.getContainingFile();
      if( containingFile instanceof PsiJavaFile )
      {
        return !ReferencesSearch.search( namedElement, new LocalSearchScope( containingFile ) )
                    //!! keep casting as is here for backward compatibility with 2017.x.x IJ
          .forEach( (Processor)e -> isInStringLiteral( ((PsiReference)e).getElement() ) );
      }
    }
    return false;
  }

  private boolean isInStringLiteral( PsiElement element )
  {
    if( element == null )
    {
      return false;
    }
    if( element instanceof PsiField &&
        ManStringLiteralTemplateInjector.FIELD_NAME.equals( ((PsiField)element).getName() ) )
    {
      return true;
    }
    return isInStringLiteral( element.getParent() );
  }

  private PsiNamedElement findNamedElement( PsiElement elem )
  {
    if( elem == null )
    {
      return null;
    }
    int offset = elem.getTextOffset();
    while( elem != null && elem.getTextOffset() == offset && !(elem instanceof PsiNamedElement) )
    {
      elem = elem.getParent();
    }
    return (PsiNamedElement)elem;
  }
}

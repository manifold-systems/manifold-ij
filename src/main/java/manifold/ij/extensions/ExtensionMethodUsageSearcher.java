package manifold.ij.extensions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.search.MethodUsagesSearcher;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.util.Processor;
import manifold.ij.psi.ManLightMethodBuilder;

/**
 * Forward the search to the augmented light method on the extended class
 */
public class ExtensionMethodUsageSearcher extends MethodUsagesSearcher
{
  @Override
  public void processQuery( MethodReferencesSearch.SearchParameters p, Processor<PsiReference> consumer )
  {
    SearchScope searchScope = p.getScopeDeterminedByUser();
    if( !(searchScope instanceof GlobalSearchScope) )
    {
      return;
    }

    PsiMethod method = p.getMethod();
    PsiClass extensionClass = resolveInReadAction( p.getProject(), method::getContainingClass );
    PsiAnnotation extensionAnno = resolveInReadAction( p.getProject(), () -> extensionClass.getModifierList().findAnnotation( "manifold.ext.api.Extension" ) );
    if( extensionAnno == null )
    {
      return;
    }

    PsiMethod augmentedMethod = resolveInReadAction( p.getProject(), () ->
    {
      for( PsiParameter psiParam : method.getParameterList().getParameters() )
      {
        if( psiParam.getModifierList().findAnnotation( "manifold.ext.api.This" ) != null )
        {
          String fqn = extensionClass.getQualifiedName().substring( "extensions.".length() );
          fqn = fqn.substring( 0, fqn.lastIndexOf( '.' ) );
          PsiClass extendedClass = JavaPsiFacade.getInstance( p.getProject() ).findClass( fqn, GlobalSearchScope.allScope( p.getProject() ) );
          if( extendedClass == null )
          {
            continue;
          }
          for( PsiMethod m : extendedClass.findMethodsByName( method.getName(), false ) )
          {
            if( m instanceof ManLightMethodBuilder )
            {
              if( m.getNavigationElement().equals( method.getNavigationElement() ) )
              {
                return m;
              }
            }
          }
        }
      }
      return null;
    } );
    if( augmentedMethod != null )
    {
      MethodReferencesSearch.SearchParameters searchParams = new MethodReferencesSearch.SearchParameters( augmentedMethod, searchScope, p.isStrictSignatureSearch(), p.getOptimizer() );
      super.processQuery( searchParams, consumer );
    }
  }

  static <T> T resolveInReadAction( Project p, Computable<T> computable )
  {
    return ApplicationManager.getApplication().isReadAccessAllowed() ? computable.compute() : DumbService.getInstance( p ).runReadActionInSmartMode( computable );
  }
}

package manifold.ij.extensions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.search.MethodUsagesSearcher;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.util.Processor;
import manifold.ext.ExtensionManifold;
import manifold.ext.api.Extension;
import manifold.ext.api.This;
import manifold.ij.psi.ManLightMethodBuilder;
import manifold.util.ReflectUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Forward the search to the augmented light method on the extended class
 */
public class ExtensionMethodUsageSearcher extends MethodUsagesSearcher
{
  @Override
  public void processQuery( @NotNull final MethodReferencesSearch.SearchParameters p, @NotNull final Processor<? super PsiReference> consumer )
  {
    SearchScope searchScope = p.getScopeDeterminedByUser();
    if( !(searchScope instanceof GlobalSearchScope) )
    {
      return;
    }

    if( searchScope.getClass().getSimpleName().equals( "ModuleWithDependentsScope" ) )
    {
      // include libraries to handle extended classes
      searchScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(
        (Module)ReflectUtil.field( searchScope, "myModule" ).get(),
        ApplicationManager.getApplication().isUnitTestMode() );
    }
    GlobalSearchScope theSearchScope = (GlobalSearchScope)searchScope;

    PsiMethod method = p.getMethod();
    PsiClass extensionClass = resolveInReadAction( p.getProject(), method::getContainingClass );
    PsiAnnotation extensionAnno = resolveInReadAction( p.getProject(), () ->
      {
        PsiModifierList modifierList = extensionClass.getModifierList();
        return modifierList == null ? null : modifierList.findAnnotation( Extension.class.getName() );
      } );
    if( extensionAnno == null )
    {
      return;
    }

    PsiMethod augmentedMethod = resolveInReadAction( p.getProject(), () ->
    {
      if( method.getModifierList().findAnnotation( Extension.class.getName() ) != null )
      {
        String fqn = getExtendedFqn( extensionClass );
        PsiClass extendedClass = JavaPsiFacade.getInstance( p.getProject() ).findClass( fqn, theSearchScope );

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

      for( PsiParameter psiParam : method.getParameterList().getParameters() )
      {
        if( psiParam.getModifierList().findAnnotation( This.class.getName() ) != null )
        {
          String fqn = getExtendedFqn( extensionClass );
          PsiClass extendedClass = JavaPsiFacade.getInstance( p.getProject() ).findClass( fqn, getTargetScope( theSearchScope, method ) );
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

  private GlobalSearchScope getTargetScope( GlobalSearchScope searchScope, PsiMethod method )
  {
    if( searchScope instanceof ModuleWithDependenciesScope && searchScope.isSearchInLibraries() )
    {
      return searchScope;
    }

    Module localModule = ModuleUtil.findModuleForPsiElement( method );
    return localModule != null
           ? GlobalSearchScope.moduleWithDependenciesAndLibrariesScope( localModule )
           : GlobalSearchScope.allScope( method.getProject() );
  }

  @NotNull
  private String getExtendedFqn( PsiClass extensionClass )
  {
    String fqn = extensionClass.getQualifiedName();
    int iExt = fqn.indexOf( ExtensionManifold.EXTENSIONS_PACKAGE + '.' );
    fqn = fqn.substring( iExt + ExtensionManifold.EXTENSIONS_PACKAGE.length() + 1 );
    fqn = fqn.substring( 0, fqn.lastIndexOf( '.' ) );
    return fqn;
  }

  static <T> T resolveInReadAction( Project p, Computable<T> computable )
  {
    return ApplicationManager.getApplication().isReadAccessAllowed() ? computable.compute() : DumbService.getInstance( p ).runReadActionInSmartMode( computable );
  }
}

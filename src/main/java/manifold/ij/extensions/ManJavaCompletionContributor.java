package manifold.ij.extensions;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResult;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.module.Module;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Consumer;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.psi.ManLightFieldBuilder;
import manifold.ij.psi.ManLightMethodBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * Filters out extension methods not accessible from the call-site.
 * Also changes icons for properties.
 */
public class ManJavaCompletionContributor extends CompletionContributor
{
  @Override
  public void fillCompletionVariants( @NotNull CompletionParameters parameters, @NotNull CompletionResultSet result )
  {
    if( !ManProject.isManifoldInUse( parameters.getOriginalFile() ) )
    {
      // Manifold jars are not used in the project
      return;
    }

    result.runRemainingContributors( parameters, new MyConsumer( parameters, result ) );
    result.stopHere();
  }

  static class MyConsumer implements Consumer<CompletionResult>
  {
    private final CompletionResultSet _result;
    private final Module _module;

    MyConsumer( CompletionParameters parameters, CompletionResultSet result )
    {
      _result = result;
      _module = findModule( parameters );
    }

    private Module findModule( CompletionParameters parameters )
    {
      PsiElement position = parameters.getPosition();
      Module module = ModuleUtil.findModuleForPsiElement( position.getParent() );
      if( module == null )
      {
        module = ModuleUtil.findModuleForPsiElement( position );
      }
      return module;
    }

    @Override
    public void consume( CompletionResult completionResult )
    {
      LookupElement lookupElement = completionResult.getLookupElement();
      if( !exclude( lookupElement ) )
      {
        _result.passResult( completionResult );
      }
    }

    private boolean exclude( LookupElement lookupElement )
    {
      if( _module == null )
      {
        return false;
      }

      PsiElement psiElem = lookupElement.getPsiElement();
      if( psiElem instanceof ManLightMethodBuilder )
      {
        ManModule module = ManProject.getModule( _module );
        if( module != null && !module.isExtEnabled() )
        {
          // module not using manifold-ext-rt
          return true;
        }

        return ((ManLightMethodBuilder)psiElem).getModules().stream()
          .map( ManModule::getIjModule )
          .noneMatch( methodModule -> GlobalSearchScope.moduleWithDependenciesAndLibrariesScope( _module ).isSearchInModuleContent( methodModule ) );
      }

      //todo: if property is on extension method, filter based on that
      if( psiElem instanceof ManLightFieldBuilder )
      {
        ManModule module = ManProject.getModule( _module );
        if( module != null && !module.isPropertiesEnabled() )
        {
          // module not using manifold-props
          return true;
        }
      }

      return false;
    }
  }
}

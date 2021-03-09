package manifold.ij.extensions;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResult;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupElementRenderer;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.module.Module;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Consumer;
import manifold.ext.props.rt.api.get;
import manifold.ext.props.rt.api.set;
import manifold.ext.props.rt.api.val;
import manifold.ext.props.rt.api.var;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.psi.ManLightMethodBuilder;
import manifold.util.ReflectUtil;
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

  class MyConsumer implements Consumer<CompletionResult>
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
        replaceIconsForProperties( completionResult, lookupElement );

        _result.passResult( completionResult );
      }
    }

    private void replaceIconsForProperties( CompletionResult completionResult, LookupElement lookupElement )
    {
      PsiElement psiElem = lookupElement.getPsiElement();
      if( psiElem instanceof PsiField )
      {
        final PsiField field = (PsiField)psiElem;
        if( PropertyInference.isPropertyField( field ) )
        {
          LookupElementDecorator<LookupElement> v = LookupElementDecorator.withRenderer( lookupElement, new LookupElementRenderer<>()
          {
            @Override
            public void renderElement( LookupElementDecorator<LookupElement> element, LookupElementPresentation presentation )
            {
              element.getDelegate().renderElement( presentation );
              PsiModifierList modifierList = field.getModifierList();
              boolean isStatic = modifierList != null && modifierList.hasExplicitModifier( PsiModifier.STATIC );
              if( isReadOnlyProperty( field ) )
              {
                presentation.setIcon( isStatic ? AllIcons.Nodes.PropertyReadStatic : AllIcons.Nodes.PropertyRead );
              }
              else if( isWriteOnlyProperty( field ) )
              {
                presentation.setIcon( isStatic ? AllIcons.Nodes.PropertyWriteStatic : AllIcons.Nodes.PropertyWrite );
              }
              else
              {
                presentation.setIcon( isStatic ? AllIcons.Nodes.PropertyReadWriteStatic : AllIcons.Nodes.PropertyReadWrite );
              }
            }
          } );
          ReflectUtil.field( completionResult, "myLookupElement" ).set( v );
        }
      }
    }

    private boolean isReadOnlyProperty( PsiField field )
    {
      PropertyInference.VarTagInfo varTagInfo = field.getCopyableUserData( PropertyInference.VAR_TAG );
      if( varTagInfo != null )
      {
        return varTagInfo.varClass == val.class;
      }

      return field.getAnnotation( val.class.getTypeName() ) != null ||
        (field.getAnnotation( var.class.getTypeName() ) == null &&
          field.getAnnotation( get.class.getTypeName() ) != null);
    }

    private boolean isWriteOnlyProperty( PsiField field )
    {
      PropertyInference.VarTagInfo varTagInfo = field.getCopyableUserData( PropertyInference.VAR_TAG );
      if( varTagInfo != null )
      {
        return varTagInfo.varClass == set.class;
      }

      return field.getAnnotation( var.class.getTypeName() ) == null &&
          field.getAnnotation( set.class.getTypeName() ) != null;
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
        return ((ManLightMethodBuilder)psiElem).getModules().stream()
          .map( ManModule::getIjModule )
          .noneMatch( methodModule -> GlobalSearchScope.moduleWithDependenciesAndLibrariesScope( _module ).isSearchInModuleContent( methodModule ) );
      }
      return false;
    }
  }
}

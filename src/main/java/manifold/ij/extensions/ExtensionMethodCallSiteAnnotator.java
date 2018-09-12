package manifold.ij.extensions;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.search.GlobalSearchScope;
import manifold.ij.psi.ManLightMethodBuilder;
import org.jetbrains.annotations.NotNull;

/**
 *  Filters extension methods where the method is from a module not accessible from the call-site
 */
public class ExtensionMethodCallSiteAnnotator implements Annotator
{
  @Override
  public void annotate( @NotNull PsiElement element, @NotNull AnnotationHolder holder )
  {
    if( element instanceof PsiMethodCallExpression )
    {
      PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)element).getMethodExpression();
      PsiElement member = methodExpression.resolve();
      if( member instanceof ManLightMethodBuilder )
      {
        Module callSiteModule = ModuleUtil.findModuleForPsiElement( element );
        if( callSiteModule != null )
        {
          Module extensionModule = ((ManLightMethodBuilder)member).getModule().getIjModule();
          GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope( callSiteModule );
          if( !scope.isSearchInModuleContent( extensionModule ) )
          {
            PsiElement methodElem = methodExpression.getReferenceNameElement();
            if( methodElem != null )
            {
              // The extension method is from a module not accessible from the call-site
              TextRange range = new TextRange( methodElem.getTextRange().getStartOffset(), methodElem.getTextRange().getEndOffset() );
              holder.createErrorAnnotation( range, JavaErrorMessages.message( "cannot.resolve.method", methodExpression.getReferenceName() ) );
            }
          }
        }
      }
    }
  }
}

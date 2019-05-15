package manifold.ij.extensions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.refactoring.rename.PreferrableNameSuggestionProvider;
import java.util.Set;
import manifold.ij.core.ManProject;
import org.jetbrains.annotations.Nullable;

/**
 * A hack to fix an issue with JS GraphQL plugin where the GraphQLVariable PSI (for a variable def e.g., $title) returns
 * null instead of "$title" when {@code element.getName()} is called.
 */
public class ManNameSuggestionProvider extends PreferrableNameSuggestionProvider
{
  @Nullable
  @Override
  public SuggestedNameInfo getSuggestedNames( PsiElement element, @Nullable PsiElement nameSuggestionContext, Set<String> result )
  {
    if( !ManProject.isManifoldInUse( element ) )
    {
      // Manifold jars are not used in the project
      return null;
    }

    if( element instanceof PsiNamedElement &&
        ((PsiNamedElement)element).getName() == null &&
        element.getClass().getTypeName().contains( "GraphQLVariable" ) )
    {
      String text = element.getText();
      if( text != null && text.startsWith( "$" ) )
      {
        result.clear();
        result.add( text );
      }
    }
    return null;
  }
}

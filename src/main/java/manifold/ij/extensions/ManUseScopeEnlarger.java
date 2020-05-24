package manifold.ij.extensions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UseScopeEnlarger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Expands use-scope for elements in resource files that are represented as types via Manifold. For instance, a JSON
 * file have a "Foo" JSON property has a use-scope matching the "Foo" property of the Manifold-generated Java type. In
 * essence, the scope potentially enlarges to include modules dependent on the module enclosing the resource file.
 */
public class ManUseScopeEnlarger extends UseScopeEnlarger
{
  private static final ThreadLocal<Boolean> SHORT_CIRCUIT = ThreadLocal.withInitial( () -> false );

  @Nullable
  @Override
  public SearchScope getAdditionalUseScope( @NotNull PsiElement element )
  {
    if( SHORT_CIRCUIT.get() )
    {
      return null;
    }
    SHORT_CIRCUIT.set( true );
    try
    {
      if( !(element instanceof PsiModifierListOwner) )
      {
        Set<PsiModifierListOwner> javaElements = ResourceToManifoldUtil.findJavaElementsFor( element );
        if( !javaElements.isEmpty() )
        {
          return javaElements.iterator().next().getUseScope();
        }
      }
    }
    finally
    {
      SHORT_CIRCUIT.set( false );
    }
    return null;
  }
}

package manifold.ij.extensions;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.impl.JavaClassSupersImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.JavaClassSupers;
import manifold.ij.core.ManProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ManJavaClassSupersImpl extends JavaClassSupers
{
  private static final Key<CachedValue<Map<String, PsiSubstitutor>>> KEY_CACHED_TYPE_ALIAS_SUBSTITUTES = new Key<>("KEY_CACHED_TYPE_ALIAS_SUBSTITUTES");
  private final JavaClassSupersImpl impl = new JavaClassSupersImpl();

  @Nullable
  @Override
  public PsiSubstitutor getSuperClassSubstitutor( @NotNull PsiClass superClass, @NotNull PsiClass derivedClass, @NotNull GlobalSearchScope resolveScope, @NotNull PsiSubstitutor derivedSubstitutor )
  {
    // for performance reasons, check only if non-inherited.
    PsiSubstitutor substitutor = impl.getSuperClassSubstitutor( superClass, derivedClass, resolveScope, derivedSubstitutor );
    if( substitutor == null )
    {
      substitutor = getAliasedClassSubstitutor( superClass, derivedClass );
    }
    return substitutor;
  }

  @Override
  public void reportHierarchyInconsistency( @NotNull PsiClass superClass, @NotNull PsiClass derivedClass )
  {
    impl.reportHierarchyInconsistency( superClass, derivedClass );
  }

  private PsiSubstitutor getAliasedClassSubstitutor( @NotNull PsiClass superClass, @NotNull PsiClass derivedClass )
  {
    if( !ManProject.isTypeAliasEnabledInAnyModules( derivedClass ) )
    {
      // Manifold jars are not used in the project
      return null;
    }
    // AliasedType = OriginType
    PsiSubstitutor substitutor = getAliasedClassSubstitutor0( superClass, derivedClass );
    if( substitutor != null )
    {
      return substitutor;
    }
    // OriginType = AliasedType
    return getAliasedClassSubstitutor0( derivedClass, superClass );
  }

  private PsiSubstitutor getAliasedClassSubstitutor0( @NotNull PsiClass superClass, @NotNull PsiClass derivedClass )
  {
    return CachedValuesManager.getCachedValue( superClass, KEY_CACHED_TYPE_ALIAS_SUBSTITUTES, new MyCachedValueProvider( superClass )  ).get( derivedClass.getQualifiedName() );
  }

  private static class MyCachedValueProvider implements CachedValueProvider<Map<String, PsiSubstitutor>>
  {
    private final SmartPsiElementPointer<PsiClass> _psiClassPointer;

    public MyCachedValueProvider( PsiClass psiClass )
    {
      _psiClassPointer = SmartPointerManager.createPointer( psiClass );
    }

    @Nullable
    @Override
    public Result<Map<String, PsiSubstitutor>> compute()
    {
      HashMap<String, PsiSubstitutor> substitutes = new HashMap<>();
      PsiClass psiClass = _psiClassPointer.getElement();
      if( psiClass == null )
      {
        return Result.create( substitutes );
      }
      Set<PsiElement> dependencies = new LinkedHashSet<>();
      PsiClass newPsiClass = TypeAliasMaker.getAliasedType( psiClass );
      if( newPsiClass != null )
      {
        substitutes.put( newPsiClass.getQualifiedName(), PsiSubstitutor.EMPTY );
        dependencies.add( TypeAliasMaker.getAnnotation( psiClass ) );
      }
      dependencies.add( psiClass );
      return Result.create( substitutes, dependencies.toArray() );
    }

    @Override
    public int hashCode()
    {
      return Objects.hashCode( _psiClassPointer.getElement() );
    }

    @Override
    public boolean equals( Object obj )
    {
      if( obj instanceof MyCachedValueProvider other)
      {
        return Objects.equals( other._psiClassPointer.getElement(), _psiClassPointer.getElement() );
      }
      return false;
    }
  }
}

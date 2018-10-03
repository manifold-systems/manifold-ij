package manifold.ij.extensions;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.file.PsiPackageImpl;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import manifold.api.type.ITypeManifold;
import manifold.api.type.TypeName;
import manifold.ij.core.ManModule;
import org.jetbrains.annotations.NotNull;

/**
 */
public class NonDirectoryPackage extends PsiPackageImpl
{
  NonDirectoryPackage( PsiManager manager, String qualifiedName )
  {
    super( manager, qualifiedName );
  }

  @Override
  public PsiElement copy()
  {
    return new NonDirectoryPackage( getManager(), getQualifiedName() );
  }

  @Override
  public boolean isValid()
  {
    return true;
  }

  @NotNull
  @Override
  public PsiClass[] getClasses()
  {
    return getClasses( allScope() );
  }

  @NotNull
  @Override
  public PsiClass[] getClasses( @NotNull GlobalSearchScope scope )
  {
    PsiClass[] fromSuper = super.getClasses( scope );
    Map<String, PsiClass> all = new LinkedHashMap<>();
    Arrays.stream( fromSuper ).forEach( cls -> all.put( cls.getQualifiedName(), cls ) );
    List<ManModule> modules = ManTypeFinder.findModules( scope );
    for( ManModule module: modules )
    {
      for( ITypeManifold tm: module.getTypeManifolds() )
      {
        Collection<TypeName> typeNames = tm.getTypeNames( getQualifiedName() );
        for( TypeName typeName: typeNames )
        {
          if( typeName.kind != TypeName.Kind.NAMESPACE )
          {
            PsiClass psiClass = ManifoldPsiClassCache.getPsiClass( module, typeName.name );
            if( psiClass != null )
            {
              all.put( typeName.name, psiClass );
            }
          }
        }
      }
    }
    return all.values().toArray( new PsiClass[0] );
  }
}
package manifold.ij.extensions;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.HierarchicalMethodSignature;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.util.Processor;
import manifold.ij.core.ManProject;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ManMethodSuperSearcher extends QueryExecutorBase<MethodSignatureBackedByPsiMethod, SuperMethodsSearch.SearchParameters>
{
  @Override
  public void processQuery( @NotNull SuperMethodsSearch.SearchParameters queryParameters, @NotNull Processor<? super MethodSignatureBackedByPsiMethod> consumer )
  {
    PsiMethod method = queryParameters.getMethod();
    if( !ManProject.isTypeAliasEnabledInAnyModules( method ) )
    {
      return;
    }
    HierarchicalMethodSignature signature = method.getHierarchicalMethodSignature();
    List<HierarchicalMethodSignature> supers = signature.getSuperSignatures();
    if( !supers.isEmpty() )
    {
      return;
    }
    for( HierarchicalMethodSignature superSignature : getSameNameSuperSignatures( method, signature ) )
    {
      if( MethodSignatureUtil.isSubsignature( superSignature, resolve( superSignature, signature ) ) )
      {
        consumer.process( superSignature );
      }
    }
  }

  private static MethodSignature resolve( @NotNull MethodSignature superSignature, @NotNull MethodSignature subSignature )
  {
    // resolve parameter type
    PsiType[] oldParameterTypes = subSignature.getParameterTypes();
    PsiType[] superParameterTypes = superSignature.getParameterTypes();
    PsiType[] newParameterTypes = oldParameterTypes;
    for( int i = 0; i < newParameterTypes.length; ++i )
    {
      if( newParameterTypes[i].isAssignableFrom(superParameterTypes[i]) )
      {
        if( newParameterTypes == oldParameterTypes )
        {
          newParameterTypes = oldParameterTypes.clone();
        }
        newParameterTypes[i] = superParameterTypes[i];
      }
    }
    // resolve type parameter.
    PsiTypeParameter[] oldTypeParameterList = subSignature.getTypeParameters();
    PsiTypeParameter[] superTypeParameterList = superSignature.getTypeParameters();
    PsiTypeParameter[] newTypeParameterList = oldTypeParameterList;
    for( int i = 0; i < newTypeParameterList.length; ++i )
    {
      if( newTypeParameterList[i].isInheritor( superTypeParameterList[i], false ) )
      {
        if( newTypeParameterList == oldTypeParameterList )
        {
          newTypeParameterList = oldTypeParameterList.clone();
        }
        newTypeParameterList[i] = superTypeParameterList[i];
      }
    }
    if( newParameterTypes == oldParameterTypes && newTypeParameterList == oldTypeParameterList )
    {
      return subSignature;
    }
    String name = subSignature.getName();
    PsiSubstitutor substitutor = subSignature.getSubstitutor();
    boolean isConstructor = subSignature.isConstructor();
    return MethodSignatureUtil.createMethodSignature( name, newParameterTypes, newTypeParameterList, substitutor, isConstructor );
  }

  private static List<HierarchicalMethodSignature> getSameNameSuperSignatures( PsiMethod method, HierarchicalMethodSignature signature )
  {
    if( !(method.getParent() instanceof PsiClass psiClass) )
    {
      return Collections.emptyList();
    }
    ArrayList<HierarchicalMethodSignature> methodSignatures = new ArrayList<>();
    int parameterCount = signature.getParameterTypes().length;
    int typeParameterCount = signature.getTypeParameters().length;
    for( PsiClass psiSuperClass : psiClass.getSupers() )
    {
      for( PsiMethod psiSuperMethod : psiSuperClass.findMethodsByName( method.getName(), true ) )
      {
        HierarchicalMethodSignature superSignature = psiSuperMethod.getHierarchicalMethodSignature();
        if( parameterCount == superSignature.getParameterTypes().length && typeParameterCount == superSignature.getTypeParameters().length )
        {
          methodSignatures.add( superSignature );
        }
      }
    }
    return methodSignatures;
  }
}

package manifold.ij.extensions;

import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.PsiResolveHelperImpl;
import com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import manifold.ext.props.rt.api.*;
import manifold.ext.rt.api.Jailbreak;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.psi.ManLightModifierListImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ManPsiResolveHelperImpl extends PsiResolveHelperImpl
{
  public ManPsiResolveHelperImpl( Project project )
  {
    super( project );
  }

  @Override
  public boolean isAccessible( @NotNull PsiMember member,
                               @Nullable PsiModifierList modifierList,
                               @NotNull PsiElement place,
                               @Nullable PsiClass accessObjectClass,
                               @Nullable PsiElement currentFileResolveScope )
  {
    boolean isCompletionContext = place.getUserData( CompletionContext.COMPLETION_CONTEXT_KEY ) != null;

    Boolean propertyAccessible = handleProperties( member, place, accessObjectClass, currentFileResolveScope, isCompletionContext );
    if( propertyAccessible != null )
    {
      return propertyAccessible;
    }

    boolean accessible = super.isAccessible( member, modifierList, place, accessObjectClass, currentFileResolveScope );

    if( !ManProject.isManifoldInUse( member ) )
    {
      // Manifold jars are not used in the project
      return accessible;
    }

    if( accessible )
    {
      return true;
    }

    PsiType type = null;
    if( place instanceof PsiExpression )
    {
      type = ((PsiExpression)place).getType();
    }
    else if( place.getContext() instanceof PsiTypeElement )
    {
      type = ((PsiTypeElement)place.getContext()).getType();
    }
    else if( place.getContext() instanceof PsiNewExpression )
    {
      type = ((PsiNewExpression)place.getContext()).getType();
    }

    accessible = isJailbreakType( type );

    if( !accessible )
    {
      // for code completion candidates members...
      if( isCompletionContext )
      {
        if( place.getContext() instanceof PsiReferenceExpression )
        {
          PsiReferenceExpression context = (PsiReferenceExpression)place.getContext();
          if( context.getQualifier() instanceof PsiReferenceExpression )
          {
            // for completion on type annotated with @Jailbreak
            type = ((PsiReferenceExpression)context.getQualifier()).getType();
            accessible = isJailbreakType( type );
          }
          else if( context.getQualifierExpression() instanceof PsiMethodCallExpressionImpl )
          {
            // for completion from jailbreak() method
            type = ((PsiMethodCallExpressionImpl)context.getQualifierExpression()).getMethodExpression().getType();
            accessible = isJailbreakType( type );
          }
        }
      }
    }
    return accessible;
  }

  @Nullable
  public Boolean handleProperties( @NotNull PsiMember member, @NotNull PsiElement place, @Nullable PsiClass accessObjectClass, @Nullable PsiElement currentFileResolveScope, boolean isCompletionContext )
  {
    ManModule module = ManProject.getModule( member );
    if( module != null && !module.isPropertiesEnabled() )
    {
      // project/module not using properties
      return null;
    }

    Boolean res = null;
    if( isCompletionContext )
    {
      res = handleClsPropertyAccessCompletion( member, place, accessObjectClass, currentFileResolveScope );
      if( res != null )
      {
        return res;
      }

      return handlePsiPropertyAccessCompletion( member, place, accessObjectClass, currentFileResolveScope );
    }
    else
    {
      res = handleClsPropertyAccess( member, place, accessObjectClass, currentFileResolveScope );
      if( res != null )
      {
        return res;
      }

      return handlePsiPropertyAccess( member, place, accessObjectClass, currentFileResolveScope );
    }
  }

  private Boolean handleClsPropertyAccess( PsiMember member, PsiElement place, PsiClass accessObjectClass,
                                           PsiElement currentFileResolveScope )
  {
    if( !(member instanceof PsiField) )
    {
      return null;
    }

    // all class files tag property fields and accessors with @propgen
    PsiAnnotation propgenAnno = member.getAnnotation( propgen.class.getTypeName() );
    if( propgenAnno == null )
    {
      // not an explicit property
      return null;
    }


    long flags = (long)((PsiLiteralValue)propgenAnno.findAttributeValue( "flags" )).getValue();
    List<String> modifiers = new ArrayList<>();
    for( ModifierMap modifier : ModifierMap.values() )
    {
      if( (flags & modifier.getMod()) != 0 )
      {
        modifiers.add( modifier.getName() );
      }
    }
    ManLightModifierListImpl modifierList = new ManLightModifierListImpl(
      place.getManager(), JavaLanguage.INSTANCE, modifiers.toArray( new String[0] ) );
    return super.isAccessible( member, modifierList, place, accessObjectClass, currentFileResolveScope );
  }

  private Boolean handlePsiPropertyAccess( PsiMember member, PsiElement place, PsiClass accessObjectClass,
                                           PsiElement currentFileResolveScope )
  {
    if( !(member instanceof PsiField) )
    {
      return null;
    }

    PsiField field = (PsiField)member;

    if( isVarTagAccessible( field, place, accessObjectClass, currentFileResolveScope ) )
    {
      return true;
    }

    if( !PropertyInference.isPropertyField( field ) )
    {
      return null;
    }

    PsiModifierList modifierList = field.getModifierList();
    if( modifierList != null && modifierList.hasModifierProperty( PsiModifier.PACKAGE_LOCAL ) )
    {
      // properties are PUBLIC by default
      return true;
    }
    return null;
  }

  private Boolean handleClsPropertyAccessCompletion( PsiMember member, PsiElement place, PsiClass accessObjectClass,
                                                     PsiElement currentFileResolveScope )
  {
    // all class files tag property fields and accessors with @propgen

    PsiAnnotation propgenAnno = member.getAnnotation( propgen.class.getTypeName() );
    if( propgenAnno == null )
    {
      return null;
    }

    if( member instanceof PsiMethod )
    {
      // getters/setters should not be available in completion
      return false;
    }

    if( !(member instanceof PsiField) )
    {
      return null;
    }

    long flags = (long)((PsiLiteralValue)propgenAnno.findAttributeValue( "flags" )).getValue();
    List<String> modifiers = new ArrayList<>();
    for( ModifierMap modifier : ModifierMap.values() )
    {
      if( (flags & modifier.getMod()) != 0 )
      {
        modifiers.add( modifier.getName() );
      }
    }
    ManLightModifierListImpl modifierList = new ManLightModifierListImpl(
      place.getManager(), JavaLanguage.INSTANCE, modifiers.toArray( new String[0] ) );
    return super.isAccessible( member, modifierList, place, accessObjectClass, currentFileResolveScope );
  }

  private Boolean handlePsiPropertyAccessCompletion( PsiMember member, PsiElement place, PsiClass accessObjectClass,
                                                     PsiElement currentFileResolveScope )
  {
    if( member instanceof PsiMethod )
    {
      if( isOrOverridesAccessor( (PsiMethod)member ) )
      {
        // getters/setters corresponding with properties should not be available in completion, use property identifier
        return false;
      }
    }

    if( !(member instanceof PsiField) )
    {
      return null;
    }

    if( isVarTagAccessible( (PsiField)member, place, accessObjectClass, currentFileResolveScope ) )
    {
      return true;
    }

    PsiModifierList modifierList1 = member.getModifierList();
    if( modifierList1 != null && modifierList1.hasModifierProperty( PsiModifier.PACKAGE_LOCAL ) )
    {
      // properties are PUBLIC by default
      return true;
    }
    return null;
  }

  @Nullable
  private boolean isOrOverridesAccessor( PsiMethod method )
  {
    if( isPropertyAccessor( method ) )
    {
      // getters/setters corresponding with properties should not be available in completion
      return true;
    }

    // check if accessor override
    if( isPotentialAccesor( method ) )
    {
      MethodSignatureBackedByPsiMethod match = SuperMethodsSearch.search( method, null, true, false ).findFirst();
      if( match != null )
      {
        method = match.getMethod();
        if( isOrOverridesAccessor( method ) )
        {
          // getters/setters corresponding with properties should not be available in completion
          method.putCopyableUserData( ManPropertiesAugmentProvider.ACCESSOR_TAG, true ); // mark with tag to avoid super searching again
          return true;
        }
      }
    }
    return false;
  }

  private boolean isVarTagAccessible( PsiField field, PsiElement place, PsiClass accessObjectClass, PsiElement currentFileResolveScope )
  {
    PropertyInference.VarTagInfo tag = field.getCopyableUserData( PropertyInference.VAR_TAG );
    if( tag != null && tag.weakestAccess >= 0 )
    {
      List<String> modifiers = new ArrayList<>();
      for( ModifierMap modifier : ModifierMap.values() )
      {
        if( (tag.weakestAccess & modifier.getMod()) != 0 )
        {
          modifiers.add( modifier.getName() );
        }
      }
      ManLightModifierListImpl modifierList = new ManLightModifierListImpl(
        place.getManager(), JavaLanguage.INSTANCE, modifiers.toArray( new String[0] ) );
      return super.isAccessible( field, modifierList, place, accessObjectClass, currentFileResolveScope );
    }
    return false;
  }

  private boolean isPotentialAccesor( PsiMethod m )
  {
    String name = m.getName();
    return name.startsWith( "get" ) || name.startsWith( "is" ) || name.startsWith( "set" );
  }

  private boolean isPropertyAccessor( PsiMember member )
  {
    return member.getCopyableUserData( ManPropertiesAugmentProvider.ACCESSOR_TAG ) != null;
  }

  @Override
  @NotNull
  public JavaResolveResult[] multiResolveConstructor( @NotNull PsiClassType type, @NotNull PsiExpressionList argumentList, @NotNull PsiElement place )
  {
    JavaResolveResult[] results = super.multiResolveConstructor( type, argumentList, place );

    if( !ManProject.isManifoldInUse( place ) )
    {
      // Manifold jars are not used in the project
      return results;
    }

    for( JavaResolveResult result : results )
    {
      if( result instanceof CandidateInfo )
      {
        if( place instanceof PsiNewExpression )
        {
          PsiType t = ((PsiNewExpression)place).getType();
          if( isJailbreakType( t ) )
          {
            @Jailbreak CandidateInfo info = (CandidateInfo)result;
            info.myAccessible = true;
          }
        }
        else if( place.getContext() instanceof PsiNewExpression )
        {
          PsiType t = ((PsiNewExpression)place.getContext()).getType();
          if( isJailbreakType( t ) )
          {
            @Jailbreak CandidateInfo info = (CandidateInfo)result;
            info.myAccessible = true;
          }
        }
      }
    }
    return results;
  }

  private boolean isJailbreakType( PsiType type )
  {
    return type != null && type.findAnnotation( Jailbreak.class.getTypeName() ) != null;
  }
}

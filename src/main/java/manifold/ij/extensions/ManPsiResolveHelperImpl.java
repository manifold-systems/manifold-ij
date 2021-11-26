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

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

import static manifold.ij.extensions.PropertyInference.*;

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
    if( !ManProject.isManifoldInUse( place ) )
    {
      // Manifold jars are not used in the project
      return null;
    }

    ManModule module = ManProject.getModule( place );
    if( module != null && !module.isPropertiesEnabled() )
    {
      // project/module not using properties
      return null;
    }

    member = updateFieldHack( member );

    Boolean res;
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

    Boolean varTagAccessible = isVarTagAccessible( field, place, accessObjectClass, currentFileResolveScope );
    if( varTagAccessible != null )
    {
      return varTagAccessible;
    }

    if( field.getCopyableUserData( PropertyInference.VAR_TAG ) != null )
    {
      // inferred props are not public by default
      return null;
    }

    if( !PropertyInference.isPropertyField( field ) )
    {
      return null;
    }

    PsiModifierList modifierList = field.getModifierList();
    if( modifierList != null )
    {
      Boolean accessible = isGetSetAccessible( modifierList, member, place, accessObjectClass, currentFileResolveScope );
      if( accessible != null )
      {
        return accessible;
      }
      if( modifierList.hasModifierProperty( PsiModifier.PACKAGE_LOCAL ) ||
          (!modifierList.hasModifierProperty( PsiModifier.PUBLIC ) &&
            !modifierList.hasModifierProperty( PsiModifier.PROTECTED ) &&
            !modifierList.hasModifierProperty( PsiModifier.PRIVATE )) )
      {
        // explicitly declared properties are PUBLIC by default (inferred properties are not)
        return true;
      }
    }
    return null;
  }

  /**
   * This is a hack to get the latest version of a field, otherwise we sometimes
   * get an incomplete one e.g., missing VAR_TAG
   */
  @NotNull
  private PsiMember updateFieldHack( @NotNull PsiMember member )
  {
    if( !(member instanceof PsiField) )
    {
      return member;
    }

    for( PsiField f : member.getContainingClass().getFields() )
    {
      if( f.getName().equals( member.getName() ) )
      {
        // update the field
        member = f;
        break;
      }
    }

    return member;
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

    Boolean varTagAccessible = isVarTagAccessible( (PsiField)member, place, accessObjectClass, currentFileResolveScope );
    if( varTagAccessible != null )
    {
      return varTagAccessible;
    }

    if( member.getCopyableUserData( PropertyInference.VAR_TAG ) != null )
    {
      // inferred props are not public by default
      return null;
    }

    if( !PropertyInference.isPropertyField( (PsiField)member ) )
    {
      return null;
    }

    PsiModifierList modifierList = member.getModifierList();
    if( modifierList != null &&
      !modifierList.hasModifierProperty( PsiModifier.PUBLIC ) &&
      !modifierList.hasModifierProperty( PsiModifier.PROTECTED ) &&
      !modifierList.hasModifierProperty( PsiModifier.PRIVATE ) )
    {
      // explicitly declared properties are PUBLIC by default (inferred properties are not)
      return true;
    }
    return null;
  }

  private Boolean isGetSetAccessible( PsiModifierList modifierList, PsiMember member, PsiElement place,
                                      PsiClass accessObjectClass, PsiElement currentFileResolveScope )
  {
    Class<? extends Annotation> annoClass =
      place.getParent() instanceof PsiAssignmentExpression ? set.class : get.class;

    PsiAnnotation annotation = modifierList.findAnnotation( annoClass.getTypeName() );
    if( annotation != null )
    {
      PsiAnnotationMemberValue value = annotation.findAttributeValue( "value" );
      if( value != null )
      {
        String text = value.getText();
        List<String> modifiers = new ArrayList<>();
        if( modifierList.hasExplicitModifier( PsiModifier.STATIC ) )
        {
          modifiers.add( PsiModifier.STATIC );
        }

        if( text.contains( PropOption.Private.name() ) )
        {
          modifiers.add( PsiModifier.PRIVATE );
        }
        else if( text.contains( PropOption.Package.name() ) )
        {
          modifiers.add( PsiModifier.PACKAGE_LOCAL );
        }
        else if( text.contains( PropOption.Protected.name() ) )
        {
          modifiers.add( PsiModifier.PROTECTED );
        }
        else
        {
          modifiers.add( PsiModifier.PUBLIC );
        }

        modifierList = new ManLightModifierListImpl(
          member.getManager(), JavaLanguage.INSTANCE, modifiers.toArray( new String[0] ) );
        return super.isAccessible( member, modifierList, place, accessObjectClass, currentFileResolveScope );
      }
    }
    return null;
  }

  private boolean isOrOverridesAccessor( PsiMethod method )
  {
    if( getPropertyFieldFrom( method ) != null )
    {
      // getters/setters corresponding with explicit properties should not be available in completion
      return true;
    }

    // check if accessor override
    if( isPotentialAccessor( method ) )
    {
      MethodSignatureBackedByPsiMethod match = SuperMethodsSearch.search( method, null, true, false ).findFirst();
      if( match != null )
      {
        method = match.getMethod();
        if( isOrOverridesAccessor( method ) )
        {
          // getters/setters corresponding with properties should not be available in completion
//          method.putCopyableUserData( PropertyInference.ACCESSOR_TAG, true ); // mark with tag to avoid super searching again
          return true;
        }
      }
    }
    return false;
  }

  private Boolean isVarTagAccessible( PsiField field, PsiElement place, PsiClass accessObjectClass, PsiElement currentFileResolveScope )
  {
    PropertyInference.VarTagInfo tag = field.getCopyableUserData( PropertyInference.VAR_TAG );
    if( tag != null && tag.weakestAccess >= 0 )
    {
      List<String> modifiers = ModifierMap.fromBits( tag.weakestAccess );
      ManLightModifierListImpl modifierList = new ManLightModifierListImpl(
        place.getManager(), JavaLanguage.INSTANCE, modifiers.toArray( new String[0] ) );
      // note, inferred properties respect the default package-protected behavior
      return super.isAccessible( field, modifierList, place, accessObjectClass, currentFileResolveScope );
    }
    return null;
  }

  private boolean isPotentialAccessor( PsiMethod m )
  {
    String name = m.getName();
    for( String prefix: new String[] {"get", "is", "set"} )
    {
      if( name.length() > prefix.length() && name.startsWith( prefix ) &&
        !Character.isLowerCase( name.charAt( prefix.length() ) ) )
      {
        return true;
      }
    }
    return false;
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

  public static boolean isJailbreakType( PsiType type )
  {
    return type != null && type.findAnnotation( Jailbreak.class.getTypeName() ) != null;
  }
}

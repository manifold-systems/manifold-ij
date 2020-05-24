package manifold.ij.extensions;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.messages.MessageBus;
import java.util.Map;
import manifold.ext.rt.api.Jailbreak;
import manifold.ext.rt.api.Self;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.psi.ManLightFieldBuilder;
import manifold.ij.psi.ManLightMethod;
import manifold.ij.psi.ManLightMethodBuilder;
import manifold.ij.psi.ManPsiElementFactory;
import org.jetbrains.annotations.NotNull;

public class ManResolveCache extends ResolveCache
{
  public ManResolveCache( @NotNull MessageBus messageBus )
  {
    super( messageBus );
  }

  @NotNull
  public <T extends PsiPolyVariantReference> ResolveResult[] resolveWithCaching( @NotNull final T ref,
                                                                                 @NotNull final PolyVariantContextResolver<T> resolver,
                                                                                 boolean needToPreventRecursion,
                                                                                 final boolean incompleteCode,
                                                                                 @NotNull final PsiFile containingFile )
  {
    if( !ManProject.isManifoldInUse( containingFile ) )
    {
      // Manifold jars are not used in the project
      return super.resolveWithCaching( ref, resolver, needToPreventRecursion, incompleteCode, containingFile );
    }

    boolean physical = containingFile.isPhysical();
    Map<T, ResolveResult[]> map;
    @Jailbreak ResolveCache me = this;
    int index = me.getIndex( incompleteCode, true );
    map = me.getMap( physical, index );
    ResolveResult[] results = map.get( ref );
    if( results != null )
    {
      return results;
    }

    results = super.resolveWithCaching( ref, resolver, needToPreventRecursion, incompleteCode, containingFile );
    for( ResolveResult result: results )
    {
      if( result instanceof CandidateInfo )
      {
        @Jailbreak CandidateInfo info = (CandidateInfo)result;
        if( ref instanceof PsiReferenceExpression )
        {
          Boolean accessible = info.myAccessible;
          if( accessible == null || !accessible )
          {
            PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
            if( refExpr.getQualifier() instanceof PsiReferenceExpression )
            {
              PsiType type = ((PsiReferenceExpression)refExpr.getQualifier()).getType();
              if( isJailbreakType( type ) )
              {
                info.myAccessible = true;
              }
            }
            else if( refExpr.getQualifier() instanceof PsiMethodCallExpressionImpl )
            {
              PsiMethodCallExpressionImpl qualifier = (PsiMethodCallExpressionImpl)refExpr.getQualifier();
              String referenceName = qualifier.getMethodExpression().getReferenceName();
              if( referenceName != null && referenceName.equals( "jailbreak" ) )
              {
                // special case for jailbreak() extension
                info.myAccessible = true;
              }
              else
              {
                PsiType type = refExpr.getType();
                if( type != null && type.findAnnotation( Jailbreak.class.getTypeName() ) != null )
                {
                  info.myAccessible = true;
                }
              }
            }
          }
          if( info instanceof MethodCandidateInfo )
          {
            handleMethodSelfTypes( (MethodCandidateInfo)info, ref );
          }
          else
          {
            handleFieldSelfTypes( info, ref );
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

  private void handleMethodSelfTypes( MethodCandidateInfo info, PsiPolyVariantReference ref )
  {
    PsiMethod method = info.getElement();
    if( isStatic( method ) )
    {
      return;
    }

    if( !hasSelfAnnotation( method ) )
    {
      return;
    }

    // Reassign the candidate method with one that has Self type substitution
    info.jailbreak().myCandidate = wrapMethod( ManProject.getModule( ref.getElement() ), method, ref );
  }

  private void handleFieldSelfTypes( CandidateInfo info, PsiPolyVariantReference ref )
  {
    if( !(info.getElement() instanceof PsiField) )
    {
      return;
    }

    PsiField field = (PsiField)info.getElement();
    if( isStatic( field ) )
    {
      return;
    }

    if( !SelfTypeUtil.instance().hasSelfAnnotation( field.getType() ) )
    {
      return;
    }

    // Reassign the candidate method with one that has Self type substitution
    info.jailbreak().myCandidate = wrapField( field, ref );
  }

  private ManLightFieldBuilder wrapField( PsiField field, PsiPolyVariantReference ref )
  {
    ManPsiElementFactory manPsiElemFactory = ManPsiElementFactory.instance();
    ManLightFieldBuilder wrappedField = manPsiElemFactory.createLightField( field.getManager(), field.getName(),
      handleType( field.getType(), ref, field ) );
    wrappedField.withNavigationElement( field.getNavigationElement() );

    return wrappedField;
  }

  private PsiMethod wrapMethod( ManModule manModule, PsiMethod refMethod, PsiPolyVariantReference ref )
  {
    ManPsiElementFactory manPsiElemFactory = ManPsiElementFactory.instance();
    String methodName = refMethod.getName();
    PsiClass psiClass = refMethod.getContainingClass();
    ManLightMethodBuilder method = manPsiElemFactory
      .createLightMethod( manModule, psiClass.getManager(), methodName )
      .withContainingClass( psiClass );
    method.withNavigationElement( refMethod.getNavigationElement() );
    method.withMethodReturnType( handleType( refMethod.getReturnType(), ref, refMethod ) );
    copyAnnotations( refMethod, method );

    copyModifiers( refMethod, method );

    for( PsiTypeParameter tv: refMethod.getTypeParameters() )
    {
      method.withTypeParameter( tv );
    }

    PsiParameter[] parameters = refMethod.getParameterList().getParameters();
    for( PsiParameter psiParameter: parameters )
    {
//      method.withParameter( psiParameter.getName(), psiParameter.getType() );
      PsiType type = handleType( psiParameter.getType(), ref, refMethod );
      method.withParameter( psiParameter.getName(), type );
    }

    for( PsiClassType psiClassType: refMethod.getThrowsList().getReferencedTypes() )
    {
      method.withException( psiClassType );
    }

    return method;
  }

  private PsiMethod wrapMethod2( PsiMethod refMethod, PsiPolyVariantReference ref )
  {
    ManPsiElementFactory manPsiElemFactory = ManPsiElementFactory.instance();
    ManLightMethod method = manPsiElemFactory.createLightMethod( refMethod.getManager(), refMethod, refMethod.getContainingClass() );
    method.withNavigationElement( refMethod.getNavigationElement() );
    method.withMethodReturnType( handleType( refMethod.getReturnType(), ref, method ) );

    PsiParameter[] parameters = refMethod.getParameterList().getParameters();
    for( PsiParameter psiParameter: parameters )
    {
//      method.withParameter( psiParameter, psiParameter.getType() );
      method.withParameter( psiParameter, handleType( psiParameter.getType(), ref, method ) );
    }

    return method;
  }

  private PsiType handleType( PsiType type, PsiPolyVariantReference ref, PsiElement context )
  {
    return SelfTypeUtil.instance().handleSelfType2( type, type, (PsiReferenceExpression)ref );
  }

  private void copyModifiers( PsiMethod refMethod, ManLightMethodBuilder method )
  {
    addModifier( refMethod, method, PsiModifier.PUBLIC );
    addModifier( refMethod, method, PsiModifier.STATIC );
    addModifier( refMethod, method, PsiModifier.PACKAGE_LOCAL );
    addModifier( refMethod, method, PsiModifier.PROTECTED );
  }

  private void copyAnnotations( PsiMethod refMethod, ManLightMethodBuilder method )
  {
    for( PsiAnnotation anno: refMethod.getModifierList().getAnnotations() )
    {
      if( Self.class.getTypeName().equals( anno.getQualifiedName() ) )
      {
        // do not transfer @Self, to target type
        continue;
      }

      PsiAnnotation psiAnnotation = method.getModifierList().addAnnotation( anno.getQualifiedName() );
      for( PsiNameValuePair pair: anno.getParameterList().getAttributes() )
      {
        psiAnnotation.setDeclaredAttributeValue( pair.getName(), pair.getValue() );
      }
    }
  }

  private void addModifier( PsiMethod psiMethod, ManLightMethodBuilder method, String modifier )
  {
    if( psiMethod.hasModifierProperty( modifier ) )
    {
      method.withModifier( modifier );
    }
  }

  private boolean hasSelfAnnotation( PsiMethod m )
  {
    if( SelfTypeUtil.instance().hasSelfAnnotation( m.getReturnType() ) )
    {
      return true;
    }
    for( PsiParameter param: m.getParameterList().getParameters() )
    {
      if( SelfTypeUtil.instance().hasSelfAnnotation( param.getType() ) )
      {
        return true;
      }
    }
    return false;
  }

  private static boolean isStatic( PsiModifierListOwner owner )
  {
    if( owner == null )
    {
      return false;
    }
    if( owner instanceof PsiClass && ClassUtil.isTopLevelClass( (PsiClass)owner ) )
    {
      return true;
    }
    PsiModifierList modifierList = owner.getModifierList();
    return modifierList != null && modifierList.hasModifierProperty( PsiModifier.STATIC );
  }

}
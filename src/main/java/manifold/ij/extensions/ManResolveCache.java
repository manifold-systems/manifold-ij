package manifold.ij.extensions;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;

import java.util.*;

import com.intellij.util.IdempotenceChecker;
import manifold.api.util.BytecodeOptions;
import manifold.ext.rt.api.Jailbreak;
import manifold.ext.rt.api.Self;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.psi.ManLightFieldBuilder;
import manifold.ij.psi.ManLightMethod;
import manifold.ij.psi.ManLightMethodBuilder;
import manifold.ij.psi.ManPsiElementFactory;
import manifold.ij.util.ReparseUtil;
import manifold.util.ReflectUtil;
import manifold.util.concurrent.LocklessLazyVar;
import org.apache.log4j.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static manifold.ij.extensions.ManPropertiesAugmentProvider.KEY_CACHED_PROP_FIELD_AUGMENTS;

public class ManResolveCache extends ResolveCache
{
  private static final String MANIFOLD_EXPERIMENTAL_FEATURES_ENABLED = "manifold.experimental.features.enabled";
  private static final LocklessLazyVar<boolean[]> EXPERIMENTAL_FEATURES_ENABLED = LocklessLazyVar.make( () ->
    new boolean[] {PropertiesComponent.getInstance().getBoolean( MANIFOLD_EXPERIMENTAL_FEATURES_ENABLED, true )} );

  public ManResolveCache( @NotNull Project project )
  {
    super( project );

    if( !BytecodeOptions.JDWP_ENABLED.get() )
    {
      // stop the IdempotenceChecker from alarming users, turn it off when not debugging
      // (need to spend time fixing some of these though, hence keeping it on in debugger)
      ReflectUtil.field( IdempotenceChecker.class, "LOG" ).setStatic( new IgnoreLogger() );
    }
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

    ManModule manModule = ManProject.getModule( ref.getElement() );
    if( manModule != null && !manModule.isExtEnabled() )
    {
      // manifold-ext-rt is not used in the ref's module
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
            PsiElement qualifier = refExpr.getQualifier();
            PsiType type = null;
            if( qualifier instanceof PsiExpression )
            {
              type = ((PsiExpression)qualifier).getType();
              if( isJailbreakType( type ) )
              {
                info.myAccessible = true;
              }
            }

            while( qualifier instanceof PsiParenthesizedExpression )
            {
              qualifier = ((PsiParenthesizedExpression)qualifier).getExpression();
            }

            if( qualifier instanceof PsiReferenceExpression )
            {
              if( refExpr instanceof PsiReferenceExpressionImpl )
              {
                // field is not accessible, maybe it's an inferred property that matches an existing field and the
                // enclosing class hasn't been augmented yet. This can happen if no properties were referenced that
                // don't match existing field names.
                if( maybeInvokeFieldAugmenter( ref ) )
                {
                  // trigger field augmenter if need be and re-resolve
                  return resolveWithCaching( ref, resolver, needToPreventRecursion, incompleteCode, containingFile );
                }
              }
            }
            else if( qualifier instanceof PsiMethodCallExpressionImpl )
            {
              String referenceName = ((PsiMethodCallExpressionImpl)qualifier).getMethodExpression().getReferenceName();
              if( referenceName != null && referenceName.equals( "jailbreak" ) )
              {
                // special case for jailbreak() extension
                info.myAccessible = true;
              }
              else
              {
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

  static boolean isExperimentalFeaturesEnabled()
  {
    return EXPERIMENTAL_FEATURES_ENABLED.get()[0];
  }
  static void setExperimentalFeaturesEnabled( boolean enabled )
  {
    EXPERIMENTAL_FEATURES_ENABLED.get()[0] = enabled;
    PropertiesComponent.getInstance().setValue( MANIFOLD_EXPERIMENTAL_FEATURES_ENABLED, enabled, true );
  }

  /**
   * If a field/prop ref resolves as inaccessible, explicitly invoke field augmenters on the qualifier's type.
   * <p/>
   * For example, the `LocalTime` class has existing private field `hour` that matches the inferred property name and,
   * therefore must be designated as a property, which does not happen unless our PsiAugmentProvider for properties runs,
   * which does not happen unless and until a property name is resolved that is NOT an existing field. This is how a
   * PsiClass works with its `findFieldByName()` method -- it won't invoke PsiAugmentProviders until it HAS to.
   * <p/>
   * The reason we're doing this -- forcing PsiAugmentProviders to run -- is that they otherwise don't run
   * for a given PsiClass unless the property name does not exist as a field. So, if a reference to a property is met by
   * an existing field, even if private, it won't trigger the PsiAugmentProvider. But we need the <i>side effects</i> from
   * running the PsiAugmentProvider, such as with field LocalTime#hour which the same name as inferred property from `getHour()`
   * is marked with user data: `VAR_TAG`. This is how a non-private reference to `hour` resolves as a reference to the
   * public property designated by `getHour()`. Otherwise, the reference resolves to the private field, which results in
   * an error.
   */
  private <T extends PsiPolyVariantReference> boolean maybeInvokeFieldAugmenter( T ref )
  {
    if( !isExperimentalFeaturesEnabled() )
    {
      return false;
    }

    if( !(ref instanceof PsiReferenceExpressionImpl) )
    {
      return false;
    }

    PsiExpression qual = ((PsiReferenceExpressionImpl)ref).getQualifierExpression();
    if( qual == null )
    {
      return false;
    }

    ManModule module = ManProject.getModule( qual );
    if( module != null && !module.isPropertiesEnabled() )
    {
      // module not using properties
      return false;
    }

    PsiClass psiClass = PsiTypesUtil.getPsiClass( qual.getType() );

    if( psiClass == null || psiClass.getUserData( KEY_CACHED_PROP_FIELD_AUGMENTS ) != null )
    {
      // already augmented with fields/props
      return false;
    }

    // limiting to ClsClassImpl for now, maybe source classes too someday if necessary
    if( psiClass instanceof ClsClassImpl )
    {
      PsiAugmentProvider.collectAugments( psiClass, PsiField.class, null );
      return true;
    }

    return false;
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

    ManModule manModule = ManProject.getModule( ref.getElement() );
    if( manModule != null &&
      method instanceof ManLightMethodBuilder &&
      ((ManLightMethodBuilder)method).getModules().stream()
        .map( ManModule::getIjModule )
        .noneMatch( methodModule -> GlobalSearchScope.moduleWithDependenciesAndLibrariesScope( manModule.getIjModule() )
          .isSearchInModuleContent( methodModule ) ) )
    {
      // method is extension method and is not accessible from call-site
      return;
    }

    // Reassign the candidate method with one that has Self type substitution
    info.jailbreak().myCandidate = wrapMethod( manModule, method, ref );
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
      handleType( field.getType(), ref, field ), field instanceof ManLightFieldBuilder && ((ManLightFieldBuilder)field).isProperty() );
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

  private static class IgnoreLogger extends Logger
  {
    @Override
    public boolean isDebugEnabled()
    {
      return false;
    }

    @Override
    public void debug( String message )
    {
    }

    @Override
    public void debug( @Nullable Throwable t )
    {
    }

    @Override
    public void debug( String message, @Nullable Throwable t )
    {
    }

    @Override
    public void info( String message )
    {
    }

    @Override
    public void info( String message, @Nullable Throwable t )
    {
    }

    @Override
    public void warn( String message, @Nullable Throwable t )
    {
    }

    @Override
    public void error( String message, @Nullable Throwable t, @NotNull String... details )
    {
    }

    @Override
    public void setLevel( Level level )
    {
    }
  }
}
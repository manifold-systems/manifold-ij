package manifold.ij.extensions;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiCapturedWildcardType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.impl.source.tree.java.PsiLocalVariableImpl;
import com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import java.util.List;
import manifold.ext.api.Jailbreak;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.psi.ManLightMethodBuilder;
import manifold.internal.javac.JavacPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


import static manifold.ij.util.ManVersionUtil.is2019_1_orGreater;

/**
 * Unfortunately IJ doesn't provide a way to augment a type with interfaces, so we are stuck with suppressing errors
 */
public class ManHighlightInfoFilter implements HighlightInfoFilter
{
  /**
   * Override to filter errors related to type incompatibilities arising from a
   * manifold extension adding an interface to an existing classpath class (as opposed
   * to a source file).  Basically suppress "incompatible type errors" or similar
   * involving a structural interface extension.
   */
  @Override
  public boolean accept( @NotNull HighlightInfo hi, @Nullable PsiFile file )
  {
    if( hi.getDescription() == null ||
        hi.getSeverity() != HighlightSeverity.ERROR ||
        file == null )
    {
      return true;
    }

    if( hi.getDescription().contains( "Unhandled exception:" ) )
    {
      Module fileModule = ManProject.getIjModule( file );
      if( fileModule != null )
      {
        ManModule manModule = ManProject.getModule( fileModule );
        return !manModule.isPluginArgEnabled( JavacPlugin.ARG_EXCEPTIONS );
      }
    }

    PsiElement firstElem = file.findElementAt( hi.getStartOffset() );
    if( firstElem == null )
    {
      return true;
    }

    if( filterAmbiguousMethods( hi, firstElem ) )
    {
      return false;
    }

    PsiElement elem = firstElem.getParent();
    if( elem == null )
    {
      return true;
    }

    if( filterIllegalEscapedCharDollars( hi, firstElem, elem ) )
    {
      return false;
    }

    if( filterCannotAssignToFinalIfJailbreak( hi, firstElem ) )
    {
      return false;
    }

    if( !is2019_1_orGreater() )
    {
      if( isInvalidStaticMethodOnInterface( hi ) )
      {
        PsiElement parent = elem.getParent();
        if( !(parent instanceof PsiMethodCallExpressionImpl) )
        {
          return true;
        }
        PsiMethodCallExpressionImpl methodCall = (PsiMethodCallExpressionImpl)parent;
        PsiReferenceExpressionImpl qualifierExpression = (PsiReferenceExpressionImpl)methodCall.getMethodExpression().getQualifierExpression();
        PsiElement lhsType = qualifierExpression == null ? null : qualifierExpression.resolve();
        if( lhsType instanceof ManifoldPsiClass )
        {
          PsiMethod psiMethod = methodCall.resolveMethod();
          if( psiMethod != null )
          {
            // ignore "Static method may be invoked on containing interface class only" errors
            // where the method really is directly on the interface, albeit the delegate
            PsiClass containingClass = psiMethod.getContainingClass();
            return containingClass != null && !containingClass.isInterface();
          }
        }
        return true;
      }
    }

    //##
    //## structural interface extensions cannot be added to the psiClass, so for now we suppress "incompatible type
    //## errors" or similar involving a structural interface extension.
    //##
    Boolean x = acceptInterfaceError( hi, firstElem, elem );
    if( x != null )
    {
      return x;
    }

    return true;
  }

  private boolean filterCannotAssignToFinalIfJailbreak( HighlightInfo hi, PsiElement firstElem )
  {
    if( !hi.getDescription().startsWith( "Cannot assign a value to final variable" ) )
    {
      return false;
    }

    PsiElement parent = firstElem.getParent();
    PsiType type = null;
    if( parent instanceof PsiReferenceExpression )
    {
      type = ((PsiReferenceExpression)parent).getType();
    }
    return type != null && type.findAnnotation( Jailbreak.class.getTypeName() ) != null;
  }

  private boolean filterAmbiguousMethods( HighlightInfo hi, PsiElement elem )
  {
    if( !hi.getDescription().startsWith( "Ambiguous method call" ) )
    {
      return false;
    }

    while( !(elem instanceof PsiMethodCallExpression) )
    {
      elem = elem.getParent();
      if( elem == null )
      {
        return false;
      }
    }

    PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)elem).getMethodExpression();
    JavaResolveResult[] javaResolveResults = methodExpression.multiResolve( false );
    for( JavaResolveResult result: javaResolveResults )
    {
      if( result instanceof MethodCandidateInfo )
      {
        PsiElement psiMethod = result.getElement();
        if( psiMethod instanceof ManLightMethodBuilder )
        {
          return true;
        }
      }
    }
    return false;
  }

  private boolean filterIllegalEscapedCharDollars( @NotNull HighlightInfo hi, PsiElement firstElem, PsiElement elem )
  {
    return firstElem instanceof PsiJavaToken &&
           ((PsiJavaToken)firstElem).getTokenType() == JavaTokenType.STRING_LITERAL &&
           hi.getDescription().contains( "Illegal escape character" ) &&
           elem.getText().contains( "\\$" );
  }

  @Nullable
  private Boolean acceptInterfaceError( @NotNull HighlightInfo hi, PsiElement firstElem, PsiElement elem )
  {
    if( elem instanceof PsiTypeCastExpression )
    {
      PsiTypeElement castType = ((PsiTypeCastExpression)elem).getCastType();
      if( isStructuralType( castType ) )
      {
//        if( TypeUtil.isStructurallyAssignable( castType.getType(), ((PsiTypeCastExpression)elem).getType(), false ) )
//        {
        // ignore incompatible cast type involving structure
        return false;
//        }
      }
    }
    else if( isTypeParameterStructural( hi, firstElem ) )
    {
      return false;
    }
    else if( firstElem instanceof PsiIdentifier )
    {
      PsiTypeElement lhsType = findTypeElement( firstElem );
      if( isStructuralType( lhsType ) )
      {
        PsiType initType = findInitializerType( firstElem );
        if( initType != null )
        {
//          if( TypeUtil.isStructurallyAssignable( lhsType.getType(), initType, false ) )
//          {
            // ignore incompatible type in assignment involving structure
            return false;
//          }
        }
      }
    }
    else if( hi.getDescription().contains( "cannot be applied to" ) )
    {
      PsiMethodCallExpression methodCall = findMethodCall( firstElem );
      if( methodCall != null )
      {
        PsiMethod psiMethod = methodCall.resolveMethod();
        if( psiMethod != null )
        {
          PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
          PsiType[] argTypes = methodCall.getArgumentList().getExpressionTypes();
          for( int i = 0; i < parameters.length; i++ )
          {
            PsiParameter param = parameters[i];
            if( argTypes.length <= i )
            {
              return true;
            }
            if( !isStructuralType( param.getTypeElement() ) )
            {
              if( !param.getType().isAssignableFrom( argTypes[i] ) )
              {
                return true;
              }
            }
//            else
//            {
//              boolean nominal = false;//typeExtensionNominallyExtends( methodCall.getArgumentList().getExpressionTypes()[i], param.getTypeElement() );
//              if( !TypeUtil.isStructurallyAssignable( param.getType(), methodCall.getArgumentList().getExpressionTypes()[i], !nominal ) )
//              {
//                return true;
//              }
//            }
          }
          return true;
        }
      }
    }
    return null;
  }

  private boolean isTypeParameterStructural( @NotNull HighlightInfo hi, PsiElement firstElem )
  {
    if( firstElem == null )
    {
      return false;
    }

    String prefix = "is not within its bound; should ";
    int iPrefix = hi.getDescription().indexOf( prefix );
    if( iPrefix < 0 )
    {
      return false;
    }

    String fqn = hi.getDescription().substring( iPrefix + prefix.length() );
    fqn = fqn.substring( fqn.indexOf( '\'' ) + 1, fqn.lastIndexOf( '\'' ) );
    int iAngle = fqn.indexOf( '<' );
    if( iAngle > 0 )
    {
      fqn = fqn.substring( 0, iAngle );
    }
    // punting on generics for now, using just raw types (waiting patiently for jetbrains to add interfaces as augments...)
    PsiClass iface = JavaPsiFacade.getInstance( firstElem.getProject() ).findClass( fqn, GlobalSearchScope.allScope( firstElem.getProject() ) );
    //PsiReferenceExpression expr = (PsiReferenceExpression)PsiElementFactory.SERVICE.getInstance( firstElem.getProject() ).createExpressionFromText( fqn, firstElem );
    return isExtendedWithInterface( firstElem, iface );
  }

  private boolean isExtendedWithInterface( PsiElement firstElem, PsiClass iface )
  {
    if( iface == null || !isStructuralInterface( iface ) )
    {
      return false;
    }

    PsiTypeElement typeElement = findTypeElement( firstElem );
    if( typeElement == null )
    {
      return false;
    }

    PsiType elemType = typeElement.getType();
    PsiClass psiClass = PsiUtil.resolveClassInType( elemType );
    if( psiClass == null )
    {
      while( elemType instanceof PsiCapturedWildcardType )
      {
        elemType = ((PsiCapturedWildcardType)elemType).getWildcard();
      }

      if( elemType instanceof PsiWildcardType )
      {
        PsiType bound = ((PsiWildcardType)elemType).getBound();
        if( bound == null )
        {
          bound = PsiType.getJavaLangObject( firstElem.getManager(), firstElem.getResolveScope() );
        }
        psiClass = PsiUtil.resolveClassInType( bound );
      }

      if( psiClass == null )
      {
        return false;
      }
    }

    return isExtendedWithInterface( psiClass, iface );
  }

  /**
   * This method checks if the given psiClass or anything in its hierarchy is extended with the structural iface
   * via the KEY_MAN_INTERFACE_EXTENSIONS user data, which is added during augmentation as a hack to provide info
   * about extension interfaces.
   */
  private boolean isExtendedWithInterface( PsiClass psiClass, PsiClass psiInterface )
  {
    if( psiClass == null )
    {
      return false;
    }

    psiClass.getMethods(); // force the ManAugmentProvider to add the KEY_MAN_INTERFACE_EXTENSIONS data, if it hasn't yet
    List<String> ifaceExtenions = psiClass.getCopyableUserData( ManAugmentProvider.KEY_MAN_INTERFACE_EXTENSIONS );
    if( ifaceExtenions == null || ifaceExtenions.isEmpty() )
    {
      return false;
    }

    Project project = psiClass.getProject();
    PsiClassType typeIface = JavaPsiFacade.getInstance( project ).getElementFactory().createType( psiInterface );

    for( String fqnExt: ifaceExtenions )
    {
      PsiClass psiExt = JavaPsiFacade.getInstance( project ).findClass( fqnExt, GlobalSearchScope.allScope( project ) );
      if( psiExt != null )
      {
        PsiClassType typeExt = JavaPsiFacade.getInstance( project ).getElementFactory().createType( psiExt );
        if( typeIface.isAssignableFrom( typeExt ) )
        {
          return true;
        }
      }
    }

    for( PsiClassType extendsType: psiClass.getExtendsListTypes() )
    {
      PsiClass extendsPsi = extendsType.resolve();
      if( isExtendedWithInterface( extendsPsi, psiInterface ) )
      {
        return true;
      }
    }
    for( PsiClassType implementsType: psiClass.getImplementsListTypes() )
    {
      PsiClass implementsPsi = implementsType.resolve();
      if( isExtendedWithInterface( implementsPsi, psiInterface ) )
      {
        return true;
      }
    }
    return false;
  }

  private boolean isInvalidStaticMethodOnInterface( HighlightInfo hi )
  {
    return hi.getDescription().contains( "Static method may be invoked on containing interface class only" );
  }

  private PsiType findInitializerType( PsiElement firstElem )
  {
    PsiElement csr = firstElem;
    while( csr != null && !(csr instanceof PsiLocalVariableImpl) )
    {
      csr = csr.getParent();
    }
    if( csr != null )
    {
      PsiExpression initializer = ((PsiLocalVariableImpl)csr).getInitializer();
      return initializer == null ? null : initializer.getType();
    }
    return null;
  }

//## todo: implementing this is not efficient to say the least, so for now we will always check for structural assignability
//  private boolean typeExtensionNominallyExtends( PsiType psiType, PsiTypeElement typeElement )
//  {
//    if( !(psiType instanceof PsiClassType) )
//    {
//      return false;
//    }
//
//    PsiClassType rawType = ((PsiClassType)psiType).rawType();
//    rawType.getSuperTypes()
//    ManModule module = ManProject.getModule( typeElement );
//    for( ITypeManifold sp : module.getTypeManifolds() )
//    {
//      if( sp.getContributorKind() == Supplemental )
//      {
//
//      }
//    }
//  }

//  private int findArgPos( PsiMethodCallExpression methodCall, PsiElement firstElem )
//  {
//    PsiExpression[] args = methodCall.getArgumentList().getExpressions();
//    for( int i = 0; i < args.length; i++ )
//    {
//      PsiExpression arg = args[i];
//      PsiElement csr = firstElem;
//      while( csr != null && csr != firstElem )
//      {
//        csr = csr.getParent();
//      }
//      if( csr == firstElem )
//      {
//        return i;
//      }
//    }
//    throw new IllegalStateException();
//  }

  private boolean isStructuralType( PsiTypeElement typeElem )
  {
    if( typeElem != null )
    {
      PsiClass psiClass = PsiUtil.resolveClassInType( typeElem.getType() );
      if( psiClass == null )
      {
        return false;
      }
      return isStructuralInterface( psiClass );
    }
    return false;
  }

  private boolean isStructuralInterface( PsiClass psiClass )
  {
    PsiAnnotation structuralAnno = psiClass.getModifierList() == null
                                   ? null
                                   : psiClass.getModifierList().findAnnotation( "manifold.ext.api.Structural" );
    return structuralAnno != null;
  }

  private PsiTypeElement findTypeElement( PsiElement elem )
  {
    PsiElement csr = elem;
    while( csr != null && !(csr instanceof PsiTypeElement) )
    {
      csr = csr.getParent();
    }
    return (PsiTypeElement)csr;
  }

  private PsiMethodCallExpression findMethodCall( PsiElement elem )
  {
    PsiElement csr = elem;
    while( csr != null && !(csr instanceof PsiMethodCallExpression) )
    {
      csr = csr.getParent();
    }
    return (PsiMethodCallExpression)csr;
  }
}

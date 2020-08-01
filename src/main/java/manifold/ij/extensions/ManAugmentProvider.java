package manifold.ij.extensions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;
import manifold.api.fs.IFile;
import manifold.api.gen.AbstractSrcMethod;
import manifold.api.gen.SrcAnnotationExpression;
import manifold.api.gen.SrcClass;
import manifold.api.gen.SrcMethod;
import manifold.api.gen.SrcParameter;
import manifold.api.gen.SrcRawStatement;
import manifold.api.gen.SrcStatementBlock;
import manifold.api.gen.SrcType;
import manifold.rt.api.Array;
import manifold.api.type.ITypeManifold;
import manifold.api.type.SourcePosition;
import manifold.ext.IExtensionClassProducer;
import manifold.ext.rt.api.Extension;
import manifold.ext.rt.api.This;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.fs.IjFile;
import manifold.ij.psi.ManLightMethodBuilder;
import manifold.ij.psi.ManPsiElementFactory;
import org.jetbrains.annotations.NotNull;


import static manifold.api.type.ContributorKind.Supplemental;

/**
 * Augment a PsiClass extended by one or more extension classes.  All
 * extension methods from all modules are added.  We filter the methods
 * in code completion to those accessible from the call-site.  We also
 * add a compile error via {@link ExtensionMethodCallSiteAnnotator} where
 * an extension method is not accessible to a call-site.
 */
public class ManAugmentProvider extends PsiAugmentProvider
{
  static final Key<List<String>> KEY_MAN_INTERFACE_EXTENSIONS = new Key<>( "MAN_INTERFACE_EXTENSIONS" );

  @NotNull
  public <E extends PsiElement> List<E> getAugments( @NotNull PsiElement element, @NotNull Class<E> cls )
  {
    if( !ManProject.isManifoldInUse( element ) )
    {
      // Manifold jars are not used in the project
      return Collections.emptyList();
    }

    return ApplicationManager.getApplication().runReadAction( (Computable<List<E>>)() -> _getAugments( element, cls ) );
  }

  private <E extends PsiElement> List<E> _getAugments( PsiElement element, Class<E> cls )
  {
    // Module is assigned to user-data via ManTypeFinder, which loads the psiClass (element)
    if( DumbService.getInstance( element.getProject() ).isDumb() )
    {
      // skip processing during index rebuild
      return Collections.emptyList();
    }

    if( !(element instanceof PsiClass) || !element.isValid() || !PsiMethod.class.isAssignableFrom( cls ) )
    {
      return Collections.emptyList();
    }

    LinkedHashMap<String, PsiMethod> augFeatures = new LinkedHashMap<>();
    PsiClass psiClass = (PsiClass)element;
    String className = psiClass.getQualifiedName();
    if( className == null )
    {
      return Collections.emptyList();
    }

    String name = ((PsiClass) element).getName();
    if( name != null && name.equals( "__Array__") )
    {
      className = Array.class.getTypeName();
    }

    addMethods( className, psiClass, augFeatures );

    //noinspection unchecked
    return new ArrayList<>( (Collection<? extends E>) augFeatures.values() );
  }

  private void addMethods( String fqn, PsiClass psiClass, LinkedHashMap<String, PsiMethod> augFeatures )
  {
    ManProject manProject = ManProject.manProjectFrom( psiClass.getProject() );
    for( ManModule manModule : manProject.getModules().values() )
    {
      addMethods( fqn, psiClass, augFeatures, manModule.getIjModule() );
    }
  }

  private void addMethods( String fqn, PsiClass psiClass, LinkedHashMap<String, PsiMethod> augFeatures, Module module )
  {
    ManModule manModule = ManProject.getModule( module );
    if( manModule == null )
    {
      return;
    }

    for( ITypeManifold tm : manModule.getTypeManifolds() )
    {
      if( tm.getContributorKind() == Supplemental )
      {
        if( tm.isType( fqn ) )
        {
          List<IFile> files = tm.findFilesForType( fqn );
          for( IFile file : files )
          {
            VirtualFile vFile = ((IjFile)file.getPhysicalFile()).getVirtualFile();
            if( !vFile.isValid() )
            {
              continue;
            }

            PsiFile psiFile = PsiManager.getInstance( module.getProject() ).findFile( vFile );
            PsiJavaFile psiJavaFile = (PsiJavaFile)psiFile;
            if( psiJavaFile != null )
            {
              PsiClass[] classes = psiJavaFile.getClasses();
              if( classes.length > 0 )
              {
                String topLevelFqn = ManifoldPsiClassCache.findTopLevelFqn( tm, fqn );
                String innerSuffix = fqn.substring( topLevelFqn.length() );

                PsiClass extClass = findExtClass( classes[0], classes[0].getQualifiedName() + innerSuffix );
                addMethods( psiClass, augFeatures, manModule, extClass );
              }
            }
          }
        }
      }
      else if( tm instanceof IExtensionClassProducer )
      {
        IExtensionClassProducer ecp = (IExtensionClassProducer)tm;
        if( ecp.isExtendedType( fqn ) )
        {
          Set<String> extensionClassNames = ecp.getExtensionClasses( fqn );
          for( String extension : extensionClassNames )
          {
            PsiClass extPsiClass = ManifoldPsiClassCache.getPsiClass( manModule, extension );
            PsiClass extClass = findExtClass( extPsiClass, extension );
            addMethods( psiClass, augFeatures, manModule, extClass );
          }
        }
      }
    }
  }

  private PsiClass findExtClass( PsiClass topLevel, String fqn )
  {
    String name = topLevel.getQualifiedName();
    if( name == null || name.equals( fqn ) )
    {
      return topLevel;
    }

    // Handle inner class extensions
    for( PsiClass inner : topLevel.getInnerClasses() )
    {
      PsiClass extClass = findExtClass( inner, fqn );
      if( extClass != null )
      {
        return extClass;
      }
    }

    return null;
  }

  private void addMethods( PsiClass psiClass, LinkedHashMap<String, PsiMethod> augFeatures, ManModule manModule, PsiClass extClass )
  {
    if( extClass == null )
    {
      return;
    }

    addInterfaceExtensions( psiClass, extClass );

    SrcClass srcExtClass = new StubBuilder().make( extClass.getQualifiedName(), manModule );
    if( srcExtClass == null )
    {
      return;
    }

    SrcClass scratchClass = new SrcClass( psiClass.getQualifiedName(), psiClass.isInterface() ? SrcClass.Kind.Interface : SrcClass.Kind.Class );
    for( PsiTypeParameter tv : psiClass.getTypeParameters() )
    {
      scratchClass.addTypeVar( new SrcType( StubBuilder.makeTypeVar( tv ) ) );
    }
    for( AbstractSrcMethod<?> m : srcExtClass.getMethods() )
    {
      SrcMethod srcMethod = addExtensionMethod( scratchClass, m, psiClass );
      if( srcMethod != null )
      {
        StringBuilder key = new StringBuilder();
        srcMethod.render( key, 0 );
        PsiMethod existingMethod = augFeatures.get( key.toString() );
        if( existingMethod != null )
        {
          // already added from another module root, the method has multiple module refs e.g., ManStringExt
          ((ManLightMethodBuilder)existingMethod).withAdditionalModule( manModule );
        }
        else
        {
          PsiMethod extMethod = makePsiMethod( srcMethod, psiClass );
          if( extMethod != null )
          {
            PsiMethod plantedMethod = plantMethodInPsiClass( manModule, extMethod, psiClass, extClass );
            augFeatures.put( key.toString(), plantedMethod );
          }
        }
      }
    }
  }

  /**
   * A stopgap until jetbrains supports interfaces as augments
   */
  private void addInterfaceExtensions( PsiClass psiClass, PsiClass extClass )
  {
    List<String> ifaceExtensions = Arrays.stream( extClass.getImplementsListTypes() )
      .map( PsiClassType::resolve )
      .filter( Objects::nonNull )
      .map( PsiClass::getQualifiedName ).collect( Collectors.toList() );
    psiClass.putUserData( KEY_MAN_INTERFACE_EXTENSIONS, ifaceExtensions );
  }

  private PsiMethod makePsiMethod( AbstractSrcMethod<?> method, PsiClass psiClass )
  {
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance( psiClass.getProject() ).getElementFactory();
    StringBuilder sb = new StringBuilder();
    method.render( sb, 0 );
    try
    {
      return elementFactory.createMethodFromText( sb.toString(), psiClass );
    }
    catch( IncorrectOperationException ioe )
    {
      // the text of the method does not conform to method grammar, probably being edited in an IJ editor,
      // ignore these since the editor provides error information
      return null;
    }
  }

  private PsiMethod plantMethodInPsiClass( ManModule manModule, PsiMethod refMethod, PsiClass psiClass, PsiClass extClass )
  {
    if( null != refMethod )
    {
      ManPsiElementFactory manPsiElemFactory = ManPsiElementFactory.instance();
      String methodName = refMethod.getName();
      ManLightMethodBuilder method = manPsiElemFactory.createLightMethod( manModule, psiClass.getManager(), methodName )
        .withMethodReturnType( refMethod.getReturnType() )
        .withContainingClass( psiClass );
      PsiElement navElem = findExtensionMethodNavigationElement( extClass, refMethod );
      if( navElem != null )
      {
        method.withNavigationElement( navElem );
      }

      copyAnnotations( refMethod, method );

      copyModifiers( refMethod, method );

      for( PsiTypeParameter tv : refMethod.getTypeParameters() )
      {
        method.withTypeParameter( tv );
      }

      PsiParameter[] parameters = refMethod.getParameterList().getParameters();
      for( PsiParameter psiParameter : parameters )
      {
        method.withParameter( psiParameter.getName(), psiParameter.getType() );
      }

      for( PsiClassType psiClassType : refMethod.getThrowsList().getReferencedTypes() )
      {
        method.withException( psiClassType );
      }

      return method;
    }
    return null;
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
    for( PsiAnnotation anno : refMethod.getModifierList().getAnnotations() )
    {
      String qualifiedName = anno.getQualifiedName();
      if( qualifiedName == null )
      {
        continue;
      }
      PsiAnnotation psiAnnotation = method.getModifierList().addAnnotation( qualifiedName );
      for( PsiNameValuePair pair : anno.getParameterList().getAttributes() )
      {
        psiAnnotation.setDeclaredAttributeValue( pair.getName(), pair.getValue() );
      }
    }
  }

  private PsiElement findExtensionMethodNavigationElement( PsiClass extClass, PsiMethod plantedMethod )
  {
    PsiMethod[] found = extClass.findMethodsByName( plantedMethod.getName(), false );
    outer:
    for( PsiMethod m : found )
    {
      PsiParameter[] extParams = m.getParameterList().getParameters();
      PsiParameter[] plantedParams = plantedMethod.getParameterList().getParameters();
      int offset = getParamOffset( m );
      if( extParams.length - offset == plantedParams.length )
      {
        for( int i = offset; i < extParams.length; i++ )
        {
          PsiParameter extParam = extParams[i];
          PsiParameter plantedParam = plantedParams[i - offset];
          PsiType extErased = TypeConversionUtil.erasure( extParam.getType() );
          PsiType plantedErased = TypeConversionUtil.erasure( plantedParam.getType() );
          if( !extErased.toString().equals( plantedErased.toString() ) )
          {
            continue outer;
          }
        }
        return m.getNavigationElement();
      }
    }
    return null;
  }

  private int getParamOffset( PsiMethod m )
  {
    boolean isStaticExtension = Arrays.stream( m.getModifierList().getAnnotations() )
      .anyMatch( anno ->
        anno.getQualifiedName() != null && anno.getQualifiedName().equals( Extension.class.getTypeName() ) );
    return isStaticExtension ? 0 : 1;
  }

  private void addModifier( PsiMethod psiMethod, ManLightMethodBuilder method, String modifier )
  {
    if( psiMethod.hasModifierProperty( modifier ) )
    {
      method.withModifier( modifier );
    }
  }

  private SrcMethod addExtensionMethod( SrcClass srcClass, AbstractSrcMethod<?> method, PsiClass extendedType )
  {
    if( !isExtensionMethod( method, extendedType.getQualifiedName() ) )
    {
      return null;
    }

    SrcMethod srcMethod = new SrcMethod( srcClass );

    copyAnnotations( method, srcMethod );

    long modifiers = method.getModifiers();

    boolean isInstanceExtensionMethod = isInstanceExtensionMethod( method, extendedType.getQualifiedName() );

    if( extendedType.isInterface() && isInstanceExtensionMethod )
    {
      // extension method must be default method in interface to not require implementation
      modifiers |= 0x80000000000L; //Flags.DEFAULT;
    }

    if( isInstanceExtensionMethod )
    {
      // remove static
      modifiers &= ~Modifier.STATIC;
    }
    srcMethod.modifiers( modifiers );

    srcMethod.returns( method.getReturnType() );

    String name = method.getSimpleName();
    srcMethod.name( name );

    @SuppressWarnings("unchecked")
    List<SrcType> typeParams = method.getTypeVariables();

    // extension method must reflect extended type's type vars before its own
    PsiTypeParameterList typeParameterList = extendedType.getTypeParameterList();
    if( typeParameterList != null )
    {
      int extendedTypeVarCount = typeParameterList.getTypeParameters().length;
      for( int i = isInstanceExtensionMethod ? extendedTypeVarCount : 0; i < typeParams.size(); i++ )
      {
        SrcType typeVar = typeParams.get( i );
        srcMethod.addTypeVar( typeVar );
      }
    }

    List<SrcParameter> params = method.getParameters();
    for( int i = isInstanceExtensionMethod ? 1 : 0; i < params.size(); i++ )
    {
      // exclude This param

      SrcParameter param = params.get( i );
      srcMethod.addParam( param.getSimpleName(), param.getType() );
    }

    for( Object throwType : method.getThrowTypes() )
    {
      srcMethod.addThrowType( (SrcType)throwType );
    }

    srcMethod.body( new SrcStatementBlock()
      .addStatement(
        new SrcRawStatement()
          .rawText( "throw new RuntimeException();" ) ) );

    //srcClass.addMethod( srcMethod );
    return srcMethod;
  }

  private void copyAnnotations( AbstractSrcMethod<?> method, SrcMethod srcMethod )
  {
    for( Object anno : method.getAnnotations() )
    {
      SrcAnnotationExpression annoExpr = (SrcAnnotationExpression)anno;
      if( annoExpr.getAnnotationType().equals( SourcePosition.class.getName() ) )
      {
        srcMethod.addAnnotation( annoExpr );
      }
    }
  }

  private boolean isExtensionMethod( AbstractSrcMethod<?> method, String extendedType )
  {
    if( !Modifier.isStatic( (int)method.getModifiers() ) || Modifier.isPrivate( (int)method.getModifiers() ) )
    {
      return false;
    }

    if( method.hasAnnotation( Extension.class ) )
    {
      return true;
    }

    return hasThisAnnotation( method, extendedType );
  }

  private boolean isInstanceExtensionMethod( AbstractSrcMethod<?> method, String extendedType )
  {
    if( !Modifier.isStatic( (int)method.getModifiers() ) || Modifier.isPrivate( (int)method.getModifiers() ) )
    {
      return false;
    }

    return hasThisAnnotation( method, extendedType );
  }

  private boolean hasThisAnnotation( AbstractSrcMethod<?> method, String extendedType )
  {
    List<SrcParameter> params = method.getParameters();
    if( params.size() == 0 )
    {
      return false;
    }
    SrcParameter param = params.get( 0 );
    if( !param.hasAnnotation( This.class ) )
    {
      return false;
    }
    return param.getType().getName().equals( extendedType ) || isArrayExtension( param, extendedType );
  }

  private boolean isArrayExtension( SrcParameter param, String extendedType )
  {
    return extendedType.endsWith( ".__Array__" ) && param.getType().getName().equals( Object.class.getTypeName() );
  }
}

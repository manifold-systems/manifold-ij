package manifold.ij.extensions;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
import manifold.ext.rt.api.ThisClass;
import manifold.rt.api.Array;
import manifold.api.type.ITypeManifold;
import manifold.rt.api.SourcePosition;
import manifold.ext.IExtensionClassProducer;
import manifold.ext.rt.api.Extension;
import manifold.ext.rt.api.This;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.fs.IjFile;
import manifold.ij.psi.ManLightMethodBuilder;
import manifold.ij.psi.ManPsiElementFactory;
import manifold.util.ReflectUtil;
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
  static final Key<CachedValue<? extends PsiElement>> KEY_CACHED_AUGMENTS = new Key<>( "CACHED_AUGMENTS" );

  private final Map<Project, ExtensionClassPsiListener> _mapExtClassListeners = new ConcurrentHashMap<>();


  @NotNull
  public <E extends PsiElement> List<E> getAugments( @NotNull PsiElement element, @NotNull Class<E> cls )
  {
    return getAugments( element, cls, null );
  }
  public <E extends PsiElement> List<E> getAugments( @NotNull PsiElement element, @NotNull Class<E> cls, String nameHint )
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

    PsiClass psiClass = (PsiClass)element;

    if( psiClass.getLanguage() != JavaLanguage.INSTANCE &&
      psiClass.getLanguage().getBaseLanguage() != JavaLanguage.INSTANCE )
    {
      return Collections.emptyList();
    }

    String className = psiClass.getQualifiedName();
    if( className == null )
    {
      return Collections.emptyList();
    }

    String name = ((PsiClass)element).getName();
    if( name != null && name.equals( "__Array__" ) )
    {
      className = Array.class.getTypeName();
    }

    String fqnClass = className;

    Project project = psiClass.getProject();
    addPsiExtensionChangeListener( project );

//Without caching:
//    LinkedHashMap<String, PsiMethod> augFeatures = new LinkedHashMap<>();
//    addMethods( fqnClass, psiClass, augFeatures );
//    return new ArrayList<>( (Collection<? extends E>) augFeatures.values() );

//With caching:
    ReflectUtil.FieldRef DO_CHECKS = ReflectUtil.field( "com.intellij.util.CachedValueStabilityChecker", "DO_CHECKS" );
    try { if( (boolean)DO_CHECKS.getStatic() ) DO_CHECKS.setStatic( false ); } catch( Throwable ignore ){}
    //noinspection unchecked
    return CachedValuesManager.getCachedValue( psiClass,
      (Key)KEY_CACHED_AUGMENTS,
      (CachedValueProvider<List<E>>)() -> {
        LinkedHashMap<String, PsiMethod> augFeatures = new LinkedHashMap<>();
        List<Object> dependencies = new ArrayList<>( addMethods( fqnClass, psiClass, augFeatures ) );
        dependencies.add( psiClass );
        dependencies.add( new MyModificationTracker( fqnClass, project ) );
        //noinspection unchecked
        return new CachedValueProvider.Result<>(
          new ArrayList<E>( (Collection<E>)augFeatures.values() ), dependencies.toArray() );
      } );
  }

  private void addPsiExtensionChangeListener( Project project )
  {
    ExtensionClassPsiListener extensionClassPsiListener = _mapExtClassListeners.get( project );
    if( extensionClassPsiListener == null )
    {
      extensionClassPsiListener = new ExtensionClassPsiListener();
      PsiManager.getInstance( project ).addPsiTreeChangeListener( extensionClassPsiListener, project );
      _mapExtClassListeners.put( project, extensionClassPsiListener );
    }
  }

  private List<PsiClass> addMethods( String fqn, PsiClass psiClass, LinkedHashMap<String, PsiMethod> augFeatures )
  {
    ManProject manProject = ManProject.manProjectFrom( psiClass.getProject() );
    List<PsiClass> extensionClasses = new ArrayList<>();
    for( ManModule manModule : manProject.getModules().values() )
    {
      extensionClasses.addAll( addMethods( fqn, psiClass, augFeatures, manModule ) );
    }
    return extensionClasses;
  }

  private List<PsiClass> addMethods( String fqn, PsiClass psiClass, LinkedHashMap<String, PsiMethod> augFeatures, ManModule manModule )
  {
    List<PsiClass> extensionClasses = new ArrayList<>();

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

            PsiFile psiFile = PsiManager.getInstance( manModule.getIjModule().getProject() ).findFile( vFile );
            PsiJavaFile psiJavaFile = (PsiJavaFile)psiFile;
            if( psiJavaFile != null )
            {
              PsiClass[] classes = psiJavaFile.getClasses();
              if( classes.length > 0 )
              {
                String topLevelFqn = ManifoldPsiClassCache.findTopLevelFqn( tm, fqn );
                String innerSuffix = fqn.substring( topLevelFqn.length() );

                PsiClass extClass = findExtClass( classes[0], classes[0].getQualifiedName() + innerSuffix );
                if( extClass != null )
                {
                  extensionClasses.add( extClass );
                  addMethods( psiClass, augFeatures, manModule, extClass );
                }
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
            if( extClass != null )
            {
              extensionClasses.add( extClass );
              addMethods( psiClass, augFeatures, manModule, extClass );
            }
          }
        }
      }
    }
    return extensionClasses;
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
      int offset = getParamOffset( extParams );
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

  private int getParamOffset( PsiParameter[] params )
  {
    if( params.isEmpty() )
    {
      return 0;
    }
    boolean skipFirstParam = Arrays.stream( params[0].getAnnotations() )
      .anyMatch( anno ->
        anno.getQualifiedName() != null &&
          (anno.getQualifiedName().equals( This.class.getTypeName() ) ||
           anno.getQualifiedName().equals( ThisClass.class.getTypeName() )) );
    return skipFirstParam ? 1 : 0;
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

    List<SrcType> typeParams = method.getTypeVariables();

    // extension method must reflect extended type's type vars before its own
    PsiTypeParameterList typeParameterList = getTypeParameterList( extendedType );
    int extendedTypeVarCount = typeParameterList == null ? 0 : typeParameterList.getTypeParameters().length;
    for( int i = isInstanceExtensionMethod ? extendedTypeVarCount : 0; i < typeParams.size(); i++ )
    {
      SrcType typeVar = typeParams.get( i );
      srcMethod.addTypeVar( typeVar );
    }

    boolean hasThisClassAnnotation = hasThisClassAnnotation( method );
    List<SrcParameter> params = method.getParameters();
    for( int i = (isInstanceExtensionMethod || hasThisClassAnnotation) ? 1 : 0; i < params.size(); i++ )
    {
      // exclude @This or @ThisClass param

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

  private PsiTypeParameterList getTypeParameterList( PsiClass extendedType )
  {
    String name = extendedType.getName();
    if( name != null && name.equals( "__Array__" ) )
    {
      // ignore type variable on IJ's internal array type,
      // it must behave as the Java compiler's array type, which is not generic
      return null;
    }
    return extendedType.getTypeParameterList();
  }

  private void copyAnnotations( AbstractSrcMethod<?> method, SrcMethod srcMethod )
  {
    for( Object anno : method.getAnnotations() )
    {
      SrcAnnotationExpression annoExpr = (SrcAnnotationExpression)anno;
      if( annoExpr.getAnnotationType().equals( SourcePosition.class.getName() ) ||
          annoExpr.getAnnotationType().equals( SafeVarargs.class.getTypeName() ) ) // why aren't all annotations here?
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

    return hasThisAnnotation( method, extendedType ) || hasThisClassAnnotation( method );
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
  private boolean hasThisClassAnnotation( AbstractSrcMethod<?> method )
  {
    List<SrcParameter> params = method.getParameters();
    if( params.size() == 0 )
    {
      return false;
    }
    SrcParameter param = params.get( 0 );
    if( !param.hasAnnotation( ThisClass.class ) )
    {
      return false;
    }
    return param.getType().getName().equals( Class.class.getTypeName() );
  }

  private boolean isArrayExtension( SrcParameter param, String extendedType )
  {
    return extendedType.endsWith( ".__Array__" ) && param.getType().getName().equals( Object.class.getTypeName() );
  }

  private class MyModificationTracker implements ModificationTracker
  {
    private final String _fqn;
    private final ExtensionClassPsiListener _extensionChangeListener;

    private MyModificationTracker( String fqn, Project project )
    {
      _fqn = fqn;
      _extensionChangeListener = _mapExtClassListeners.get( project );
      if( _extensionChangeListener == null )
      {
        throw new IllegalStateException();
      }
    }

    @Override
    public long getModificationCount()
    {
      // if any mods were made to extension classes on this fqn, the mod count bumps
      return _extensionChangeListener.getModCount( _fqn );
    }
  }

  /**
   * Used to keep track of when a new extension class is created for a given psi class.
   */
  private static class ExtensionClassPsiListener implements PsiTreeChangeListener
  {
    private final Map<String, Long> _mapFqnToModCount = new ConcurrentHashMap<>();

    private long getModCount( String fqn )
    {
      Long modCount = _mapFqnToModCount.get( fqn );
      return modCount == null ? 0L : modCount;
    }

    @Override
    public void beforeChildAddition( @NotNull PsiTreeChangeEvent event )
    {
    }

    @Override
    public void beforeChildRemoval( @NotNull PsiTreeChangeEvent event )
    {
    }

    @Override
    public void beforeChildReplacement( @NotNull PsiTreeChangeEvent event )
    {
    }

    @Override
    public void beforeChildMovement( @NotNull PsiTreeChangeEvent event )
    {
    }

    @Override
    public void beforeChildrenChange( @NotNull PsiTreeChangeEvent event )
    {
    }

    @Override
    public void beforePropertyChange( @NotNull PsiTreeChangeEvent event )
    {
    }

    @Override
    public void childAdded( @NotNull PsiTreeChangeEvent event )
    {
      incIfExtensionClass( event );
    }

    @Override
    public void childRemoved( @NotNull PsiTreeChangeEvent event )
    {
      incIfExtensionClass( event );
    }

    @Override
    public void childReplaced( @NotNull PsiTreeChangeEvent event )
    {
      incIfExtensionClass( event );
    }

    @Override
    public void childrenChanged( @NotNull PsiTreeChangeEvent event )
    {
      incIfExtensionClass( event );
    }

    @Override
    public void childMoved( @NotNull PsiTreeChangeEvent event )
    {
      incIfExtensionClass( event );
    }

    @Override
    public void propertyChanged( @NotNull PsiTreeChangeEvent event )
    {
      incIfExtensionClass( event );
    }

    private void incIfExtensionClass( PsiTreeChangeEvent event )
    {
      PsiFile file = event.getFile();
      if( file instanceof PsiClassOwner )
      {
        PsiClass changedClass = null;
        if( event.getFile() instanceof PsiJavaFile )
        {
          PsiClass[] classes = ((PsiJavaFile)event.getFile()).getClasses();
          if( classes.length > 0 )
          {
            changedClass = classes[0];
          }
        }
        if( changedClass != null &&
          changedClass.hasAnnotation( Extension.class.getTypeName() ) )
        {
          String packageName = PsiUtil.getPackageName( changedClass );
          if( packageName != null )
          {
            String extendedClassFqn = ExtensionClassAnnotator.getExtendedClassName( packageName );
            Long modCount = _mapFqnToModCount.computeIfAbsent( extendedClassFqn, key -> 0L );
            _mapFqnToModCount.put( extendedClassFqn, modCount + 1 );
          }
        }
      }
    }
  }
}

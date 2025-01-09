/*
 *
 *  * Copyright (c) 2022 - Manifold Systems LLC
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package manifold.ij.extensions;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
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
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.*;

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
import manifold.internal.javac.ManAttr;
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
import manifold.rt.api.util.ManClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


import static manifold.api.type.ContributorKind.Supplemental;
import static manifold.ij.util.ManPsiGenerationUtil.*;

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
  static final Key<CachedValue<List<PsiMethod>>> KEY_CACHED_AUGMENTS = new Key<>( "CACHED_AUGMENTS" );

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

    if( !(element instanceof PsiExtensibleClass) || !element.isValid() || !PsiMethod.class.isAssignableFrom( cls ) )
    {
      return Collections.emptyList();
    }

    PsiExtensibleClass psiClass = (PsiExtensibleClass)element;

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

    addPsiExtensionChangeListener( psiClass.getProject() );

//Without caching:
//    LinkedHashMap<String, PsiMethod> augFeatures = new LinkedHashMap<>();
//    addMethods( fqnClass, psiClass, augFeatures );
//    return new ArrayList<>( (Collection<? extends E>) augFeatures.values() );

//With caching:
    //noinspection unchecked
    return CachedValuesManager.getCachedValue( psiClass,
      (Key)KEY_CACHED_AUGMENTS,
      (CachedValueProvider<List<E>>)() -> {
        String fqn = ((PsiClass)element).getName();
        if( fqn != null && fqn.equals( "__Array__" ) )
        {
          fqn = Array.class.getTypeName();
        }
        else
        {
          fqn = ((PsiExtensibleClass)element).getQualifiedName();
        }

        LinkedHashMap<String, PsiMethod> augFeatures = new LinkedHashMap<>();
        List<Object> dependencies = new ArrayList<>( addMethods( fqn, psiClass, augFeatures ) );
        dependencies.add( psiClass );
        dependencies.add( new MyModificationTracker( fqn, psiClass.getProject() ) );
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

    String qualifiedName = extClass.getQualifiedName();
    if( qualifiedName == null )
    {
      return;
    }

    SrcClass srcExtClass = new StubBuilder().make( qualifiedName, manModule, false );
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
            PsiMethod navMethod = findExtensionMethodNavigationElement( extClass, extMethod );
            PsiMethod plantedMethod = plantMethodInPsiClass( manModule, extMethod, psiClass, navMethod );
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

  final ThreadLocal<Set<PsiReturnStatement>> visited = ThreadLocal.withInitial( () -> new HashSet<>() );
  @Override @Nullable
  protected PsiType inferType( @NotNull PsiTypeElement typeElement )
  {
    if( !canInferType( typeElement ) )
    {
      return null;
    }

    PsiVariable variable = PsiTreeUtil.getParentOfType( typeElement, PsiVariable.class );
    if( variable != null )
    {
      PsiExpression initializer = variable.getInitializer();
      if( initializer != null )
      {
        return initializer.getType();
      }
      else if( variable instanceof PsiParameter )
      {
        PsiForeachStatement forEachStmt = PsiTreeUtil.getParentOfType( typeElement, PsiForeachStatement.class );
        if( forEachStmt != null )
        {
          PsiExpression expression = forEachStmt.getIteratedValue();
          return expression == null ? null : JavaGenericsUtil.getCollectionItemType( expression );
        }
      }
    }
    else
    {
      PsiMethod method = PsiTreeUtil.getParentOfType( typeElement, PsiMethod.class );
      if( method != null )
      {
        PsiCodeBlock body = method.getBody();
        if( body != null )
        {
          PsiType[] retType = {null};
          //noinspection UnsafeReturnStatementVisitor
          body.accept(
            new JavaRecursiveElementVisitor()
            {
              @Override
              public void visitReturnStatement( PsiReturnStatement statement )
              {
                if( visited.get().contains( statement ) )
                {
                  return;
                }
                visited.get().add( statement );
                try
                {
                  super.visitReturnStatement( statement );
                  PsiExpression returnValue = statement.getReturnValue();
                  if( retType[0] == null )
                  {
                    retType[0] = returnValue == null ? null : returnValue.getType();
                  }
                  else
                  {
                    retType[0] = lub( retType[0], returnValue );
                  }
                }
                finally
                {
                  visited.get().remove( statement );
                }
              }
            } );
          if( retType[0] != null )
          {
            retType[0] = handleIntersectionAutoReturnType( retType[0] );
            return retType[0];
          }
        }
      }
    }
    return null;
  }

  @Override
  protected boolean canInferType( @NotNull PsiTypeElement typeElement )
  {
    if( !ManProject.isManifoldInUse( typeElement ) )
    {
      // Manifold jars are not used in the project
      return false;
    }

    String fqn = typeElement.getText();
    return fqn.equals( ManClassUtil.getShortClassName( ManAttr.AUTO_TYPE ) ) ||
      fqn.equals( ManAttr.AUTO_TYPE );
  }

  // borrowed heavily from IJ's PsiConditional
  private PsiType lub( PsiType type1, PsiExpression expr )
  {
    PsiType type2 = expr == null ? null : expr.getType();
    if( type1 == null )
    {
      return type2;
    }
    if( type2 == null )
    {
      return type1;
    }
    if (Objects.equals(type1, type2)) return type1;

    if (PsiUtil.isLanguageLevel8OrHigher( expr ) &&
      PsiPolyExpressionUtil.isPolyExpression(expr)) {
      //15.25.3 Reference Conditional Expressions
      // The type of a poly reference conditional expression is the same as its target type.
      PsiType targetType = InferenceSession.getTargetType(expr);
      if ( MethodCandidateInfo.isOverloadCheck()) {
        return targetType != null &&
          targetType.isAssignableFrom(type1) &&
          targetType.isAssignableFrom(type2) ? targetType : null;
      }
      //for standalone conditional expression try to detect target type by type of the sides
      if (targetType != null) {
        return targetType;
      }
    }

    final int typeRank1 = TypeConversionUtil.getTypeRank(type1);
    final int typeRank2 = TypeConversionUtil.getTypeRank(type2);

    // bug in JLS3, see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6888770
    if (type1 instanceof PsiClassType && type2.equals(PsiPrimitiveType.getUnboxedType(type1))) return type2;
    if (type2 instanceof PsiClassType && type1.equals(PsiPrimitiveType.getUnboxedType(type2))) return type1;

    if (TypeConversionUtil.isNumericType(typeRank1) && TypeConversionUtil.isNumericType(typeRank2)){
      if (typeRank1 == TypeConversionUtil.BYTE_RANK && typeRank2 == TypeConversionUtil.SHORT_RANK) {
        return type2 instanceof PsiPrimitiveType ? type2 : PsiPrimitiveType.getUnboxedType(type2);
      }
      if (typeRank1 == TypeConversionUtil.SHORT_RANK && typeRank2 == TypeConversionUtil.BYTE_RANK) {
        return type1 instanceof PsiPrimitiveType ? type1 : PsiPrimitiveType.getUnboxedType(type1);
      }
      if (typeRank2 == TypeConversionUtil.INT_RANK && (typeRank1 == TypeConversionUtil.BYTE_RANK || typeRank1 == TypeConversionUtil.SHORT_RANK || typeRank1 == TypeConversionUtil.CHAR_RANK)){
        if (TypeConversionUtil.areTypesAssignmentCompatible(type1, expr)) return type1;
      }
      return TypeConversionUtil.binaryNumericPromotion(type1, type2);
    }
    if (TypeConversionUtil.isNullType(type1) && !(type2 instanceof PsiPrimitiveType)) return type2;
    if (TypeConversionUtil.isNullType(type2) && !(type1 instanceof PsiPrimitiveType)) return type1;

    if (TypeConversionUtil.isAssignable(type1, type2, false)) return type1;
    if (TypeConversionUtil.isAssignable(type2, type1, false)) return type2;
    if (!PsiUtil.isLanguageLevel5OrHigher( expr )) {
      return null;
    }
    if (TypeConversionUtil.isPrimitiveAndNotNull(type1)) {
      type1 = ((PsiPrimitiveType)type1).getBoxedType( expr );
      if (type1 == null) return null;
    }
    if (TypeConversionUtil.isPrimitiveAndNotNull(type2)) {
      type2 = ((PsiPrimitiveType)type2).getBoxedType( expr );
      if (type2 == null) return null;
    }

    if (type1 instanceof PsiLambdaParameterType || type2 instanceof PsiLambdaParameterType) return null;
    final PsiType leastUpperBound = GenericsUtil.getLeastUpperBound(type1, type2, expr.getManager());
    return leastUpperBound != null ? PsiUtil.captureToplevelWildcards(leastUpperBound, expr) : null;
  }

  private PsiType handleIntersectionAutoReturnType( PsiType type )
  {
    if( type instanceof PsiIntersectionType )
    {
      PsiIntersectionType intersectionType = (PsiIntersectionType)type;
      PsiClass[] interfaces = Arrays.stream( intersectionType.getConjuncts() )
        .map( t -> t instanceof PsiClassType ? ((PsiClassType)t).resolve() : null )
        .filter( t -> t != null && t.isInterface() )
        .toArray( size -> new PsiClass[size] );
      PsiClass superClass = Arrays.stream( intersectionType.getConjuncts() )
        .map( t -> t instanceof PsiClassType ? ((PsiClassType)t).resolve() : null )
        .filter( t -> t != null && !t.isInterface() )
        .findFirst().orElse( null );

      PsiClass retType = superClass;
      if( !interfaces.isEmpty() &&
        (retType == null ||
          retType.getQualifiedName() == null ||
          retType.getQualifiedName().equals( Object.class.getTypeName() )) )
      {
        // Since an interface implicitly has Object's members, it is more relevant than Object.
        // Choose the one with the most members as a simple way to find the "best" one.

        int maxMemberCount = -1;
        for( PsiClass t : interfaces )
        {
          int methodCount = t.getMethods().length;
          if( maxMemberCount < methodCount )
          {
            maxMemberCount = methodCount;
            retType = t;
          }
        }
      }
      return PsiTypesUtil.getClassType( retType );
    }
    return type;
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

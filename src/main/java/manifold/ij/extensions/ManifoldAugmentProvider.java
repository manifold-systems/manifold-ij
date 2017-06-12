package manifold.ij.extensions;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.util.TypeConversionUtil;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import manifold.api.fs.IFile;
import manifold.api.gen.AbstractSrcMethod;
import manifold.api.gen.SrcAnnotationExpression;
import manifold.api.gen.SrcClass;
import manifold.api.gen.SrcMethod;
import manifold.api.gen.SrcParameter;
import manifold.api.gen.SrcRawStatement;
import manifold.api.gen.SrcStatementBlock;
import manifold.api.gen.SrcType;
import manifold.api.sourceprod.ISourceProducer;
import manifold.api.sourceprod.ITypeProcessor;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.fs.IjFile;
import manifold.ij.psi.ManifoldLightMethodBuilder;
import manifold.ij.psi.ManifoldPsiElementFactory;


import static manifold.api.sourceprod.ISourceProducer.ProducerKind.Supplemental;

/**
 */
public class ManifoldAugmentProvider extends PsiAugmentProvider
{
  public <E extends PsiElement> List<E> getAugments( PsiElement element, Class<E> cls )
  {
    if( DumbService.getInstance( element.getProject() ).isDumb() )
    {
      // skip processing during index rebuild
      return Collections.emptyList();
    }

    if( !(element instanceof PsiClass) || !element.isValid() || !PsiMethod.class.isAssignableFrom( cls ) )
    {
      return Collections.emptyList();
    }

    List<PsiElement> augFeatures = new ArrayList<>();
    PsiClass psiClass = (PsiClass)element;
    String className = psiClass.getQualifiedName();
    if( className == null )
    {
      return Collections.emptyList();
    }

    addMethods( className, psiClass, augFeatures );

    //noinspection unchecked
    return (List<E>)augFeatures;
  }

  private void addMethods( String fqn, PsiClass psiClass, List<PsiElement> augFeatures )
  {
    Module module = getModule( psiClass );
    if( module != null )
    {
      addMethods( fqn, psiClass, augFeatures, ManProject.getModule( module ) );
    }
    else
    {
      ManProject manProject = ManProject.manProjectFrom( psiClass.getProject() );
      for( ManModule manModule : manProject.getModules() )
      {
        addMethods( fqn, psiClass, augFeatures, manModule );
      }
    }
  }

  private void addMethods( String fqn, PsiClass psiClass, List<PsiElement> augFeatures, ManModule module )
  {
    ManModule manModule = ManProject.getModule( module.getIjModule() );
    for( ISourceProducer sp : manModule.getSourceProducers() )
    {
      if( sp.getProducerKind() == Supplemental )
      {
        if( sp.isType( fqn ) )
        {
          List<IFile> files = sp.findFilesForType( fqn );
          for( IFile file : files )
          {
            VirtualFile vFile = ((IjFile)file).getVirtualFile();
            if( !vFile.isValid() )
            {
              continue;
            }

            PsiFile psiFile = PsiManager.getInstance( module.getIjModule().getProject() ).findFile( vFile );
            PsiJavaFile psiJavaFile = (PsiJavaFile)psiFile;
            PsiClass[] classes = psiJavaFile.getClasses();
            if( classes.length > 0 )
            {
              SrcClass srcExtClass = new StubBuilder().make( classes[0].getQualifiedName(), manModule );
              SrcClass scratchClass = new SrcClass( psiClass.getQualifiedName(), psiClass.isInterface() ? SrcClass.Kind.Interface : SrcClass.Kind.Class );
              for( PsiTypeParameter tv : psiClass.getTypeParameters() )
              {
                scratchClass.addTypeVar( new SrcType( StubBuilder.makeTypeVar( tv ) ) );
              }
              for( AbstractSrcMethod m : srcExtClass.getMethods() )
              {
                SrcMethod srcMethod = addExtensionMethod( scratchClass, m, psiClass );
                if( srcMethod != null )
                {
                  PsiMethod extMethod = makePsiMethod( srcMethod, psiClass );
                  PsiMethod plantedMethod = plantMethodInPsiClass( extMethod, psiClass, classes[0] );
                  augFeatures.add( plantedMethod );
                }
              }
            }
          }
        }
      }
    }
  }

  private PsiMethod makePsiMethod( AbstractSrcMethod method, PsiClass psiClass )
  {
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance( psiClass.getProject() ).getElementFactory();
    StringBuilder sb = new StringBuilder();
    method.render( sb, 0 );
    return elementFactory.createMethodFromText( sb.toString(), psiClass );
  }

  private PsiMethod plantMethodInPsiClass( PsiMethod refMethod, PsiClass psiClass, PsiClass extClass )
  {
    if( null != refMethod )
    {
      ManifoldPsiElementFactory manPsiElemFactory = ManifoldPsiElementFactory.getInstance();
      String methodName = refMethod.getName();
      ManifoldLightMethodBuilder method = manPsiElemFactory.createLightMethod( psiClass.getManager(), methodName )
        .withMethodReturnType( refMethod.getReturnType() )
        .withContainingClass( psiClass );
      PsiElement navElem = findExtensionMethodNavigationElement( extClass, refMethod );
      if( navElem != null )
      {
        method.withNavigationElement( navElem );
      }
      addModifier( refMethod, method, PsiModifier.PUBLIC );
      addModifier( refMethod, method, PsiModifier.PACKAGE_LOCAL );
      addModifier( refMethod, method, PsiModifier.PROTECTED );

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

  private PsiElement findExtensionMethodNavigationElement( PsiClass extClass, PsiMethod plantedMethod )
  {
    PsiMethod[] found = extClass.findMethodsByName( plantedMethod.getName(), false );
    outer:
    for( PsiMethod m : found )
    {
      PsiParameter[] extParams = m.getParameterList().getParameters();
      PsiParameter[] plantedParams = plantedMethod.getParameterList().getParameters();
      if( extParams.length - 1 == plantedParams.length )
      {
        for( int i = 1; i < extParams.length; i++ )
        {
          PsiParameter extParam = extParams[i];
          PsiParameter plantedParam = plantedParams[i - 1];
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

  private void addModifier( PsiMethod psiMethod, ManifoldLightMethodBuilder method, String modifier )
  {
    if( psiMethod.hasModifierProperty( modifier ) )
    {
      method.withModifier( modifier );
    }
  }

  private SrcMethod addExtensionMethod( SrcClass srcClass, AbstractSrcMethod method, PsiClass extendedType )
  {
    if( !isExtensionMethod( method, extendedType.getQualifiedName() ) )
    {
      return null;
    }

    SrcMethod srcMethod = new SrcMethod( srcClass );
    long modifiers = method.getModifiers();
    if( extendedType.isInterface() )
    {
      // extension method must be default method in interface to not require implementation
      modifiers |= 0x80000000000L; //Flags.DEFAULT;
    }

    // remove static
    srcMethod.modifiers( modifiers & ~Modifier.STATIC );

    srcMethod.returns( method.getReturnType() );

    String name = method.getSimpleName();
    srcMethod.name( name );
    List<SrcType> typeParams = method.getTypeVariables();

    // extension method must reflect extended type's type vars before its own
    int extendedTypeVarCount = extendedType.getTypeParameterList().getTypeParameters().length;
    for( int i = extendedTypeVarCount; i < typeParams.size(); i++ )
    {
      SrcType typeVar = typeParams.get( i );
      srcMethod.addTypeVar( typeVar );
    }

    List<SrcParameter> params = method.getParameters();
    for( int i = 1; i < params.size(); i++ )
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

  private boolean isExtensionMethod( AbstractSrcMethod method, String extendedFqn )
  {
    if( !Modifier.isStatic( (int)method.getModifiers() ) || Modifier.isPrivate( (int)method.getModifiers() ) )
    {
      return false;
    }
    List<SrcParameter> params = method.getParameters();
    if( params.size() == 0 )
    {
      return false;
    }
    SrcParameter param = params.get( 0 );
    List<SrcAnnotationExpression> annotations = param.getAnnotations();
    if( annotations.size() > 0 && annotations.get( 0 ).getType().endsWith( ".This" ) )
    {
      return false;
    }
    return extendedFqn.equals( param.getType().getName() );
  }

  private Module getModule( PsiElement element )
  {
    if( element.isPhysical() )
    {
      return ProjectRootManager.getInstance( element.getProject() )
        .getFileIndex().getModuleForFile( element.getContainingFile().getVirtualFile() );
    }
    JavaFacadePsiClass javaFacadePsiClass = element.getContainingFile().getUserData( JavaFacadePsiClass.KEY_JAVAFACADE );
    if( javaFacadePsiClass != null )
    {
      return javaFacadePsiClass.getModule();
    }
    return null;
  }
}

package manifold.ij.extensions;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.source.PsiJavaFileImpl;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.impl.source.tree.java.ImplementsListElement;
import com.intellij.psi.search.GlobalSearchScope;
import java.lang.reflect.Modifier;
import java.util.List;
import manifold.ExtIssueMsg;
import manifold.api.fs.IFile;
import manifold.api.type.ITypeManifold;
import manifold.ext.ExtensionManifold;
import manifold.ext.api.Extension;
import manifold.ext.api.Structural;
import manifold.ext.api.This;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.fs.IjFile;


import static manifold.api.type.ITypeManifold.ProducerKind.Supplemental;

/**
 */
public class ExtensionClassAnnotator implements Annotator
{
  @Override
  public void annotate( PsiElement element, AnnotationHolder holder )
  {
    if( DumbService.getInstance( element.getProject() ).isDumb() )
    {
      // skip processing during index rebuild
      return;
    }

    PsiClass psiExtensionClass = findExtensionClass( element );

    if( psiExtensionClass != null )
    {
      verifyPackage( element, holder );
      verifyExtensionInterfaces( element, holder );
      verifyExtensionMethods( element, holder );
    }
  }

  private void verifyExtensionMethods( PsiElement element, AnnotationHolder holder )
  {
    if( !(element instanceof PsiMethodImpl) )
    {
      return;
    }

    PsiMethodImpl psiMethod = (PsiMethodImpl)element;

    String extendedClassName = getExtendedClassName( ((PsiJavaFile)psiMethod.getContainingFile()).getPackageName() );

    boolean thisAnnoFound = false;
    final long modifiers = StubBuilder.getModifiers( psiMethod.getModifierList() );
    PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
    for( int i = 0; i < parameters.length; i++ )
    {
      PsiParameter param = parameters[i];
      if( param.getModifierList().findAnnotation( This.class.getName() ) != null )
      {
        thisAnnoFound = true;

        if( i != 0 )
        {
          TextRange range = new TextRange( param.getTextRange().getStartOffset(),
                                           param.getTextRange().getEndOffset() );
          holder.createErrorAnnotation( range, ExtIssueMsg.MSG_THIS_FIRST.get() );
        }

        if( param.getType() instanceof PsiPrimitiveType || !param.getType().getCanonicalText().equals( extendedClassName ) )
        {
          PsiClass extendClassSym = JavaPsiFacade.getInstance( element.getProject() )
            .findClass( extendedClassName, GlobalSearchScope.allScope( element.getProject() ) );
          if( extendClassSym != null && !isStructuralInterface( extendClassSym ) ) // an extended class could be made a structural interface which results in Object as @This param, ignore this
          {
            TextRange range = new TextRange( param.getTextRange().getStartOffset(),
                                             param.getTextRange().getEndOffset() );
            holder.createErrorAnnotation( range, ExtIssueMsg.MSG_EXPECTING_TYPE_FOR_THIS.get( extendedClassName ) );
          }
        }
      }
      else if( i == 0 &&
               Modifier.isStatic( (int)modifiers ) &&
               Modifier.isPublic( (int)modifiers ) &&
               param.getType().getCanonicalText().equals( extendedClassName ) )
      {
        TextRange range = new TextRange( param.getTextRange().getStartOffset(),
                                         param.getTextRange().getEndOffset() );
        holder.createWarningAnnotation( range, ExtIssueMsg.MSG_MAYBE_MISSING_THIS.get() );
      }
    }

    if( thisAnnoFound || psiMethod.getModifierList().findAnnotation( Extension.class.getName() ) != null )
    {
      if( !Modifier.isStatic( (int)modifiers ) )
      {
        TextRange range = new TextRange( psiMethod.getNavigationElement().getTextRange().getStartOffset(),
                                         psiMethod.getNavigationElement().getTextRange().getEndOffset() );
        holder.createWarningAnnotation( range, ExtIssueMsg.MSG_MUST_BE_STATIC.get( psiMethod.getName() ) );
      }

      if( Modifier.isPrivate( (int)modifiers ) )
      {
        TextRange range = new TextRange( psiMethod.getNavigationElement().getTextRange().getStartOffset(),
                                         psiMethod.getNavigationElement().getTextRange().getEndOffset() );
        holder.createWarningAnnotation( range, ExtIssueMsg.MSG_MUST_NOT_BE_PRIVATE.get( psiMethod.getName() ) );
      }
    }
  }

  private void verifyExtensionInterfaces( PsiElement element, AnnotationHolder holder )
  {
    if( element instanceof PsiJavaCodeReferenceElementImpl &&
        ((PsiJavaCodeReferenceElementImpl)element).getTreeParent() instanceof ImplementsListElement )
    {
      final PsiElement resolve = element.getReference().resolve();
      if( resolve instanceof PsiExtensibleClass )
      {
        PsiExtensibleClass iface = (PsiExtensibleClass)resolve;
        if( !isStructuralInterface( iface ) )
        {
          TextRange range = new TextRange( element.getTextRange().getStartOffset(),
                                           element.getTextRange().getEndOffset() );
          holder.createErrorAnnotation( range, ExtIssueMsg.MSG_ONLY_STRUCTURAL_INTERFACE_ALLOWED_HERE.get( iface.getName() ) );
        }
      }
    }
  }

  private boolean isStructuralInterface( PsiClass iface )
  {
    return iface.getModifierList().findAnnotation( Structural.class.getName() ) != null ||
           isInterfaceMadeStructuralByExtension( iface );
  }

  private void verifyPackage( PsiElement element, AnnotationHolder holder )
  {
    if( !(element instanceof PsiPackageStatement) )
    {
      return;
    }

    String packageName = ((PsiPackageStatement)element).getPackageName();
    if( !packageName.startsWith( ExtensionManifold.EXTENSIONS_PACKAGE + '.' ) )
    {
      TextRange range = new TextRange( element.getTextRange().getStartOffset(),
                                       element.getTextRange().getEndOffset() );
      holder.createErrorAnnotation( range, ExtIssueMsg.MSG_EXPECTING_EXTENSIONS_ROOT_PACKAGE.get( getPackageRoot( packageName ) ) );
    }
    else
    {
      String extendedClassName = getExtendedClassName( packageName );
      if( extendedClassName.isEmpty() &&
          JavaPsiFacade.getInstance( element.getProject() )
            .findClass( extendedClassName, GlobalSearchScope.allScope( element.getProject() ) ) == null )
      {
        TextRange range = new TextRange( element.getTextRange().getStartOffset(),
                                         element.getTextRange().getEndOffset() );
        holder.createErrorAnnotation( range, ExtIssueMsg.MSG_EXPECTING_EXTENDED_CLASS_NAME.get( getPackageRoot( extendedClassName ) ) );
      }
    }
  }

  private String getPackageRoot( String packageName )
  {
    int iDot = packageName.indexOf( '.' );
    if( iDot < 0 )
    {
      return packageName;
    }
    return packageName.substring( 0, iDot );
  }

  private String getExtendedClassName( String packageName )
  {
    int iDot = packageName.indexOf( '.' );
    if( iDot < 0 )
    {
      return "";
    }
    return packageName.substring( iDot + 1 );
  }

  private boolean isInterfaceMadeStructuralByExtension( PsiClass psiExtentionInterface )
  {
    Module module = ManProject.getIjModule( psiExtentionInterface );
    if( module != null )
    {
      if( isInterfaceMadeStructuralByExtension( psiExtentionInterface, ManProject.getModule( module ) ) )
      {
        return true;
      }
    }
    else
    {
      ManProject manProject = ManProject.manProjectFrom( psiExtentionInterface.getProject() );
      for( ManModule manModule : manProject.getModules() )
      {
        if( isInterfaceMadeStructuralByExtension( psiExtentionInterface, manModule ) )
        {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isInterfaceMadeStructuralByExtension( PsiClass psiInterface, ManModule module )
  {
    final String fqn = psiInterface.getQualifiedName();
    ManModule manModule = ManProject.getModule( module.getIjModule() );
    for( ITypeManifold sp : manModule.getTypeManifolds() )
    {
      if( sp.getProducerKind() == Supplemental )
      {
        if( sp.isType( fqn ) )
        {
          List<IFile> files = sp.findFilesForType( fqn );
          for( IFile file : files )
          {
            VirtualFile vExtensionClassFile = ((IjFile)file).getVirtualFile();
            if( !vExtensionClassFile.isValid() )
            {
              continue;
            }

            PsiJavaFile psiExtClassJavaFile =
              (PsiJavaFile)PsiManager.getInstance( module.getIjModule().getProject() ).findFile( vExtensionClassFile );
            PsiClass[] classes = psiExtClassJavaFile.getClasses();
            if( classes.length > 0 )
            {
              PsiClass psiExtClass = classes[0];
              if( psiExtClass.getModifierList().findAnnotation( Structural.class.getName() ) != null )
              {
                return true;
              }
            }
          }
        }
      }
    }
    return false;
  }

  private PsiClass findExtensionClass( PsiElement element )
  {
    PsiFile containingFile = element.getContainingFile();
    if( !(containingFile instanceof PsiJavaFileImpl) )
    {
      return null;
    }

    PsiJavaFileImpl file = (PsiJavaFileImpl)containingFile;
    for( PsiClass psiClass : file.getClasses() )
    {
      if( psiClass.getModifierList().findAnnotation( Extension.class.getName() ) != null )
      {
        return psiClass;
      }
    }

    return null;
  }
}

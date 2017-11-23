package manifold.ij.extensions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import manifold.api.type.ITypeManifold;
import manifold.api.type.SourcePosition;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.fs.IjFile;
import manifold.ij.util.FileUtil;
import org.jetbrains.annotations.NotNull;

/**
 */
public class ResourceToManifoldUtil
{
  /**
   * Find the Manifold PisClass corresponding with a resource file.
   * @param fileElem a psiFile, normally this should be a resource file
   * @return The corresponding Manifold PsiClass or null
   */
  public static PsiClass findPsiClass( PsiFileSystemItem fileElem )
  {
    Project project = fileElem.getProject();
    ManProject manProject = ManProject.manProjectFrom( project );
    VirtualFile virtualFile = fileElem.getVirtualFile();
    if( virtualFile == null )
    {
      return null;
    }
    for( ManModule module : manProject.getModules() )
    {
      IjFile file = FileUtil.toIFile( manProject, virtualFile );
      Set<ITypeManifold> set = module.findTypeManifoldsFor( file );
      if( set != null )
      {
        for( ITypeManifold tf : set )
        {
          if( tf.getProducerKind() == ITypeManifold.ProducerKind.Primary )
          {
            String[] fqns = tf.getTypesForFile( file );
            for( String fqn : fqns )
            {
              PsiClass psiClass = ManifoldPsiClassCache.instance().getPsiClass( GlobalSearchScope.moduleWithDependenciesAndLibrariesScope( module.getIjModule() ), module, fqn );
              if( psiClass != null )
              {
                return psiClass;
              }
            }
          }
        }
      }
    }
    return null;
  }

  /**
   * Given a PsiElement in a resource file, find the corresponding declared member[s] in the Manifold generated PsiClass for the resource file.
   * Note this functionality depends on the generated PsiClass annotating its members with @SourcePosition.
   *
   * @param element An element inside a resource file (or the psiFile itself) that corresponds with a declared field, method, or inner class
   *                inside a Manifold PsiClass or the PsiClass itself.
   * @return The declared Java PsiElement[s] inside the Manifold PsiClass corresponding with the resource file element.  Note there can be more
   *   than one PsiElement i.e., when the resource element is a "property" and has corresponding "getter" and "setter" methods in the PsiClass.
   */
  public static List<PsiElement> findJavaElementsFor( @NotNull PsiElement element )
  {
    if( element instanceof ManifoldPsiClass )
    {
      if( ((ManifoldPsiClass)element).getContainingClass() != null )
      {
        return Collections.singletonList( element );
      }
      return Collections.emptyList();
    }

    if( element instanceof PsiFile )
    {
      PsiClass psiClass = findPsiClass( (PsiFile)element );
      if( psiClass != null )
      {
        return Collections.singletonList( psiClass );
      }
    }

    List<PsiElement> result = new ArrayList<>();

    Project project = element.getProject();
    ManProject manProject = ManProject.manProjectFrom( project );
    PsiFile containingFile = element.getContainingFile();
    VirtualFile virtualFile = containingFile == null ? null : containingFile.getVirtualFile();
    if( virtualFile == null )
    {
      return Collections.emptyList();
    }
    for( ManModule module : manProject.getModules() )
    {
      IjFile file = FileUtil.toIFile( manProject, virtualFile );
      Set<ITypeManifold> set = module.findTypeManifoldsFor( file );
      if( set != null )
      {
        for( ITypeManifold tf : set )
        {
          if( tf.getProducerKind() == ITypeManifold.ProducerKind.Primary )
          {
            String[] fqns = tf.getTypesForFile( file );
            for( String fqn : fqns )
            {
              PsiClass psiClass = ManifoldPsiClassCache.instance().getPsiClass( GlobalSearchScope.moduleWithDependenciesAndLibrariesScope( module.getIjModule() ), module, fqn );
              if( psiClass != null )
              {
                result.addAll( findJavaElementsFor( psiClass, element ) );
              }
            }
          }
        }
      }
    }
    return result;
  }

  private static List<PsiElement> findJavaElementsFor( PsiClass psiClass, PsiElement element )
  {
    List<PsiElement> result = new ArrayList<>();
    PsiMethod[] methods = psiClass.getMethods();
    for( PsiMethod method : methods )
    {
      if( isJavaElementFor( method, element ) )
      {
        result.add( method );
      }
    }
    PsiField[] fields = psiClass.getFields();
    for( PsiField field : fields )
    {
      if( isJavaElementFor( field, element ) )
      {
        result.add( field );
      }
    }
    PsiClass[] inners = psiClass.getInnerClasses();
    for( PsiClass inner : inners )
    {
      if( isJavaElementFor( inner, element ) )
      {
        result.add( inner );
      }
      result.addAll( findJavaElementsFor( inner, element ) );
    }
    return result;
  }

  private static boolean isJavaElementFor( PsiModifierListOwner modifierListOwner, PsiElement element )
  {
    PsiAnnotation annotation = modifierListOwner.getModifierList().findAnnotation( SourcePosition.class.getName() );
    if( annotation != null )
    {
      int textOffset = element.getTextOffset();
      int textLength = element instanceof PsiNamedElement ? ((PsiNamedElement)element).getName().length() : element.getTextLength();
      PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
      int offset = -1;
      for( PsiNameValuePair pair : attributes )
      {
        if( pair.getNameIdentifier().getText().equals( SourcePosition.OFFSET ) )
        {
          offset = Integer.parseInt( pair.getLiteralValue() );
          break;
        }
      }
      if( offset >= textOffset && offset <= textOffset + textLength )
      {
        return true;
      }
    }
    return false;
  }
}

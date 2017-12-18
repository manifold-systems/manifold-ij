package manifold.ij.extensions;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
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
import com.intellij.psi.PsiPlainText;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.search.GlobalSearchScope;
import java.awt.KeyboardFocusManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import manifold.api.type.ITypeManifold;
import manifold.api.type.SourcePosition;
import manifold.api.type.TypeReference;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.fs.IjFile;
import manifold.ij.util.FileUtil;
import org.jetbrains.annotations.NotNull;

/**
 */
public class ResourceToManifoldUtil
{
  public static final Key<FeaturePath> KEY_FEATURE_PATH = new Key<>( "FeaturePath" );

  /**
   * Find the Manifold PisClass corresponding with a resource file.
   *
   * @param fileElem a psiFile, normally this should be a resource file
   *
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
   *
   * @return The declared Java PsiElement[s] inside the Manifold PsiClass corresponding with the resource file element.  Note there can be more
   * than one PsiElement i.e., when the resource element is a "property" and has corresponding "getter" and "setter" methods in the PsiClass.
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

    if( element instanceof PsiPlainText )
    {
      // can happen in tests
      element = element.getContainingFile();
    }

    if( element instanceof PsiFile )
    {
      if( element instanceof PsiPlainTextFile && isFileOpenInCurrentEditor( (PsiFile)element ) )
      {
        element = findFakePlainTextElement( (PsiPlainTextFile)element );
        if( element == null )
        {
          return Collections.emptyList();
        }
      }
      else
      {
        PsiClass psiClass = findPsiClass( (PsiFile)element );
        if( psiClass != null )
        {
          return Collections.singletonList( psiClass );
        }
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
                if( PsiErrorClassUtil.isErrorClass( psiClass ) )
                {
                  result.add( psiClass );
                }
                else
                {
                  result.addAll( findJavaElementsFor( psiClass, element ) );
                }
              }
            }
          }
        }
      }
    }
    return result;
  }

  static PsiElement findFakePlainTextElement( PsiPlainTextFile psiTextFile )
  {
    try
    {
      int start = getWordAtCaretStart( psiTextFile.getProject(), psiTextFile.getVirtualFile().getPath() );
      if( start < 0 )
      {
        return null;
      }

      int end = getWordAtCaretEnd( psiTextFile.getProject(), psiTextFile.getVirtualFile().getPath() );
      if( end < 0 )
      {
        return null;
      }

      String name = psiTextFile.getText().substring( start, end );

      return new FakeTargetElement( psiTextFile, start, end - start, name );
    }
    catch( Exception e )
    {
      return null;
    }
  }

  private static boolean isFileOpenInCurrentEditor( PsiFile file )
  {
    Editor editor = getActiveEditor( file.getProject() );
    return editor instanceof EditorImpl && ((EditorImpl)editor).getVirtualFile().getPath().equals( file.getVirtualFile().getPath() );
  }

  private static int getWordAtCaretStart( Project project, String filePath )
  {
    Editor[] editor = new Editor[1];
    editor[0] = getActiveEditor( project );
    if( !(editor[0] instanceof EditorImpl) )
    {
      return -1;
    }

    EditorImpl editorImpl = (EditorImpl)editor[0];
    if( !editorImpl.getVirtualFile().getPath().equals( filePath ) )
    {
      return -1;
    }

    CaretModel cm = editorImpl.getCaretModel();
    Document document = editorImpl.getDocument();
    int offset = cm.getOffset();
    if( offset == 0 )
    {
      return 0;
    }
    int lineNumber = cm.getLogicalPosition().line;
    int newOffset = offset - 1;
    int minOffset = lineNumber > 0 ? document.getLineEndOffset( lineNumber - 1 ) : 0;
    for( ; newOffset > minOffset; newOffset-- )
    {
      if( EditorActionUtil.isWordOrLexemeStart( editorImpl, newOffset, false ) )
      {
        break;
      }
    }

    return newOffset;
  }

  private static int getWordAtCaretEnd( Project project, String filePath )
  {
    Editor[] editor = new Editor[1];
    editor[0] = getActiveEditor( project );
    if( !(editor[0] instanceof EditorImpl) )
    {
      return -1;
    }

    EditorImpl editorImpl = (EditorImpl)editor[0];
    if( !editorImpl.getVirtualFile().getPath().equals( filePath ) )
    {
      return -1;
    }

    CaretModel cm = editorImpl.getCaretModel();
    Document document = editorImpl.getDocument();
    int offset = cm.getOffset();

    if( offset >= document.getTextLength() - 1 || document.getLineCount() == 0 )
    {
      return offset;
    }

    int newOffset = offset + 1;

    int lineNumber = cm.getLogicalPosition().line;
    int maxOffset = document.getLineEndOffset( lineNumber );
    if( newOffset > maxOffset )
    {
      if( lineNumber + 1 >= document.getLineCount() )
      {
        return offset;
      }
      maxOffset = document.getLineEndOffset( lineNumber + 1 );
    }
    for( ; newOffset < maxOffset; newOffset++ )
    {
      if( EditorActionUtil.isWordOrLexemeEnd( editorImpl, newOffset, false ) )
      {
        break;
      }
    }

    return newOffset;
  }

  private static List<PsiElement> findJavaElementsFor( PsiClass psiClass, PsiElement element )
  {
    return findJavaElementsFor( psiClass, element, new FeaturePath( psiClass ) );
  }

  private static List<PsiElement> findJavaElementsFor( PsiClass psiClass, PsiElement element, FeaturePath parent )
  {
    List<PsiElement> result = new ArrayList<>();

    if( isJavaElementFor( psiClass, element ) )
    {
      result.add( psiClass );
      psiClass.putUserData( KEY_FEATURE_PATH, FeaturePath.make( parent, FeaturePath.FeatureType.Class, 0, 1 ) );
    }

    PsiMethod[] methods = psiClass.getMethods();
    for( int i = 0; i < methods.length; i++ )
    {
      PsiMethod method = methods[i];
      if( isJavaElementFor( method, element ) ||
          element instanceof PsiClass && isJavaElementForType( method, (PsiClass)element ) )
      {
        result.add( method );
        method.putUserData( KEY_FEATURE_PATH, FeaturePath.make( parent, FeaturePath.FeatureType.Method, i, methods.length ) );
      }
    }
    PsiField[] fields = psiClass.getFields();
    for( int i = 0; i < fields.length; i++ )
    {
      PsiField field = fields[i];
      if( isJavaElementFor( field, element ) ||
          element instanceof PsiClass && isJavaElementForType( field, (PsiClass)element ) )
      {
        result.add( field );
        field.putUserData( KEY_FEATURE_PATH, FeaturePath.make( parent, FeaturePath.FeatureType.Field, i, fields.length ) );
      }
    }
    PsiClass[] inners = psiClass.getInnerClasses();
    for( int i = 0; i < inners.length; i++ )
    {
      PsiClass inner = inners[i];
      if( isJavaElementFor( inner, element ) ||
          element instanceof PsiClass && isJavaElementForType( inner, (PsiClass)element ) )
      {
        result.add( inner );
        inner.putUserData( KEY_FEATURE_PATH, FeaturePath.make( parent, FeaturePath.FeatureType.Class, i, inners.length ) );
      }
      result.addAll( findJavaElementsFor( inner, element, new FeaturePath( parent, FeaturePath.FeatureType.Class, i, inners.length ) ) );
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
          String literalValue = pair.getLiteralValue();
          if( literalValue == null )
          {
            return false;
          }
          offset = Integer.parseInt( literalValue );
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

  private static boolean isJavaElementForType( PsiModifierListOwner modifierListOwner, PsiClass psiClass )
  {
    PsiAnnotation annotation = modifierListOwner.getModifierList().findAnnotation( TypeReference.class.getName() );
    if( annotation != null )
    {
      PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
      for( PsiNameValuePair pair : attributes )
      {
        String fqn = pair.getLiteralValue();
        if( psiClass.getQualifiedName().contains( fqn ) )
        {
          return true;
        }
      }
    }
    return false;
  }

  public static Editor getActiveEditor( Project project )
  {
    if( FileEditorManager.getInstance( project ) instanceof FileEditorManagerImpl )
    {
      // get the active editor without having to use the dispatch thread, which otherwise can cause deadlock
      return DataManager.getInstance().getDataContext( KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner() ).getData( PlatformDataKeys.EDITOR );
    }
    else
    {
      return FileEditorManager.getInstance( project ).getSelectedTextEditor();
    }
  }
}

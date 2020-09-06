package manifold.ij.extensions;

import com.intellij.ide.DataManager;
import com.intellij.json.psi.JsonProperty;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
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
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Query;
import java.awt.KeyboardFocusManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import manifold.api.fs.IFile;
import manifold.api.fs.IFileFragment;
import manifold.api.type.ContributorKind;
import manifold.api.type.ITypeManifold;
import manifold.rt.api.SourcePosition;
import manifold.rt.api.TypeReference;
import manifold.ext.IExtensionClassProducer;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.fs.IjFile;
import manifold.ij.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 */
public class ResourceToManifoldUtil
{
  static final Key<FeaturePath> KEY_FEATURE_PATH = new Key<>( "FeaturePath" );

  /**
   * Find the Manifold PisClasses corresponding with a resource file.
   *
   * @param fileElem a psiFile, normally this should be a resource file
   *
   * @return The corresponding Manifold PsiClass[es]
   */
  public static Set<PsiClass> findPsiClass( PsiFileSystemItem fileElem )
  {
    Project project = fileElem.getProject();
    ManProject manProject = ManProject.manProjectFrom( project );
    VirtualFile virtualFile = fileElem.getVirtualFile();
    if( virtualFile == null )
    {
      return Collections.emptySet();
    }

    Set<PsiClass> result = new HashSet<>();
    IjFile file = FileUtil.toIFile( manProject, virtualFile );
    Set<ITypeManifold> tms = ManModule.findTypeManifoldsForFile( manProject.getNativeProject(), file,
      tm -> tm.getContributorKind() == ContributorKind.Primary,
      tm -> tm.getContributorKind() == ContributorKind.Primary );
    for( ITypeManifold tm : tms )
    {
      String[] fqns = tm.getTypesForFile( file );
      for( String fqn : fqns )
      {
        PsiClass psiClass = JavaPsiFacade.getInstance( fileElem.getProject() )
          .findClass( fqn, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope( ((ManModule)tm.getModule()).getIjModule() ) );
        if( psiClass != null )
        {
          result.add( psiClass );
        }
      }
    }
    return result;
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
  public static Set<PsiModifierListOwner> findJavaElementsFor( @NotNull PsiElement element )
  {
    return findJavaElementsFor( element, new HashSet<>() );
  }
  private static Set<PsiModifierListOwner> findJavaElementsFor( @NotNull PsiElement element, Set<PsiElement> visited )
  {
    if( visited.contains( element ) )
    {
      return Collections.emptySet();
    }
    visited.add( element );

    if( element instanceof ManifoldPsiClass )
    {
      if( ((ManifoldPsiClass)element).getContainingClass() != null )
      {
        return Collections.singleton( (ManifoldPsiClass)element );
      }
      return Collections.emptySet();
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
          return Collections.emptySet();
        }
      }
      else
      {
        //noinspection unchecked
        return (Set)findPsiClass( (PsiFile)element );
      }
    }

    Set<PsiModifierListOwner> result = new HashSet<>();

    Project project = element.getProject();
    ManProject manProject = ManProject.manProjectFrom( project );
    IFile file = fileFromElement( element, manProject );
    if( file == null )
    {
      return Collections.emptySet();
    }
    Set<ITypeManifold> set = ManModule.findTypeManifoldsForFile( manProject.getNativeProject(), file,
      tm ->  tm.getContributorKind() == ContributorKind.Primary,
      tm -> tm.getContributorKind() == ContributorKind.Primary );
    for( ITypeManifold tm : set )
    {
      Collection<String> fqns = findTypesForFile( tm, file );
      for( String fqn : fqns )
      {
        PsiClass psiClass = JavaPsiFacade.getInstance( manProject.getNativeProject() )
          .findClass( fqn, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope( ((ManModule)tm.getModule()).getIjModule() ) );
        if( psiClass != null )
        {
          if( PsiErrorClassUtil.isErrorClass( psiClass ) )
          {
            result.add( psiClass );
          }
          else
          {
            result.addAll( findJavaElementsFor( psiClass, file, element ) );
          }
        }
      }
    }

    // Find Java elements from references to the element.  For example, in the "JS GraphQL" plugin a query field
    // references a type field -- the fields are considered the same -- therefore make sure Java references to the query
    // field ref are also accounted for.
    Module moduleForPsiElement = ModuleUtilCore.findModuleForPsiElement( element );
    Query<PsiReference> search = ReferencesSearch.search( element,
      moduleForPsiElement == null
      ? GlobalSearchScope.projectScope( project )
      : GlobalSearchScope.moduleScope( moduleForPsiElement ) );
    for( PsiReference ref: searchForElement( search ) )
    {
      if( ref.getElement() instanceof JsonProperty )
      {
        //## hack: IJ's json parser considers all properties having the same name as the same reference, which is total crap
        continue;
      }
      result.addAll( findJavaElementsFor( ref.getElement(), visited ) );
    }

    return result;
  }

  // Handle cases like XML files where IntelliJ overlays the file with a fake DTD e.g., Foo.xml is masked by a Foo.xml.dtd
  // which is not a physical file, therefore element.getContainingFile().getVirtualFile() is either null or "light" and
  // does not provide any means to get the actual Foo.xml file, so we are stuck with finding Foo.xml using this method,
  // which finds usages of the fake dtd element, which are the actual XML file elements. From there we get the actual file.
  private static VirtualFile findFileByLocalUsagesOfElem( PsiElement element )
  {
    if( element.getContainingFile() != null && element.getContainingFile().isPhysical() )
    {
      return null;
    }

    Query<PsiReference> search = ReferencesSearch.search( element, GlobalSearchScope.projectScope( element.getProject() ) );
    Collection<PsiReference> psiReferences = ResourceToManifoldUtil.searchForElement( search );
    if( !psiReferences.isEmpty() )
    {
      PsiFile containingFile = psiReferences.iterator().next().getElement().getContainingFile();
      return containingFile == null ? null : containingFile.getVirtualFile();
    }
    return null;
  }

  public static PsiElement findPhysicalElementFor( PsiElement element )
  {
    if( element.getContainingFile() != null && element.getContainingFile().isPhysical() )
    {
      return element;
    }

    // element is not physical e.g., for XML intellij creates a shadow psi tree with a fake DTD/XSD

    // Find references to this non-physical element with the hope that a local reference will be the physical element underlying it

    Query<PsiReference> search = ReferencesSearch.search( element, GlobalSearchScope.projectScope( element.getProject() ) );
    Collection<PsiReference> psiReferences = ResourceToManifoldUtil.searchForElement( search );
    PsiElement physicalElem = null;
    if( !psiReferences.isEmpty() )
    {
      for( PsiReference ref: psiReferences )
      {
        if( physicalElem == null )
        {
          physicalElem = ref.getElement();
        }
        else if( ref.getElement().getTextOffset() < physicalElem.getTextOffset() )
        {
          physicalElem = ref.getElement();
        }
      }
    }
    return physicalElem;
  }

  @NotNull
  public static Collection<PsiReference> searchForElement( Query<PsiReference> search )
  {
    if( ApplicationManager.getApplication().isDispatchThread() )
    {
      return search.findAll();
    }
    return ProgressManager.getInstance().runProcess( () -> search.findAll(), new EmptyProgressIndicator() );
  }

  @Nullable
  private static IFile fileFromElement( PsiElement element, ManProject manProject )
  {
    PsiFile containingFile = element.getContainingFile();
    if( containingFile == null )
    {
      return null;
    }
    PsiElement fileContext = FileContextUtil.getFileContext( containingFile );
    IFile file;
    if( fileContext instanceof PsiFileFragment )
    {
      // account for fragments
      file = ((PsiFileFragment)fileContext).getFragment();
    }
    else
    {
      VirtualFile virtualFile = containingFile.getVirtualFile();
      if( virtualFile == null )
      {
        virtualFile = findFileByLocalUsagesOfElem( element );
      }
      file = virtualFile == null ? null : FileUtil.toIFile( manProject, virtualFile );
    }
    return file;
  }

  private static Collection<String> findTypesForFile( ITypeManifold tf, IFile file )
  {
    Set<String> fqns = new HashSet<>();
    fqns.addAll( Arrays.stream( tf.getTypesForFile( file ) ).collect( Collectors.toSet() ) );

    // Also include types extended via IExtensoinClassProviders
    if( tf instanceof IExtensionClassProducer )
    {
      fqns.addAll( ((IExtensionClassProducer)tf).getExtendedTypesForFile( file ) );
    }
    return fqns;
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

      return new FakeTargetElement( psiTextFile, start, end - start, name, SourcePosition.DEFAULT_KIND );
    }
    catch( Exception e )
    {
      return null;
    }
  }

  private static boolean isFileOpenInCurrentEditor( PsiFile file )
  {
    Editor editor = getActiveEditor( file.getProject() );
    if( editor == null )
    {
      return false;
    }

    VirtualFile editorFile = FileDocumentManager.getInstance().getFile( editor.getDocument() );
    return editorFile != null && editorFile.getPath().equals( file.getVirtualFile().getPath() );

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
    int newOffset = offset;
    int minOffset = lineNumber > 0 ? document.getLineEndOffset( lineNumber - 1 ) : 0;
    CharSequence content = editorImpl.getDocument().getCharsSequence();
    for( ; newOffset > minOffset; newOffset-- )
    {
      if( Character.isJavaIdentifierStart( content.charAt( newOffset ) ) &&
          !Character.isJavaIdentifierStart( content.charAt( newOffset - 1 ) ) )
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

    int newOffset = offset;

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

    CharSequence content = editorImpl.getDocument().getCharsSequence();
    for( ; newOffset < maxOffset; newOffset++ )
    {
      if( !Character.isJavaIdentifierPart( content.charAt( newOffset ) ) )
      {
        break;
      }
    }

    return newOffset;
  }

  private static List<PsiModifierListOwner> findJavaElementsFor( PsiClass psiClass, IFile file, PsiElement element )
  {
    psiClass.putUserData( KEY_FEATURE_PATH, null );
    return findJavaElementsFor( psiClass, file, element, new FeaturePath( psiClass ) );
  }

  private static List<PsiModifierListOwner> findJavaElementsFor( PsiClass psiClass, IFile file, PsiElement element, FeaturePath parent )
  {
    List<PsiModifierListOwner> result = new ArrayList<>();

    if( isJavaElementFor( psiClass, file, psiClass, element ) && psiClass.getUserData( KEY_FEATURE_PATH ) == null )
    {
      result.add( psiClass );
      psiClass.putUserData( KEY_FEATURE_PATH, FeaturePath.make( parent, FeaturePath.FeatureType.Class, 0, 1 ) );
    }

    PsiMethod[] methods = psiClass.getMethods();
    for( int i = 0; i < methods.length; i++ )
    {
      PsiMethod method = methods[i];
      if( isJavaElementFor( psiClass, file, method, element ) ||
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
      if( isJavaElementFor( psiClass, file, field, element ) ||
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
      if( isJavaElementFor( psiClass, file, inner, element ) ||
          element instanceof PsiClass && isJavaElementForType( inner, (PsiClass)element ) )
      {
        result.add( inner );
        inner.putUserData( KEY_FEATURE_PATH, FeaturePath.make( parent, FeaturePath.FeatureType.Class, i, inners.length ) );
      }
      result.addAll( findJavaElementsFor( inner, file, element, new FeaturePath( parent, FeaturePath.FeatureType.Class, i, inners.length ) ) );
    }

    return result;
  }

  /**
   * Does the Java source member, {@code modifierListOwner}, have a SourcePosition annotation that matches the
   * name and location of the given {@code element}?
   */
  private static boolean isJavaElementFor( PsiClass declaringClass, IFile file, PsiModifierListOwner modifierListOwner, PsiElement element )
  {
    String targetFeatureName = element.getText();
    if( targetFeatureName == null || targetFeatureName.isEmpty() )
    {
      return false;
    }

    PsiAnnotation annotation = modifierListOwner.getModifierList().findAnnotation( SourcePosition.class.getName() );
    if( annotation != null )
    {
      int textOffset = 0;
      if( file instanceof IFileFragment )
      {
        textOffset = ((IFileFragment)file).getOffset();
      }
      textOffset += element.getTextOffset();
      int textLength = element instanceof PsiNamedElement && ((PsiNamedElement)element).getName() != null
                       ? ((PsiNamedElement)element).getName().length()
                       : element.getTextLength();
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
        }
        else if( pair.getNameIdentifier().getText().equals( SourcePosition.FEATURE ) )
        {
          String featureName = pair.getLiteralValue();
          if( !featureNameMatches( declaringClass, element, featureName ) )
          {
            return false;
          }
        }
      }
      return offset >= textOffset && offset <= textOffset + textLength;
    }
    return false;
  }

  private static boolean featureNameMatches( PsiClass declaringClass, PsiElement element, String featureName )
  {
    return featureName != null &&
           (featureName.equals( element.getText() ) ||
            (element instanceof NavigationItem && isSame( declaringClass, ((NavigationItem)element).getName(), featureName )) ||
            (element instanceof PsiNamedElement && isSame( declaringClass, ((PsiNamedElement)element).getName(), featureName )));
  }

  private static boolean isSame( PsiClass declaringClass, String elemName, String featureName )
  {
    return featureName.equals( elemName ) ||
           (declaringClass != null &&
            elemName != null &&
            (featureName.equals( declaringClass.getQualifiedName() + '.' + elemName ) ||
             elemName.endsWith( '.' + featureName ) ||
             isSame( declaringClass.getContainingClass(), elemName, featureName )));
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

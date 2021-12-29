package manifold.ij.extensions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiPlainText;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.impl.light.AbstractLightClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.refactoring.rename.RenamePsiFileProcessor;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Query;
import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This processor renames a Manifold *type* reference in source code.  It extends the psi File processor
 * so it can substitute a referenced Manifold type with the type's corresponding resource file; the base class
 * renames the file.  This class also collects all code references to the Manifold type so that it can later rename
 * them in its getPostRenameCallback() override.  Additionally, this class creates its own rename dialog so
 * it can select only the file base name in the text field; it excludes the file extension to make it clearer
 * only the name should be changed.
 */
public class RenameTypeManifoldFileProcessor extends RenamePsiFileProcessor
{
  private List<UsageInfo> _usages = Collections.emptyList();
  private SmartPsiElementPointer<PsiNamedElement> _classDeclElement;

  @Override
  public boolean canProcessElement( @NotNull PsiElement element )
  {
    if( !ManProject.isManifoldInUse( element ) )
    {
      return false;
    }

    return !isElementInsidePlainTextFile( element ) &&
           super.canProcessElement( maybeGetResourceFile( element ) );
  }

  private boolean isElementInsidePlainTextFile( PsiElement element )
  {
    if( element instanceof PsiPlainText )
    {
      element = element.getContainingFile();
    }

    if( element instanceof PsiPlainTextFile )
    {
      PsiElement fakeElement = ResourceToManifoldUtil.findFakePlainTextElement( (PsiPlainTextFile)element );
      return fakeElement != null && !(fakeElement instanceof PsiPlainTextFile) && !isTopLevelClassDeclaration( fakeElement );

    }
    return false;
  }

  private boolean isTopLevelClassDeclaration( PsiElement fakeElement )
  {
    Set<PsiModifierListOwner> javaElems = ResourceToManifoldUtil.findJavaElementsFor( fakeElement );
    return javaElems.size() == 1 &&
           javaElems.iterator().next() instanceof PsiClass &&
           ((PsiClass)javaElems.iterator().next()).getContainingClass() == null;
  }

  @Override
  public boolean isInplaceRenameSupported()
  {
    return false;
  }

  @Nullable
  @Override
  public PsiElement substituteElementToRename( @NotNull PsiElement element, @Nullable Editor editor )
  {
    if( !ManProject.isManifoldInUse( element ) )
    {
      return super.substituteElementToRename( element, editor );
    }

    return maybeGetResourceFile( element );
  }

  @NotNull
  @Override
  public RenameDialog createRenameDialog( @NotNull Project project, @NotNull PsiElement element, PsiElement nameSuggestionContext, Editor editor )
  {
    if( !ManProject.isManifoldInUse( project ) )
    {
      return super.createRenameDialog( project, element, nameSuggestionContext, editor );
    }

    return new PsiFileRenameDialog( project, element, nameSuggestionContext, editor )
    {
      protected void createNewNameComponent()
      {
        super.createNewNameComponent();
        selectNameWithoutExtension();
      }

      private void selectNameWithoutExtension()
      {
        EventQueue.invokeLater( () -> {
          Editor editor = getNameSuggestionsField().getEditor();
          if( editor == null )
          {
            return;
          }

          Module moduleForPsiElement = ModuleUtilCore.findModuleForPsiElement( element );
          if( moduleForPsiElement == null )
          {
            return;
          }

          ManModule module = ManProject.getModule( moduleForPsiElement );
          if( module == null )
          {
            return;
          }

          PsiClass psiClass = findPsiClass( (PsiFileSystemItem)element, module );
          if( psiClass != null )
          {
            String className = psiClass.getName();
            if( className != null )
            {
              int indexName = editor.getDocument().getText().indexOf( className );
              if( indexName >= 0 )
              {
                editor.getSelectionModel().setSelection( indexName, indexName + className.length() );
                editor.getCaretModel().moveToOffset( indexName );
                return;
              }
            }
          }

          int pos = editor.getDocument().getText().lastIndexOf( '.' );
          if( pos > 0 )
          {
            editor.getSelectionModel().setSelection( 0, pos );
            editor.getCaretModel().moveToOffset( pos );
          }
        } );

      }

      @Override
      protected NameSuggestionsField getNameSuggestionsField()
      {
        return super.getNameSuggestionsField();
      }
    };
  }

  private PsiElement maybeGetResourceFile( PsiElement element )
  {
    ManifoldPsiClass manifoldClass = getManifoldClass( element );
    if( manifoldClass != null )
    {
      // Must refactor the corresponding resource file...

      List<PsiFile> rawFiles = manifoldClass.getRawFiles();
      if( rawFiles.size() == 1 )
      {
        //## todo; ?
        element = rawFiles.get( 0 );
      }
    }
    return element;
  }

  private ManifoldPsiClass getManifoldClass( PsiElement element )
  {
    if( element instanceof ManifoldPsiClass )
    {
      ManifoldPsiClass manClass = (ManifoldPsiClass)element;
      return manClass.getContainingClass() == null ? manClass : null;
    }
    if( element instanceof AbstractLightClass )
    {
      return getManifoldClass( ((AbstractLightClass)element).getDelegate() );
    }
    return null;
  }

  @NotNull
  @Override
  public Collection<PsiReference> findReferences( @NotNull PsiElement element, @NotNull SearchScope scope, boolean commentsAndStrings )
  {
    if( !ManProject.isManifoldInUse( element ) )
    {
      return Collections.emptySet();
    }

    Collection<PsiReference> references = super.findReferences( element, scope, commentsAndStrings );

    // Store refs to manifold types
    storeTypeManifoldReferences( element );

    return references;
  }

  private void storeTypeManifoldReferences( @NotNull PsiElement element )
  {
    _classDeclElement = null;

    Module mod = ModuleUtilCore.findModuleForPsiElement( element );
    if( mod == null )
    {
      return;
    }

    ManModule module = ManProject.getModule( mod );
    if( module == null )
    {
      return;
    }

    PsiClass psiClass = findPsiClass( (PsiFileSystemItem)element, module );
    if( psiClass == null )
    {
      return;
    }

    Query<PsiReference> search = ReferencesSearch.search( psiClass, GlobalSearchScope.projectScope( mod.getProject() ) );
    List<UsageInfo> usages = new ArrayList<>();
    for( PsiReference ref: ResourceToManifoldUtil.searchForElement( search ) )
    {
      usages.add( new MoveRenameUsageInfo( ref.getElement(), ref, ref.getRangeInElement().getStartOffset(),
        ref.getRangeInElement().getEndOffset(), element,
        ref.resolve() == null && !(ref instanceof PsiPolyVariantReference && ((PsiPolyVariantReference)ref).multiResolve( true ).length > 0) ) );
    }
    _usages = usages;

    if( psiClass instanceof ManifoldPsiClass )
    {
      PsiElement fakeElement = ManGotoDeclarationHandler.find( psiClass, (ManifoldPsiClass)psiClass );
      if( fakeElement instanceof PsiNamedElement && isTopLevelClassDeclaration( fakeElement ) )
      {
        SmartPointerManager smartPointerMgr = SmartPointerManager.getInstance( psiClass.getProject() );
        _classDeclElement = smartPointerMgr.createSmartPsiElementPointer( (PsiNamedElement)fakeElement );
      }
    }
  }

  @Nullable
  private PsiClass findPsiClass( @NotNull PsiFileSystemItem element, ManModule module )
  {
    String[] fqns = module.getTypesForFile( FileUtil.toIFile( module.getProject(), element.getVirtualFile() ) );
    PsiClass psiClass = null;
    for( String fqn: fqns )
    {
      psiClass = JavaPsiFacade.getInstance( element.getProject() )
        .findClass( fqn, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope( module.getIjModule() ) );
      if( psiClass != null )
      {
        break;
      }
    }
    return psiClass;
  }

  @Nullable
  @Override
  public Runnable getPostRenameCallback( @NotNull PsiElement element, @NotNull String newName, @NotNull RefactoringElementListener elementListener )
  {
    return _usages.isEmpty() ? null : () -> renameManifoldTypeRefs( element, elementListener );
  }

  private void renameManifoldTypeRefs( PsiElement element, RefactoringElementListener elementListener )
  {
    ApplicationManager.getApplication().invokeLater( () ->
      WriteCommandAction.runWriteCommandAction( element.getProject(), () ->
      {
        Module ijModule = ModuleUtilCore.findModuleForPsiElement( element );
        if( ijModule == null )
        {
          return;
        }

        ManModule module = ManProject.getModule( ijModule );
        if( module == null )
        {
          return;
        }

        PsiClass psiClass = findPsiClass( (PsiFileSystemItem)element, module );
        if( psiClass == null )
        {
          return;
        }

        RenameUtil.doRename( psiClass, psiClass.getName(), _usages.toArray( new UsageInfo[0] ), element.getProject(), elementListener );

        // for plain text files, also rename a class name declaration if such a thing exists e.g., javascript class declaration
        if( _classDeclElement != null && _classDeclElement.getElement() != null )
        {
          _classDeclElement.getElement().setName( psiClass.getName() == null ? "" : psiClass.getName() );
        }
      } ) );
  }
}

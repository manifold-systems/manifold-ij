package manifold.ij.extensions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
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
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.refactoring.rename.RenamePsiFileProcessor;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Query;
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
  public PsiElement substituteElementToRename( PsiElement element, @Nullable Editor editor )
  {
    return maybeGetResourceFile( element );
  }

  @Override
  public RenameDialog createRenameDialog( Project project, PsiElement element, PsiElement nameSuggestionContext, Editor editor )
  {
    return new PsiFileRenameDialog( project, element, nameSuggestionContext, editor ) {
      protected void createNewNameComponent() {
        super.createNewNameComponent();
        getNameSuggestionsField().selectNameWithoutExtension();
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
  public Collection<PsiReference> findReferences( PsiElement element )
  {
    Collection<PsiReference> references = super.findReferences( element );

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

    PsiClass psiClass = findPsiClass( (PsiFileSystemItem)element, module );
    if( psiClass == null )
    {
      return;
    }

    Query<PsiReference> search = ReferencesSearch.search( psiClass, GlobalSearchScope.projectScope( mod.getProject() ) );
    List<UsageInfo> usages = new ArrayList<>();
    for( PsiReference ref: search.findAll() )
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
      psiClass = ManifoldPsiClassCache.instance().getPsiClass( GlobalSearchScope.moduleWithDependenciesAndLibrariesScope( module.getIjModule() ), module, fqn );
      if( psiClass != null )
      {
        break;
      }
    }
    return psiClass;
  }

  @Nullable
  @Override
  public Runnable getPostRenameCallback( PsiElement element, String newName, RefactoringElementListener elementListener )
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
        PsiClass psiClass = findPsiClass( (PsiFileSystemItem)element, module );
        if( psiClass == null )
        {
          return;
        }

        RenameUtil.doRename( psiClass, psiClass.getName(), _usages.toArray( new UsageInfo[_usages.size()] ), element.getProject(), elementListener );

        // for plain text files, also rename a class name declaration if such a thing exists e.g., javascript class declaration
        if( _classDeclElement.getElement() != null )
        {
          _classDeclElement.getElement().setName( psiClass.getName() == null ? "" : psiClass.getName() );
        }
      } ) );
  }
}

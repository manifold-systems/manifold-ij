package manifold.ij.extensions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenamePsiFileProcessor;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Query;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RenameTypeManifoldFileProcessor extends RenamePsiFileProcessor
{
  private List<UsageInfo> _usages = Collections.emptyList();

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
    Module mod = ModuleUtilCore.findModuleForPsiElement( element );
    if( mod == null )
    {
      return;
    }

    ManModule module = ManProject.getModule( mod );
    String[] fqns = module.getTypesForFile( FileUtil.toIFile( module.getProject(), ((PsiFileSystemItem)element).getVirtualFile() ) );
    if( fqns.length != 1 )
    {
      //## todo: ?
      return;
    }

    String fqn = fqns[0];
    PsiClass psiClass = ManifoldPsiClassCache.instance().getPsiClass( GlobalSearchScope.moduleWithDependenciesAndLibrariesScope( module.getIjModule() ), module, fqn );
    if( psiClass == null )
    {
      return;
    }

    Query<PsiReference> search = ReferencesSearch.search( psiClass, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope( module.getIjModule() ) );
    List<UsageInfo> usages = new ArrayList<>();
    for( PsiReference ref: search.findAll() )
    {
      usages.add( new MoveRenameUsageInfo( ref.getElement(), ref, ref.getRangeInElement().getStartOffset(),
        ref.getRangeInElement().getEndOffset(), element,
        ref.resolve() == null && !(ref instanceof PsiPolyVariantReference && ((PsiPolyVariantReference)ref).multiResolve( true ).length > 0) ) );
    }
    _usages = usages;
  }

  @Nullable
  @Override
  public Runnable getPostRenameCallback( PsiElement element, String newName, RefactoringElementListener elementListener )
  {
    return _usages.isEmpty() ? null : () -> renameManifoldTypeRefs( element, newName, elementListener );
  }

  private void renameManifoldTypeRefs( PsiElement element, String newName, RefactoringElementListener elementListener )
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
        String[] fqns = module.getTypesForFile( FileUtil.toIFile( module.getProject(), ((PsiFileSystemItem)element).getVirtualFile() ) );
        if( fqns.length != 1 )
        {
          //## todo: ?
          return;
        }

        String fqn = fqns[0];
        PsiClass psiClass = ManifoldPsiClassCache.instance().getPsiClass( GlobalSearchScope.moduleWithDependenciesAndLibrariesScope( module.getIjModule() ), module, fqn );
        if( psiClass == null )
        {
          return;
        }

        String name = newName;
        int iDot = name.indexOf( '.' );
        name = iDot < 0 ? newName : newName.substring( 0, iDot );
        RenameUtil.doRename( psiClass, name, _usages.toArray( new UsageInfo[_usages.size()] ), element.getProject(), elementListener );
      } ) );
  }
}

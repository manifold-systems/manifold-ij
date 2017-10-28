package manifold.ij.extensions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.util.FileUtil;
import org.jetbrains.annotations.Nullable;

public class MoveTypeManifoldFileProcessor extends MoveFileHandler
{
  @Override
  public boolean canProcessElement( PsiFile element )
  {
    return getFqnsForFile( element ).length > 0;
  }

  @Override
  public void prepareMovedFile( PsiFile file, PsiDirectory moveDestination, Map<PsiElement, PsiElement> oldToNewMap )
  {
    oldToNewMap.put( file, moveDestination );
  }

  @Nullable
  @Override
  public List<UsageInfo> findUsages( PsiFile psiFile, PsiDirectory newParent, boolean searchInComments, boolean searchInNonJavaFiles )
  {
    Module mod = ModuleUtilCore.findModuleForPsiElement( psiFile );
    ManModule module = ManProject.getModule( mod );
    PsiClass psiClass = findPsiClass( psiFile );
    if( psiClass == null )
    {
      return Collections.emptyList();
    }

    Query<PsiReference> search = ReferencesSearch.search( psiClass, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope( module.getIjModule() ) );
    List<UsageInfo> usages = new ArrayList<>();
    for( PsiReference ref: search.findAll() )
    {
      usages.add( new MoveRenameUsageInfo( ref.getElement(), ref, ref.getRangeInElement().getStartOffset(),
        ref.getRangeInElement().getEndOffset(), psiClass,
        ref.resolve() == null && !(ref instanceof PsiPolyVariantReference && ((PsiPolyVariantReference)ref).multiResolve( true ).length > 0) ) );
    }
    return usages;
  }

  @Override
  public void retargetUsages( List<UsageInfo> usageInfos, Map<PsiElement, PsiElement> oldToNewMap )
  {
    PsiFile oldFile = (PsiFile)oldToNewMap.keySet().iterator().next();

    ApplicationManager.getApplication().invokeLater( () ->
      WriteCommandAction.runWriteCommandAction( oldFile.getProject(), () ->
      {
        PsiDirectory newDir = (PsiDirectory)oldToNewMap.get( oldFile );
        PsiFile newFile = newDir.findFile( oldFile.getName() );
        if( newFile == null )
        {
          return;
        }

        PsiClass psiClass = findPsiClass( newFile );
        if( psiClass == null )
        {
          return;
        }

        for( UsageInfo usageInfo : usageInfos )
        {
          PsiReference reference = usageInfo.getReference();
          assert reference != null;
          reference.bindToElement( psiClass );
        }
      } ) );
  }

  @Override
  public void updateMovedFile( PsiFile file ) throws IncorrectOperationException
  {
    // nothing to do
  }

  private String[] getFqnsForFile( PsiFile element )
  {
    Module mod = ModuleUtilCore.findModuleForPsiElement( element );
    if( mod == null )
    {
      return new String[0];
    }

    ManModule module = ManProject.getModule( mod );
    return module.getTypesForFile( FileUtil.toIFile( module.getProject(), element.getVirtualFile() ) );
  }

  @Nullable
  private PsiClass findPsiClass( PsiFileSystemItem element )
  {
    Module mod = ModuleUtilCore.findModuleForPsiElement( element );
    if( mod == null )
    {
      return null;
    }

    ManModule module = ManProject.getModule( mod );
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
}

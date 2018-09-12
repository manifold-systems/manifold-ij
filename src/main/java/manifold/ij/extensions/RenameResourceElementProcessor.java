package manifold.ij.extensions;

import com.intellij.json.psi.JsonProperty;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiPlainText;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Query;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import manifold.util.JsonUtil;
import manifold.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


import static manifold.ij.extensions.ResourceToManifoldUtil.KEY_FEATURE_PATH;
import static manifold.ij.extensions.ResourceToManifoldUtil.findFakePlainTextElement;

/**
 */
public class RenameResourceElementProcessor extends RenamePsiElementProcessor
{
  private Map<Pair<FeaturePath, PsiElement>, List<UsageInfo>> _javaUsages;

  @Override
  public boolean canProcessElement( @NotNull PsiElement elem )
  {
    PsiElement[] element = new PsiElement[]{elem};
    List<PsiElement> javaElems = _findJavaElements( element );

    if( javaElems.isEmpty() )
    {
      return false;
    }

    for( PsiElement javaElem : javaElems )
    {
      if( !(javaElem instanceof PsiMethod) &&
          !(javaElem instanceof PsiField) &&
          !(javaElem instanceof PsiClass) )
      {
        return false;
      }
    }

    return true;
  }

  private List<PsiElement> _findJavaElements( PsiElement[] element )
  {
    List<PsiElement> javaElems = findJavaElements( element );
    if( javaElems.isEmpty() )
    {
      if( element[0] instanceof PsiModifierListOwner )
      {
        element[0] = ManGotoDeclarationHandler.find( (PsiModifierListOwner)element[0] );
        if( element[0] instanceof FakeTargetElement )
        {
          javaElems = findJavaElements( element );
        }
      }
    }
    return javaElems;
  }

  @Override
  public boolean isInplaceRenameSupported()
  {
    return false;
  }

  @Nullable
  @Override
  public PsiElement substituteElementToRename( PsiElement elem, @Nullable Editor editor )
  {
    if( elem instanceof PsiModifierListOwner )
    {
      return ManGotoDeclarationHandler.find( (PsiModifierListOwner)elem );
    }

    PsiElement[] element = new PsiElement[]{elem};
    _findJavaElements( element );

    findJavaElements( element );
    return element[0];
  }

  private List<PsiElement> findJavaElements( PsiElement[] element )
  {
    if( element[0] == null )
    {
      return Collections.emptyList();
    }

    Set<PsiModifierListOwner> javaElems = ResourceToManifoldUtil.findJavaElementsFor( element[0] );
    if( javaElems.isEmpty() && element[0] instanceof PsiModifierListOwner )
    {
      PsiElement target = ManGotoDeclarationHandler.find( (PsiModifierListOwner)element[0] );
      if( target == null )
      {
        return Collections.emptyList();
      }
      PsiFile containingFile = target.getContainingFile();
      PsiElement elemAt = containingFile instanceof PsiPlainTextFile ? target : containingFile.findElementAt( target.getTextOffset() );
      while( elemAt != null && !(elemAt instanceof PsiNamedElement) )
      {
        elemAt = elemAt.getParent();
      }
      if( elemAt != null )
      {
        element[0] = elemAt;
        javaElems = ResourceToManifoldUtil.findJavaElementsFor( element[0] );
        if( javaElems.isEmpty() )
        {
          return Collections.emptyList();
        }
      }
    }
    else if( element[0] instanceof ManifoldPsiClass && ((ManifoldPsiClass)element[0]).getContainingClass() != null )
    {
      // handle inner class reference rename e.g., json properties are also inner classes

      PsiElement target = ManGotoDeclarationHandler.find( (ManifoldPsiClass)element[0], (ManifoldPsiClass)element[0] );
      if( target != null )
      {
        PsiElement elemAt = target.getContainingFile().findElementAt( target.getTextOffset() );
        if( elemAt != null )
        {
          while( elemAt != null && (!(elemAt instanceof PsiNamedElement) /*|| oldName != null && !((PsiNamedElement)elemAt).getName().equals( oldName )*/) )
          {
            elemAt = elemAt.getParent();
          }
          element[0] = elemAt;
        }
      }
    }
    else if( element[0] instanceof PsiPlainTextFile || element[0] instanceof PsiPlainText )
    {
      element[0] = element[0] instanceof PsiPlainText ? element[0].getContainingFile() : element[0];
      element[0] = findFakePlainTextElement( (PsiPlainTextFile)element[0] );
    }
    return javaElems.stream().map( e -> (PsiElement)e ).collect( Collectors.toList() );
  }

  @NotNull
  @Override
  public Collection<PsiReference> findReferences( PsiElement element )
  {
    Collection<PsiReference> references = super.findReferences( element );

    if( element instanceof JsonProperty )
    {
      //## hack: IJ's json parser considers all properties having the same name as the same reference, which is total crap
      references.clear();
    }

    // Store refs to manifold types
    storeTypeManifoldReferences( element );

    return references;
  }

  private void storeTypeManifoldReferences( @NotNull PsiElement elem )
  {
    PsiElement[] element = new PsiElement[]{elem};
    List<PsiElement> javaElems = findJavaElements( element );
    _javaUsages = findJavaUsages( element[0], javaElems );
  }

  private static Map<Pair<FeaturePath, PsiElement>, List<UsageInfo>> findJavaUsages( PsiElement element, List<PsiElement> javaElems )
  {
    if( !(element instanceof PsiNamedElement) || javaElems.isEmpty() )
    {
      return Collections.emptyMap();
    }

    Map<Pair<FeaturePath, PsiElement>, List<UsageInfo>> allUsages = new HashMap<>();
    for( PsiElement javaElem : javaElems )
    {
      if( javaElem == null )
      {
        continue;
      }

      List<UsageInfo> usages = findUsages( javaElem, element );
      if( !usages.isEmpty() )
      {
        FeaturePath path = javaElem.getUserData( KEY_FEATURE_PATH );
        allUsages.put( new Pair<>( path, javaElem ), usages );
      }
    }

    return allUsages;
  }

  private static List<UsageInfo> findUsages( PsiElement element, PsiElement ctx )
  {
//    Module mod = ModuleUtilCore.findModuleForPsiElement( element );
//    if( mod == null )
//    {
//      return Collections.emptyList();
//    }

    Module module = ModuleUtilCore.findModuleForPsiElement( ctx );
    if( module == null )
    {
      return Collections.emptyList();
    }

    Query<PsiReference> search = ReferencesSearch.search( element, GlobalSearchScope.moduleScope( module ) );
    List<UsageInfo> usages = new ArrayList<>();
    for( PsiReference ref : search.findAll() )
    {
      MoveRenameUsageInfo usageInfo = new MoveRenameUsageInfo( ref.getElement(), ref, ref.getRangeInElement().getStartOffset(),
                                                               ref.getRangeInElement().getEndOffset(), element,
                                                               ref.resolve() == null && !(ref instanceof PsiPolyVariantReference && ((PsiPolyVariantReference)ref).multiResolve( true ).length > 0) );
      usages.add( usageInfo );
    }
    return usages;
  }

  @Nullable
  @Override
  public Runnable getPostRenameCallback( PsiElement element, String newName, RefactoringElementListener elementListener )
  {
    return _javaUsages.isEmpty() ? null : () -> handleManifoldRename( element, elementListener );
  }

  private void handleManifoldRename( PsiElement element, RefactoringElementListener elementListener )
  {
    if( !(element instanceof PsiNamedElement) || _javaUsages.isEmpty() )
    {
      return;
    }

    String name = ((PsiNamedElement)element).getName();
    String newBaseName = JsonUtil.makeIdentifier( name );

    //## find a way to add this as part of the overall rename Undo?

    ApplicationManager.getApplication().saveAll();

    ApplicationManager.getApplication().invokeLater( () ->
                                                       WriteCommandAction.runWriteCommandAction( element.getProject(), () ->
                                                       {
                                                         for( Map.Entry<Pair<FeaturePath, PsiElement>, List<UsageInfo>> entry : _javaUsages.entrySet() )
                                                         {
                                                           Pair<FeaturePath, PsiElement> key = entry.getKey();
                                                           List<UsageInfo> value = entry.getValue();
                                                           String newFeatureName = newBaseName;
                                                           FeaturePath path = key.getFirst();
                                                           if( path != null )
                                                           {
                                                             newFeatureName = findFeatureName( element, path );
                                                             if( newFeatureName == null )
                                                             {
                                                               newFeatureName = newBaseName;
                                                             }
                                                           }
                                                           if( newFeatureName != null )
                                                           {
                                                             PsiElement targetElem = key.getSecond();
                                                             RenameUtil.doRename( targetElem, newFeatureName, value.toArray( new UsageInfo[value.size()] ), element.getProject(), elementListener );
                                                           }
                                                         }
                                                       } ) );
  }

  private String findFeatureName( PsiElement element, FeaturePath path )
  {
    PsiClass root = path.getRoot();
    String fqn = root.getQualifiedName();
    if( fqn == null )
    {
      return null;
    }

    Module module = ModuleUtilCore.findModuleForPsiElement( element );
    if( module == null )
    {
      return null;
    }

    PsiClass psiClass = JavaPsiFacade.getInstance( root.getProject() ).findClass( fqn, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope( module ) );
    if( psiClass == null )
    {
      return null;
    }
    
    PsiNamedElement renamedFeature = findFeatureElement( psiClass, path.getChild() );
    return renamedFeature == null ? null : renamedFeature.getName();
  }

  private PsiNamedElement findFeatureElement( PsiClass psiClass, FeaturePath child )
  {
    if( child == null )
    {
      return psiClass;
    }

    PsiNamedElement result = null;

    switch( child.getFeatureType() )
    {
      case Class:
      {
        PsiClass[] innerClasses = psiClass.getInnerClasses();
        if( innerClasses.length == child.getCount() )
        {
          result = findFeatureElement( innerClasses[child.getIndex()], child.getChild() );
        }
        break;
      }

      case Method:
      {
        PsiMethod[] methods = psiClass.getMethods();
        if( methods.length == child.getCount() )
        {
          result = methods[child.getIndex()];
        }
        break;
      }

      case Field:
        PsiField[] fields = psiClass.getFields();
        if( fields.length == child.getCount() )
        {
          result = fields[child.getIndex()];
        }
        break;

      default:
        throw new IllegalStateException( "Unhandled feature type: " + child.getFeatureType() );
    }
    return result;
  }
}

package manifold.ij.extensions;

import com.intellij.json.psi.JsonProperty;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Query;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import manifold.util.JsonUtil;
import manifold.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 */
public class RenameResourceElementProcessor extends RenamePsiElementProcessor
{
  Map<Pair<String, PsiElement>, List<UsageInfo>> _javaUsages;

  @Override
  public boolean canProcessElement( @NotNull PsiElement elem )
  {
    PsiElement[] element = new PsiElement[]{elem};
    List<PsiElement> javaElems = findJavaElements( element );
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

  @Override
  public boolean isInplaceRenameSupported()
  {
    return false;
  }

  @Nullable
  @Override
  public PsiElement substituteElementToRename( PsiElement elem, @Nullable Editor editor )
  {
    PsiElement[] element = new PsiElement[] {elem};
    findJavaElements( element );
    return element[0];
  }

  private List<PsiElement> findJavaElements( PsiElement[] element )
  {
    if( element[0] == null )
    {
      return Collections.emptyList();
    }

    List<PsiElement> javaElems = ResourceToManifoldUtil.findJavaElementsFor( element[0] );
    if( javaElems.isEmpty() )
    {
      PsiElement target = ManGotoDeclarationHandler.find( element[0] );
      if( target == null )
      {
        return Collections.emptyList();
      }
      PsiElement elemAt = target.getContainingFile().findElementAt( target.getTextOffset() );
      while( elemAt != null && (!(elemAt instanceof PsiNamedElement) /*|| oldName != null && !((PsiNamedElement)elemAt).getName().equals( oldName )*/) )
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
    return javaElems;
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

  static Map<Pair<String, PsiElement>, List<UsageInfo>> findJavaUsages( PsiElement element, List<PsiElement> javaElems )
  {
    if( !(element instanceof PsiNamedElement) || javaElems.isEmpty() )
    {
      return Collections.emptyMap();
    }

    PsiMethod isser = null;
    PsiMethod getter = null;
    PsiMethod setter = null;
    PsiElement other = null;

    for( PsiElement javaElem : javaElems )
    {
      if( javaElem instanceof PsiMethod )
      {
        String propName = getPropertyNameFromGetter( (PsiMethod)javaElem );
        if( propName != null )
        {
          if( ((PsiMethod)javaElem).getName().startsWith( "is" ) )
          {
            isser = (PsiMethod)javaElem;
          }
          else
          {
            getter = (PsiMethod)javaElem;
          }
        }
        else
        {
          propName = getPropertyNameFromSetter( (PsiMethod)javaElem );
          if( propName != null )
          {
            setter = (PsiMethod)javaElem;
          }
          else
          {
            other = javaElem;
          }
        }
      }
      else
      {
        other = javaElem;
      }
    }

    Map<Pair<String, PsiElement>, List<UsageInfo>> allUsages = new HashMap<>();

    addUsages( "is", isser, allUsages, element );
    addUsages( "get", getter, allUsages, element );
    addUsages( "set", setter, allUsages, element );
    addUsages( "", other, allUsages, element );

    return allUsages;
  }

  private static void addUsages( String prefix, PsiElement elem, Map<Pair<String, PsiElement>, List<UsageInfo>> allUsages, PsiElement element )
  {
    if( elem == null )
    {
      return;
    }

    List<UsageInfo> usages = findUsages( elem, element );
    if( !usages.isEmpty() )
    {
      allUsages.put( new Pair<>( prefix, elem ), usages );
    }
  }

  private static List<UsageInfo> findUsages( PsiElement element, PsiElement ctx )
  {
//    Module mod = ModuleUtilCore.findModuleForPsiElement( element );
//    if( mod == null )
//    {
//      return Collections.emptyList();
//    }

    Query<PsiReference> search = ReferencesSearch.search( element, GlobalSearchScope.moduleScope( ModuleUtilCore.findModuleForPsiElement( ctx ) ) );
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

  private static String getPropertyNameFromGetter( PsiMethod method )
  {
    PsiParameter[] params = method.getParameterList().getParameters();
    if( params.length != 0 )
    {
      return null;
    }
    String name = method.getName();
    String propertyName = null;
    for( String prefix : Arrays.asList( "get", "is" ) )
    {
      if( name.length() > prefix.length() &&
          name.startsWith( prefix ) )
      {
        if( prefix.equals( "is" ) &&
            (!method.getReturnType().equals( PsiType.BOOLEAN ) &&
             !method.getReturnType().equals( PsiType.getTypeByName( CommonClassNames.JAVA_LANG_BOOLEAN, method.getProject(), GlobalSearchScope.allScope( method.getProject() ) ) )) )
        {
          break;
        }

        propertyName = name.substring( prefix.length() );
        char firstChar = propertyName.charAt( 0 );
        if( firstChar == '_' && propertyName.length() > 1 )
        {
          propertyName = propertyName.substring( 1 );
        }
        else if( Character.isAlphabetic( firstChar ) &&
                 !Character.isUpperCase( firstChar ) )
        {
          propertyName = null;
          break;
        }
      }
    }
    return propertyName;
  }

  private static String getPropertyNameFromSetter( PsiMethod method )
  {
    if( !method.getReturnType().equals( PsiType.VOID ) )
    {
      return null;
    }

    PsiParameter[] params = method.getParameterList().getParameters();
    if( params.length != 1 )
    {
      return null;
    }

    String name = method.getName();
    String propertyName = null;
    if( name.length() > "set".length() &&
        name.startsWith( "set" ) )
    {
      propertyName = name.substring( "set".length() );
      char firstChar = propertyName.charAt( 0 );
      if( firstChar == '_' && propertyName.length() > 1 )
      {
        propertyName = propertyName.substring( 1 );
      }
      else if( Character.isAlphabetic( firstChar ) &&
               !Character.isUpperCase( firstChar ) )
      {
        propertyName = null;
      }
    }
    return propertyName;
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
    int iDot = name.lastIndexOf( '.' );
    if( iDot >= 0 )
    {
      // this is to handle properties files, only rename the last part... this needs to improve
      name = name.substring( iDot+1 );
    }
    String newName = JsonUtil.makeIdentifier( name );

    //## find a way to add this as part of the overall rename Undo?

    ApplicationManager.getApplication().saveAll();

    ApplicationManager.getApplication().invokeLater( () ->
                                                       WriteCommandAction.runWriteCommandAction( element.getProject(), () ->
                                                       {
                                                         for( Map.Entry<Pair<String, PsiElement>, List<UsageInfo>> entry : _javaUsages.entrySet() )
                                                         {
                                                           Pair<String, PsiElement> key = entry.getKey();
                                                           List<UsageInfo> value = entry.getValue();
                                                           StringBuilder baseName = new StringBuilder( newName );
                                                           String prefix = key.getFirst();
                                                           if( prefix.length() > 0 )
                                                           {
                                                             baseName.setCharAt( 0, Character.toUpperCase( baseName.charAt( 0 ) ) );
                                                           }
                                                           RenameUtil.doRename( key.getSecond(), prefix + baseName, value.toArray( new UsageInfo[value.size()] ), element.getProject(), elementListener );
                                                         }
                                                       } ) );
  }
}

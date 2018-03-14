package manifold.ij.extensions;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiReference;
import java.util.List;
import java.util.Objects;
import manifold.api.darkj.DarkJavaTypeManifold;
import manifold.api.type.SourcePosition;


/**
 */
public class ManGotoDeclarationHandler extends GotoDeclarationHandlerBase
{
  @Override
  public PsiElement getGotoDeclarationTarget( PsiElement sourceElement, Editor editor )
  {
    if( sourceElement == null )
    {
      return null;
    }

    PsiElement parent = sourceElement.getParent();
    if( parent != null )
    {
      PsiReference ref = parent.getReference();
      if( ref != null )
      {
        PsiElement resolve = ref.resolve();
        if( resolve instanceof PsiModifierListOwner )
        {
          PsiElement target = find( (PsiModifierListOwner)resolve );
          if( target != null )
          {
            return target;
          }
        }
      }
    }
    return null;
  }

  public static PsiElement find( PsiModifierListOwner resolve )
  {
    PsiFile file = resolve.getContainingFile();
    if( file != null )
    {
      ManifoldPsiClass facade = resolve instanceof ManifoldPsiClass ? (ManifoldPsiClass)resolve : file.getUserData( ManifoldPsiClass.KEY_MANIFOLD_PSI_CLASS );
      PsiElement annotations = find( resolve, facade );
      if( annotations != null )
      {
        return annotations;
      }
    }
    return null;
  }

  public static PsiElement find( PsiModifierListOwner resolve, ManifoldPsiClass facade )
  {
    PsiModifierList modifierList = resolve.getModifierList();
    if( modifierList == null )
    {
      return null;
    }

    PsiAnnotation[] annotations = modifierList.getAnnotations();
    if( annotations.length > 0 &&
        Objects.equals( annotations[0].getQualifiedName(), SourcePosition.class.getName() ) )
    {
      return findTargetFeature( annotations[0], facade );
    }

    if( facade != null && !facade.getRawFiles().isEmpty() &&
        DarkJavaTypeManifold.FILE_EXTENSIONS.stream()
          .anyMatch( ext -> ext.equalsIgnoreCase( facade.getRawFiles().get( 0 ).getVirtualFile().getExtension() ) ) )
    {
      // DarkJava is Java
      return facade.getRawFiles().get( 0 ).findElementAt( resolve.getTextOffset() );
    }

    return null;
  }

  private static PsiElement findTargetFeature( PsiAnnotation psiAnnotation, ManifoldPsiClass facade )
  {
    PsiAnnotationMemberValue value = psiAnnotation.findAttributeValue( SourcePosition.FEATURE );
    String featureName = StringUtil.unquoteString( value.getText() );
//    value = psiAnnotation.findAttributeValue( SourcePosition.TYPE );
//    if( value != null )
//    {
//      String ownersType = StringUtil.unquoteString( value.getText() );
//      if( ownersType != null )
//      {
//        PsiElement target = findIndirectTarget( ownersType, featureName, facade.getRawFile().getProject() );
//        if( target != null )
//        {
//          return target;
//        }
//      }
//    }

    int iOffset = Integer.parseInt( psiAnnotation.findAttributeValue( SourcePosition.OFFSET ).getText() );
    int iLength = Integer.parseInt( psiAnnotation.findAttributeValue( SourcePosition.LENGTH ).getText() );

    if( facade == null )
    {
      String path = (String)((PsiLiteralExpression)psiAnnotation.findAttributeValue( SourcePosition.URL )).getValue();
      VirtualFile vfile = LocalFileSystem.getInstance().findFileByPath( path );
      PsiFile psiFile = psiAnnotation.getManager().findFile( vfile );
      return new FakeTargetElement( psiFile, iOffset, iLength >= 0 ? iLength : 1, featureName );
    }
    else
    {
      List<PsiFile> sourceFiles = facade.getRawFiles();
      if( iOffset >= 0 )
      {
        //PsiElement target = sourceFile.findElementAt( iOffset );
        //## todo: handle multiple files
        return new FakeTargetElement( sourceFiles.get( 0 ), iOffset, iLength >= 0 ? iLength : 1, featureName );
      }
    }
    return facade;
  }
}

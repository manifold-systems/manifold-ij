package manifold.ij.extensions;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
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

    PsiAnnotation sourcePosAnnotation = Arrays.stream( modifierList.getAnnotations() )
      .filter( anno -> Objects.equals( anno.getQualifiedName(), SourcePosition.class.getName() ) )
      .findFirst().orElse( null );
    if( sourcePosAnnotation != null )
    {
      return findTargetFeature( sourcePosAnnotation, resolve, facade );
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

  private static PsiElement findTargetFeature( PsiAnnotation psiAnnotation, PsiModifierListOwner resolve, ManifoldPsiClass facade )
  {
    PsiAnnotationMemberValue value = psiAnnotation.findAttributeValue( SourcePosition.FEATURE );
    String featureName = StringUtil.unquoteString( value.getText() );
    if( facade != null )
    {
      String declaringClassFqn = resolve instanceof PsiMember && ((PsiMember)resolve).getContainingClass() != null
                                 ? ((PsiMember)resolve).getContainingClass().getQualifiedName()
                                 : facade.getQualifiedName();
      if( featureName.startsWith( declaringClassFqn + '.' ) )
      {
        // remove class name qualifier
        featureName = featureName.substring( declaringClassFqn.length() + 1 );
      }
    }
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

    PsiLiteralExpression kindExpr = (PsiLiteralExpression)psiAnnotation.findAttributeValue( SourcePosition.KIND );
    String kind = kindExpr == null ? SourcePosition.DEFAULT_KIND : (String)kindExpr.getValue();

    String path = getUrlConstantValue( psiAnnotation );
    if( path != null )
    {
      VirtualFile vfile = LocalFileSystem.getInstance().findFileByPath( path );
      PsiFile psiFile = psiAnnotation.getManager().findFile( vfile );
      return new FakeTargetElement( psiFile, iOffset, iLength >= 0 ? iLength : 1, featureName, kind );
    }
    else if( facade != null && iOffset >= 0 )
    {
      List<PsiFile> sourceFiles = facade.getRawFiles();
      //PsiElement target = sourceFile.findElementAt( iOffset );
      //## todo: handle multiple files
      return new FakeTargetElement( sourceFiles.get( 0 ), iOffset, iLength >= 0 ? iLength : 1, featureName, kind );
    }
    return facade;
  }

  private static String getUrlConstantValue( PsiAnnotation psiAnnotation )
  {
    PsiAnnotationMemberValue urlAttr = psiAnnotation.findAttributeValue( SourcePosition.URL );
    String url;
    if( urlAttr instanceof PsiReferenceExpression )
    {
      url = ((PsiField)((PsiReferenceExpression)urlAttr).resolve()).computeConstantValue().toString();
    }
    else if( urlAttr instanceof PsiLiteralExpression )
    {
      url = (String)((PsiLiteralExpression)urlAttr).getValue();
    }
    else
    {
      return null;
    }

    try
    {
      return new File( new URL( url ).toURI() ).getAbsolutePath();
    }
    catch( MalformedURLException mue )
    {
      // assume url is just a file path
      return url;
    }
    catch( Exception e )
    {
      return null;
    }
  }
}

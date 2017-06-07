package manifold.ij.extensions;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiReference;
import java.util.List;
import manifold.api.sourceprod.SourcePosition;


/**
 */
public class ManGotoDeclarationHandler extends GotoDeclarationHandlerBase
{
  @Override
  public PsiElement getGotoDeclarationTarget( PsiElement sourceElement, Editor editor )
  {
    PsiElement parent = sourceElement.getParent();
    if( parent != null )
    {
      PsiReference ref = parent.getReference();
      if( ref != null )
      {
        PsiElement resolve = ref.resolve();
        if( resolve != null )
        {
          PsiFile file = resolve.getContainingFile();
          if( file != null )
          {
            JavaFacadePsiClass facade = file.getUserData( JavaFacadePsiClass.KEY_JAVAFACADE );
            if( facade != null )
            {
              PsiAnnotation[] annotations = ((PsiModifierListOwner)resolve).getModifierList().getAnnotations();
              if( annotations != null && annotations.length > 0 &&
                  annotations[0].getQualifiedName().equals( SourcePosition.class.getName() ) )
              {
                return findTargetFeature( annotations[0], facade );
              }
            }
          }
        }
      }
    }
    return null;
  }

  private PsiElement findTargetFeature( PsiAnnotation psiAnnotation, JavaFacadePsiClass facade )
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

    List<PsiFile> sourceFiles = facade.getRawFiles();
    if( iOffset >= 0 )
    {
      //PsiElement target = sourceFile.findElementAt( iOffset );
      //## todo: handle multiple files
      return new FakeTargetElement( sourceFiles.get( 0 ), iOffset, iLength >= 0 ? iLength : 1, featureName );
    }
    return facade;
  }
}

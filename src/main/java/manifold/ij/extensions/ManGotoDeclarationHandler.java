/*
 *
 *  * Copyright (c) 2022 - Manifold Systems LLC
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package manifold.ij.extensions;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import manifold.api.darkj.DarkJavaTypeManifold;
import manifold.ij.psi.ManLightFieldBuilder;
import manifold.rt.api.SourcePosition;
import manifold.ij.core.ManProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static manifold.ij.extensions.PropertyInference.GETTER_TAG;
import static manifold.ij.extensions.PropertyInference.SETTER_TAG;


/**
 */
public class ManGotoDeclarationHandler extends GotoDeclarationHandlerBase
{
  @Override
  public PsiElement getGotoDeclarationTarget( @Nullable PsiElement sourceElement, Editor editor )
  {
    if( sourceElement == null )
    {
      return null;
    }

    if( !ManProject.isManifoldInUse( sourceElement ) )
    {
      // Manifold jars are not used in the project
      return null;
    }

    PsiElement parent = sourceElement.getParent();
    if( parent != null )
    {
      PsiElement resolve = resolveRef( parent );
      if( resolve instanceof PsiModifierListOwner )
      {
        PsiElement accessor = findPropertyTarget( parent, resolve );
        if( accessor != null )
        {
          return accessor;
        }

        PsiElement answer = find( (PsiModifierListOwner)resolve );

        // for properties (manifold-props)
        PsiElement propAnswer = answer == null ? resolve : answer;
        if( propAnswer instanceof ManLightFieldBuilder )
        {
          answer = findTypeManifoldPropertyTarget( propAnswer );
        }

        return answer;
      }
    }
    return null;
  }

  private PsiElement findPropertyTarget( PsiElement parent, PsiElement resolve )
  {
    if( resolve instanceof PsiField )
    {
      @Nullable SmartPsiElementPointer<PsiMethod> getter = resolve.getCopyableUserData( GETTER_TAG );
      @Nullable SmartPsiElementPointer<PsiMethod> setter = resolve.getCopyableUserData( SETTER_TAG );
      PsiElement answer;
      if( getter != null || setter != null )
      {
        if( PropertiesAnnotator.keepRefToField( parent, (PsiField)resolve, PsiUtil.getTopLevelClass( parent ) ) )
        {
          return resolve;
        }

        PsiElement g = getter == null ? null : getter.getElement();
        PsiElement s = setter == null ? null : setter.getElement();
        if( parent.getParent() instanceof PsiAssignmentExpression )
        {
          answer = s;
        }
        else
        {
          answer = g;
        }

        PsiMethod enclMethod = RefactoringUtil.getEnclosingMethod( parent );
        if( answer == null ||
            g == enclMethod || s == enclMethod ) // prop ref in getter/setter goes direct to field
        {
          answer = resolve;
        }
        else
        {
          answer = findTypeManifoldPropertyTarget( answer );
        }
        return answer;
      }
    }
    return null;
  }

  @NotNull
  private PsiElement findTypeManifoldPropertyTarget( PsiElement answer )
  {
    PsiElement resolve = answer.getNavigationElement();
    if( resolve instanceof PsiModifierListOwner )
    {
      answer = find( (PsiModifierListOwner)resolve );
      if( answer == null )
      {
        answer = resolve;
      }
    }
    return answer;
  }

  public static PsiElement resolveRef( PsiElement target )
  {
    PsiReference ref = target.getReference();
    if( ref != null )
    {
      return ref.resolve();
    }

    for( PsiReference r: target.getReferences() )
    {
      PsiElement resolve = r.resolve();
      if( resolve != null )
      {
        return resolve;
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
      PsiElement target = find( resolve, facade );
      if( target != null )
      {
        return target;
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
      facade.getRawFiles().get( 0 ).getVirtualFile() != null &&
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

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

import com.intellij.lang.ASTNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.impl.PsiLiteralStub;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import manifold.api.fs.IFileFragment;
import manifold.rt.api.FragmentValue;
import org.jetbrains.annotations.NotNull;

/**
 * Overrides {@code PsiLiteralExpressionImpl#getType()} to handle the file fragment case.  In that case must return the
 * fragment type since this is no longer a String literal. Note the type of this element is determined by the type
 * specified in the corresponding type manifold's usage of {@link FragmentValue} which is an annotation on the generated
 * class e.g.,
 * <pre>
 *   package abc;
 *   &#64;FragmentValue(methodName = "runMe", type = "abc.MyType.Foo")
 *   public interface MyType {
 *     ...
 *     public static Foo runMe() {
 *       ...
 *     }
 *   }
 * </pre>
 */
public class ManPsiLiteralExpressionImpl extends PsiLiteralExpressionImpl
{
  public ManPsiLiteralExpressionImpl( @NotNull PsiLiteralStub stub )
  {
    super( stub );
  }

  public ManPsiLiteralExpressionImpl( @NotNull ASTNode node )
  {
    super( node );
  }

  @Override
  public PsiType getType()
  {
    final IElementType type = getLiteralElementType();
    if( type == JavaTokenType.STRING_LITERAL || "TEXT_BLOCK_LITERAL".equals( type.toString() ) )
    {
      ASTNode token = getNode().getFirstChildNode();
      if( token instanceof ManPsiBuilderImpl.ManPsiStringLiteral )
      {
        IFileFragment fragment = ((ManPsiBuilderImpl.ManPsiStringLiteral)token).getFragment();
        if( fragment != null )
        {
          String fragClass = getFragmentClassName( fragment );
          Module module = ModuleUtilCore.findModuleForPsiElement( this );
          PsiClass psiFragClass = getFragmentPsiClass( fragClass, module );

          if( psiFragClass != null )
          {
            PsiAnnotation annotation = psiFragClass.getAnnotation( FragmentValue.class.getTypeName() );
            if( annotation != null )
            {
              PsiAnnotationMemberValue value = PsiImplUtil.findAttributeValue( annotation, "type" );
              if( value != null )
              {
                String fqn = getFragmentValueFqn( value );
                return JavaPsiFacade.getInstance( getProject() ).getParserFacade().createTypeFromText( fqn, this );
              }
            }
          }
          // Don't know the type **yet**, another thread is likely processing the new type.
          // By observation, it appears returning `null` from this method is like saying "I don't know yet, ask again
          // later", which is the desired behavior.
          return null;
        }
      }
    }

    return super.getType();
  }

  private PsiClass getFragmentPsiClass( String fragClass, Module module )
  {
    return JavaPsiFacade.getInstance( getProject() ).findClass( fragClass, GlobalSearchScope.moduleScope( module ) );
  }

  private String getFragmentClassName( IFileFragment fragment )
  {
    PsiFile containingFile = getContainingFile();
    String fragClass = null;
    if( containingFile instanceof PsiJavaFile )
    {
      fragClass = ((PsiJavaFile)containingFile).getPackageName() + '.' + fragment.getBaseName();
    }
    else
    {
      SmartPsiElementPointer container = (SmartPsiElementPointer)fragment.getContainer();
      if( container != null )
      {
        PsiElement elem = container.getElement();
        containingFile = elem != null ? elem.getContainingFile() : null;
        if( containingFile != null )
        {
          fragClass = ((PsiJavaFile)containingFile).getPackageName() + '.' + fragment.getBaseName();
        }
      }
    }
    return fragClass;
  }

  private String getFragmentValueFqn( PsiAnnotationMemberValue value )
  {
    String fqn = null;
    if( value instanceof PsiReferenceExpression )
    {
      fqn = ((PsiField)((PsiReferenceExpression)value).resolve()).computeConstantValue().toString();
    }
    else if( value instanceof PsiLiteralExpression )
    {
      fqn = (String)((PsiLiteralExpression)value).getValue();
    }
    return fqn;
  }
}

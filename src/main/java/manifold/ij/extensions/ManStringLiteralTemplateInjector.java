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

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import java.util.List;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.util.ComputeUtil;
import manifold.strings.StringLiteralTemplateParser;
import manifold.rt.api.DisableStringLiteralTemplates;
import org.jetbrains.annotations.NotNull;

public class ManStringLiteralTemplateInjector implements LanguageInjector
{
  static final String FIELD_NAME = "_muhField_";
  private static final String PREFIX =
    "class _Muh_Class_ {\n" +
    "  Object " + FIELD_NAME + " = ";
  private static final String SUFFIX =
    ";\n" +
    "}\n";


  @Override
  public void getLanguagesToInject( @NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces injectionPlacesRegistrar )
  {
    if( !ManProject.isManifoldInUse( host.getProject() ) )
    {
      return;
    }

    PsiLiteralExpressionImpl stringLiteral = getJavaStringLiteral( host );
    if( stringLiteral == null )
    {
      return;
    }

    if( isStringLiteralTemplatesDisabled( stringLiteral, ManProject.getModule( stringLiteral ) ) )
    {
      return;
    }

    String hostText = host.getText();
    List<StringLiteralTemplateParser.Expr> exprs =
      StringLiteralTemplateParser.parse( index -> isEscaped( hostText, index ), false, hostText );
    if( exprs.isEmpty() )
    {
      // Not a template
      return;
    }

    for( StringLiteralTemplateParser.Expr expr: exprs )
    {
      if( !expr.isVerbatim() )
      {
        String prefix = qualifyThis( expr.getExpr(), stringLiteral );
        injectionPlacesRegistrar.addPlace(
          JavaLanguage.INSTANCE,
          new TextRange( expr.getOffset(), expr.getOffset() + expr.getExpr().length() ),
          prefix, SUFFIX );
      }
    }
  }

  private static @NotNull String qualifyThis( String expr, PsiLiteralExpressionImpl stringLiteral )
  {
    String prefix = PREFIX;
    if( expr.startsWith( "this." ) || expr.equals( "this" ) )
    {
      PsiClass containingClass = PsiUtil.getContainingClass( stringLiteral );
      if( containingClass != null )
      {
        // qualify `this` with outer class name, otherwise `this` refers to our _Muh_Class_ prefix
        prefix += containingClass.getName() + '.';
      }
    }
    return prefix;
  }

  private boolean isEscaped( String hostText, int index )
  {
    if( index > 0 )
    {
      if( hostText.charAt( index-1 ) == '\\' )
      {
        if( index > 1 )
        {
          return hostText.charAt( index-2 ) != '\\';
        }
      }
    }
    return false;
  }

  private boolean isStringLiteralTemplatesDisabled( PsiElement elem, ManModule module )
  {
    if( elem == null )
    {
      return false;
    }

    if( module != null && !module.isStringsEnabled() )
    {
      return true;
    }

    if( elem instanceof PsiModifierListOwner )
    {
      for( PsiAnnotation anno: ((PsiModifierListOwner)elem).getAnnotations() )
      {
        if( DisableStringLiteralTemplates.class.getTypeName().equals( anno.getQualifiedName() ) )
        {
          final PsiNameValuePair[] attributes = anno.getParameterList().getAttributes();
          if( attributes.length > 0 )
          {
            Object value = ComputeUtil.computeLiteralValue( attributes[0] );
            return !(value instanceof Boolean) || (boolean)value;
          }
          return true;
        }
      }
    }

    PsiElement parent = elem.getParent();
    if( parent instanceof PsiAnnotation )
    {
      // string templates disabled in annotations because too many annotations allow ${} templating
      return true;
    }

    if( parent == null || parent instanceof PsiJavaFile )
    {
      return false;
    }

    return isStringLiteralTemplatesDisabled( elem.getParent(), module );
  }

  private PsiLiteralExpressionImpl getJavaStringLiteral( @NotNull PsiLanguageInjectionHost host )
  {
    // Only applies to Java string literal expression
    if( host.getLanguage().isKindOf( JavaLanguage.INSTANCE ) &&
           host instanceof PsiLiteralExpressionImpl )
    {
      PsiLiteralExpressionImpl literalExpr = (PsiLiteralExpressionImpl)host;
      IElementType literalElementType = literalExpr.getLiteralElementType();
      if( literalElementType == JavaTokenType.STRING_LITERAL ||
        literalElementType == JavaTokenType.TEXT_BLOCK_LITERAL )
      {
        return literalExpr;
      }
    }
    return null;
  }
}

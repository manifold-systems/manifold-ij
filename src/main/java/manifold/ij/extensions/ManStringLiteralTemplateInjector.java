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
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import java.util.List;
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

    if( isStringLiteralTemplatesDisabled( stringLiteral ) )
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
        injectionPlacesRegistrar.addPlace(
          JavaLanguage.INSTANCE,
          new TextRange( expr.getOffset(), expr.getOffset() + expr.getExpr().length() ),
          PREFIX, SUFFIX );
      }
    }
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

  private boolean isStringLiteralTemplatesDisabled( PsiElement elem )
  {
    if( elem == null )
    {
      return false;
    }

    ManModule module = ManProject.getModule( elem );
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
    if( parent == null || parent instanceof PsiJavaFile )
    {
      return false;
    }

    return isStringLiteralTemplatesDisabled( elem.getParent() );
  }

  private PsiLiteralExpressionImpl getJavaStringLiteral( @NotNull PsiLanguageInjectionHost host )
  {
    // Only applies to Java string literal expression
    if( host.getLanguage().isKindOf( JavaLanguage.INSTANCE ) &&
           host instanceof PsiLiteralExpressionImpl )
    {
      PsiLiteralExpressionImpl literalExpr = (PsiLiteralExpressionImpl)host;
      if( literalExpr.getLiteralElementType() == JavaTokenType.STRING_LITERAL )
      {
        return literalExpr;
      }
    }
    return null;
  }
}

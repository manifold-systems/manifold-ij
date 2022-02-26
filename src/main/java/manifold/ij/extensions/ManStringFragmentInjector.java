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

import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.tree.IElementType;
import manifold.ij.core.ManProject;
import manifold.internal.javac.FragmentProcessor;
import manifold.internal.javac.HostKind;
import org.jetbrains.annotations.NotNull;


public class ManStringFragmentInjector implements LanguageInjector
{
  @Override
  public void getLanguagesToInject( @NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces injectionPlacesRegistrar )
  {
    if( !ManProject.isManifoldInUse( host.getProject() ) )
    {
      return;
    }

    HostKind stringKind = getStringKind( host );
    if( stringKind == null )
    {
      return;
    }

    String hostText = host.getText();
    FragmentProcessor fragmentProcessor = FragmentProcessor.instance();
    FragmentProcessor.Fragment fragment = fragmentProcessor.parseFragment( 0, hostText, stringKind );
    if( fragment != null )
    {
      Language language = getLanguageFromExt( fragment.getExt() );
      if( language != null )
      {
        injectionPlacesRegistrar.addPlace( language,
          new TextRange( fragment.getOffset(), fragment.getOffset() + fragment.getContent().length() ), null, null );
      }
    }
  }

  private Language getLanguageFromExt( String ext )
  {
    return LanguageUtil.getFileTypeLanguage( FileTypeManager.getInstance().getFileTypeByExtension( ext ) );
  }

  private HostKind getStringKind( @NotNull PsiLanguageInjectionHost host )
  {
    // Only applies to Java string literal expression
    if( host.getLanguage().isKindOf( JavaLanguage.INSTANCE ) &&
        host instanceof PsiLiteralExpressionImpl )
    {
      IElementType type = ((PsiLiteralExpressionImpl)host).getLiteralElementType();
      if( type == JavaTokenType.STRING_LITERAL )
      {
        return HostKind.DOUBLE_QUOTE_LITERAL;
      }
      else if( "TEXT_BLOCK_LITERAL".equals( type.toString() ) )
      {
        return HostKind.TEXT_BLOCK_LITERAL;
      }
    }
    return null;
  }
}

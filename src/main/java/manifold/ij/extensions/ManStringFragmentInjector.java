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
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.injection.ReferenceInjector;
import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.LightVirtualFile;
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
    ReferenceInjector injector = ReferenceInjector.findById( ext );
    if( injector != null )
    {
      return injector.toLanguage();
    }

    Language fileTypeLanguage = LanguageUtil.getFileTypeLanguage( FileTypeManager.getInstance().getFileTypeByExtension( ext ) );
    if( fileTypeLanguage != null )
    {
      return fileTypeLanguage;
    }

    LightVirtualFile virtualFileNamedAsLanguageId = new LightVirtualFile( ext );
    LightVirtualFile virtualFileWithLanguageIdAsExtension = new LightVirtualFile( "textmate." + ext );
    for( FileType fileType : FileTypeManager.getInstance().getRegisteredFileTypes() )
    {
      if( fileType instanceof LanguageFileType &&
        fileType instanceof FileTypeIdentifiableByVirtualFile )
      {
        FileTypeIdentifiableByVirtualFile fileTypeByVf = (FileTypeIdentifiableByVirtualFile)fileType;
        if( fileTypeByVf.isMyFileType( virtualFileNamedAsLanguageId ) ||
            fileTypeByVf.isMyFileType( virtualFileWithLanguageIdAsExtension ) )
        {
          return ((LanguageFileType)fileType).getLanguage();
        }
      }
    }

    return null;
  }

  public static Language getLanguageByString(@NotNull String languageId) {
    ReferenceInjector injector = ReferenceInjector.findById(languageId);
    if (injector != null) return injector.toLanguage();
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    FileType fileType = fileTypeManager.getFileTypeByExtension(languageId);
    if (fileType instanceof LanguageFileType) {
      return ((LanguageFileType)fileType).getLanguage();
    }

    LightVirtualFile virtualFileNamedAsLanguageId = new LightVirtualFile(languageId);
    LightVirtualFile virtualFileWithLanguageIdAsExtension = new LightVirtualFile("textmate." + languageId);
    for (FileType registeredFileType : fileTypeManager.getRegisteredFileTypes()) {
      if (registeredFileType instanceof FileTypeIdentifiableByVirtualFile &&
        registeredFileType instanceof LanguageFileType &&
        (((FileTypeIdentifiableByVirtualFile)registeredFileType).isMyFileType(virtualFileNamedAsLanguageId) ||
          ((FileTypeIdentifiableByVirtualFile)registeredFileType).isMyFileType(virtualFileWithLanguageIdAsExtension))) {
        return ((LanguageFileType)registeredFileType).getLanguage();
      }
    }
    return null;
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

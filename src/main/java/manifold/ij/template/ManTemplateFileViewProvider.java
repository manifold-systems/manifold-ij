package manifold.ij.template;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.LanguageSubstitutors;
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.injected.Place;
import com.intellij.psi.templateLanguages.TemplateDataElementType;
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import manifold.util.ReflectUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static manifold.ij.template.psi.ManTemplateTokenType.CONTENT;
import static manifold.ij.template.psi.ManTemplateTokenType.STMT;

public class ManTemplateFileViewProvider extends MultiplePsiFilesPerDocumentFileViewProvider
  implements TemplateLanguageFileViewProvider
{
  private final Language _baseLang;
  private final Language _contentLang;

  private static final ConcurrentMap<String, TemplateDataElementType> TEMPLATE_DATA_TO_LANG = ContainerUtil.newConcurrentMap();
  private static final ConcurrentMap<String, TemplateDataElementType> TEMPLATE_JAVA_TO_LANG = ContainerUtil.newConcurrentMap();

  private static TemplateDataElementType getOrCreateTemplateDataElementType( Language lang )
  {
    TemplateDataElementType result = TEMPLATE_DATA_TO_LANG.get( lang.getID() );

    if( result != null )
    {
      return result;
    }
    TemplateDataElementType created = new TemplateDataElementType( "ManTL_DATA", lang, CONTENT, STMT );
    TemplateDataElementType prevValue = TEMPLATE_DATA_TO_LANG.putIfAbsent( lang.getID(), created );

    return prevValue == null ? created : prevValue;
  }
  private static TemplateDataElementType getOrCreateTemplateJavaElementType( Language lang )
  {
    TemplateDataElementType result = TEMPLATE_JAVA_TO_LANG.get( lang.getID() );

    if( result != null )
    {
      return result;
    }
    TemplateDataElementType created = new ManTemplateDataElementType( "ManTL_Java", lang, CONTENT );
    TemplateDataElementType prevValue = TEMPLATE_JAVA_TO_LANG.putIfAbsent( lang.getID(), created );

    return prevValue == null ? created : prevValue;
  }

  ManTemplateFileViewProvider( PsiManager manager, VirtualFile file, boolean physical, Language baseLanguage )
  {
    this( manager, file, physical, baseLanguage, getTemplateDataLanguage( manager, file ) );
  }

  private ManTemplateFileViewProvider( PsiManager manager, VirtualFile file, boolean physical, Language baseLanguage, Language templateLanguage )
  {
    super( manager, file, physical );
    _baseLang = baseLanguage;
    _contentLang = templateLanguage;
  }

  @Override
  public boolean supportsIncrementalReparse( @NotNull Language rootLanguage )
  {
    return false;
  }

  @NotNull
  private static Language getTemplateDataLanguage( PsiManager manager, VirtualFile vfile )
  {
    Language languageFromFragment = getLanguageFromFragment( vfile );
    if( languageFromFragment != null )
    {
      return languageFromFragment;
    }

    Language dataLang = TemplateDataLanguageMappings.getInstance( manager.getProject() ).getMapping( vfile );
    if( dataLang == null )
    {
      FileType dataFileType = ManTemplateLanguage.getContentLanguage( vfile );
      dataLang = dataFileType instanceof LanguageFileType ? ((LanguageFileType)dataFileType).getLanguage() : PlainTextLanguage.INSTANCE;
    }

    Language substituteLang = LanguageSubstitutors.getInstance().substituteLanguage( dataLang, vfile, manager.getProject() );

    // only use a substituted language if it's templateable
    if( TemplateDataLanguageMappings.getTemplateableLanguages().contains( substituteLang ) )
    {
      dataLang = substituteLang;
    }

    return dataLang;
  }

  @Nullable
  private static Language getLanguageFromFragment( VirtualFile vfile )
  {
    if( vfile instanceof VirtualFileWindow )
    {
      // embedded template as a fragment

      VirtualFileWindow fileWindow = (VirtualFileWindow)vfile;
      String text = ((Place) ReflectUtil.method( fileWindow.getDocumentWindow(), "getShreds" ).invoke()).get( 0 ).getHost().getText();
      int start = text.indexOf( "[>" );
      if( start >= 0 )
      {
        String fragmentName = text.substring( start + 2 );
        int end = fragmentName.indexOf( "<]" );
        if( end > 0 )
        {
          fragmentName = fragmentName.substring( 0, end );
          int lastDot = fragmentName.lastIndexOf( '.' );
          if( lastDot > 0 )
          {
            fragmentName = fragmentName.substring( 0, lastDot );
            FileType fileTypeByExtension = FileTypeManager.getInstance().getFileTypeByFileName( fragmentName );
            Language fileTypeLanguage = LanguageUtil.getFileTypeLanguage( fileTypeByExtension );
            if( fileTypeLanguage != null )
            {
              return fileTypeLanguage;
            }
          }
        }
      }
    }
    return null;
  }

  @NotNull
  @Override
  public Language getBaseLanguage()
  {
    return _baseLang;
  }

  @NotNull
  @Override
  public Language getTemplateDataLanguage()
  {
    return _contentLang;
  }

  @NotNull
  @Override
  public Set<Language> getLanguages()
  {
    return new HashSet<>( Arrays.asList( _baseLang, ManTemplateJavaLanguage.INSTANCE, getTemplateDataLanguage() ) );
  }

  @NotNull
  @Override
  protected MultiplePsiFilesPerDocumentFileViewProvider cloneInner( @NotNull VirtualFile virtualFile )
  {
    return new ManTemplateFileViewProvider( getManager(), virtualFile, false, _baseLang, _contentLang );
  }

  @Override
  public IElementType getContentElementType( @NotNull Language lang )
  {
    if( lang.is( ManTemplateJavaLanguage.INSTANCE ) )
    {
      return getOrCreateTemplateJavaElementType( getBaseLanguage() );
    }
    else if( lang.is( getTemplateDataLanguage() ) )
    {
      return getOrCreateTemplateDataElementType( getBaseLanguage() );
    }
    else
    {
      return null;
    }
  }

  @Override
  protected PsiFile createFile( @NotNull Language lang )
  {
    ParserDefinition parserDefinition = getDefinition( lang );
    if( parserDefinition == null )
    {
      return null;
    }

    if( lang.is( ManTemplateJavaLanguage.INSTANCE ) )
    {
      PsiFileImpl file = (PsiFileImpl)parserDefinition.createFile( this );
      file.setContentElementType( getContentElementType( lang ) );
      return file;
    }
    else if( lang.is( getTemplateDataLanguage() ) )
    {
      PsiFileImpl file = (PsiFileImpl)parserDefinition.createFile( this );
      file.setContentElementType( getContentElementType( lang ) );
      return file;
    }
    else if( lang.isKindOf( getBaseLanguage() ) )
    {
      return parserDefinition.createFile( this );
    }
    else
    {
      return null;
    }
  }

  private ParserDefinition getDefinition( Language lang )
  {
    ParserDefinition parserDefinition;
    if( lang.isKindOf( getBaseLanguage() ) )
    {
      parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage( lang.is( getBaseLanguage() ) ? lang : getBaseLanguage() );
    }
    else if( lang.is( ManTemplateJavaLanguage.INSTANCE ) )
    {
      parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage( ManTemplateJavaLanguage.INSTANCE );
    }
    else
    {
      parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage( lang );
    }
    return parserDefinition;
  }
}

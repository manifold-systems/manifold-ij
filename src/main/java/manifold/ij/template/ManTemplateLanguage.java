package manifold.ij.template;

import com.intellij.lang.InjectableLanguage;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.vfs.VirtualFile;

public class ManTemplateLanguage extends Language implements InjectableLanguage
{
  public static final ManTemplateLanguage INSTANCE = new ManTemplateLanguage();

  private ManTemplateLanguage()
  {
    super( "ManTL" );
  }

  /**
   * The convention for a ManTL file encodes the file extension of the content before the .mtl extension:
   * {@code MyFile.html.mtl}
   */
  public static FileType getContentLanguage( VirtualFile vfile )
  {
    String nameWithoutExtension = vfile.getNameWithoutExtension();
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName( nameWithoutExtension );
    if( fileType == FileTypes.UNKNOWN )
    {
      fileType = PlainTextFileType.INSTANCE;
    }
    return fileType;
  }
}
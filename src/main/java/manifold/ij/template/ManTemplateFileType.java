package manifold.ij.template;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeEditorHighlighterProviders;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.TemplateLanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ManTemplateFileType extends LanguageFileType implements TemplateLanguageFileType
{
  public static final ManTemplateFileType INSTANCE = new ManTemplateFileType();

  private ManTemplateFileType()
  {
    super( ManTemplateLanguage.INSTANCE );
    FileTypeEditorHighlighterProviders.INSTANCE.addExplicitExtension(
      this,
      ( @Nullable Project project,
        @NotNull FileType fileType,
        @Nullable VirtualFile virtualFile,
        @NotNull EditorColorsScheme editorColorsScheme ) -> new ManTemplateLayeredHighlighter( project, virtualFile, editorColorsScheme ) );
  }

  @NotNull
  @Override
  public String getName()
  {
    return "ManTL";
  }

  @NotNull
  @Override
  public String getDescription()
  {
    return "ManTL file";
  }

  @NotNull
  @Override
  public String getDefaultExtension()
  {
    return "mtl";
  }

  @Nullable
  @Override
  public Icon getIcon()
  {
    return ManTemplateIcons.FILE;
  }
}
package manifold.ij.template.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiImportHolder;
import manifold.ij.template.ManTemplateFileType;
import manifold.ij.template.ManTemplateLanguage;
import org.jetbrains.annotations.NotNull;

public class ManTemplateFile extends PsiFileBase
  implements PsiImportHolder //!! must implement PsiImportHolder for full pass analysis and error highlighting to work
{                            //!! see HighlightVisitorImpl#suitableForFile
  public ManTemplateFile( @NotNull FileViewProvider viewProvider )
  {
    super( viewProvider, ManTemplateLanguage.INSTANCE );
  }

  @NotNull
  @Override
  public FileType getFileType()
  {
    return ManTemplateFileType.INSTANCE;
  }

  @Override
  public String toString()
  {
    return "Manifold Template File";
  }

  @Override
  public boolean importClass( PsiClass aClass )
  {
    return false;
  }
}
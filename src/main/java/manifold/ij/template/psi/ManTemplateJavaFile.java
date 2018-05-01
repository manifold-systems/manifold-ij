package manifold.ij.template.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.source.PsiJavaFileBaseImpl;
import manifold.ij.template.ManTemplateJavaLanguage;
import org.jetbrains.annotations.NotNull;

public class ManTemplateJavaFile extends PsiJavaFileBaseImpl
{
  public ManTemplateJavaFile( @NotNull FileViewProvider viewProvider )
  {
    super( ManTemplateJavaParserDefinition.FILE, ManTemplateJavaParserDefinition.FILE, viewProvider );
  }

  @NotNull
  @Override
  public FileType getFileType()
  {
    return ManTemplateJavaFileType.INSTANCE;
  }

  @Override
  @NotNull
  public Language getLanguage()
  {
    return ManTemplateJavaLanguage.INSTANCE;
  }


  @Override
  public String toString()
  {
    return "Manifold Template JAVA File";
  }

  @Override
  public boolean importClass( PsiClass aClass )
  {
    throw new UnsupportedOperationException( "Need to add import to template directives" );
  }
}
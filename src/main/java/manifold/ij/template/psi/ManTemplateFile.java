package manifold.ij.template.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiImportHolder;
import com.intellij.psi.ServerPageFile;
import java.awt.EventQueue;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.template.ManTemplateFileType;
import manifold.ij.template.ManTemplateLanguage;
import manifold.ij.util.MessageUtil;
import org.jetbrains.annotations.NotNull;


import static manifold.ij.template.psi.ManTemplateParser.Comment;
import static manifold.ij.template.psi.ManTemplateParser.Directive;

public class ManTemplateFile extends PsiFileBase
  implements PsiImportHolder, //!! must implement PsiImportHolder for full pass analysis and error highlighting to work, see HighlightVisitorImpl#suitableForFile
  ServerPageFile   //!! musts implement ServerPageFile to avoid psi checking at the file level e.g., no package stmt etc.
{
  ManTemplateFile( @NotNull FileViewProvider viewProvider )
  {
    super( viewProvider, ManTemplateLanguage.INSTANCE );
    warnIfManifoldTemplatesNotConfiguredForProject();
  }

  private void warnIfManifoldTemplatesNotConfiguredForProject()
  {
    EventQueue.invokeLater(
      () -> {
        ManModule module = ManProject.getModule( ManTemplateFile.this );
        if( module != null &&
          module.getTypeManifolds().stream()
          .noneMatch( tm -> tm.getClass().getSimpleName().equals( "TemplateManifold" ) ) )
        {
          MessageUtil.showWarning( getProject(), "The use of templates in Module '${module.getName()}' requires a dependency on <b>manifold-templates</b> or <b>manifold-all</b>" );
        }
      } );
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
  public boolean importClass( PsiClass importClass )
  {
    ManTemplateElementImpl parent = (ManTemplateElementImpl)getFirstChild();
    PsiElement lastImport = findLastImport( parent );
    if( lastImport != null )
    {
      ManTemplateFile dummyFile = (ManTemplateFile)PsiFileFactory.getInstance( getProject() )
        .createFileFromText( "dummy.mtl", ManTemplateFileType.INSTANCE,
          "\n<%@ import ${importClass.getQualifiedName()} %>" );
      PsiElement newLine = dummyFile.getFirstChild().getFirstChild();
      PsiElement importDirective = newLine.getNextSibling();

      parent.addRangeAfter( newLine, importDirective, lastImport );
    }
    else
    {
      ManTemplateFile dummyFile = (ManTemplateFile)PsiFileFactory.getInstance( getProject() )
        .createFileFromText( "dummy.mtl", ManTemplateFileType.INSTANCE,
          "<%@ import ${importClass.getQualifiedName()} %>\n" );
      PsiElement importDirective = dummyFile.getFirstChild().getFirstChild();
      PsiElement newLine = importDirective.getNextSibling();

      parent.addRangeBefore( importDirective, newLine, parent.getFirstChild() );
    }
    return true;
  }

  private PsiElement findLastImport( ManTemplateElementImpl parent )
  {
    ManTemplateElementImpl lastImport = null;
    PsiElement csr = parent.getFirstChild();
    while( csr != null )
    {
      if( csr instanceof ManTemplateElementImpl )
      {
        ManTemplateElementImpl elem = (ManTemplateElementImpl)csr;
        if( isImportDirective( elem ) )
        {
          lastImport = elem;
          csr = csr.getNextSibling();
          continue;
        }
        else if( isCommentDirective( elem ) )
        {
          csr = csr.getNextSibling();
          continue;
        }
      }
      else if( csr instanceof ManTemplateTokenImpl )
      {
        ManTemplateTokenImpl token = (ManTemplateTokenImpl)csr;
        if( token.getTokenType() == ManTemplateTokenType.CONTENT )
        {
          csr = csr.getNextSibling();
          continue;
        }
      }
      break;
    }
    return lastImport;
  }

  private boolean isImportDirective( ManTemplateElementImpl elem )
  {
    final ASTNode directive = elem.getNode();
    if( directive.getElementType() == Directive )
    {
      final ASTNode child = directive.getFirstChildNode();
      if( child.getElementType() == ManTemplateTokenType.DIR_ANGLE_BEGIN &&
          child.getTreeNext() != null && child.getTreeNext().getElementType() == ManTemplateTokenType.DIRECTIVE )
      {
        String text = child.getTreeNext().getText().trim();
        return text.startsWith( DirectiveParser.IMPORT );
      }
    }
    return false;
  }

  private boolean isCommentDirective( ManTemplateElementImpl elem )
  {
    final ASTNode directive = elem.getNode();
    return directive.getElementType() == Comment;
  }
}
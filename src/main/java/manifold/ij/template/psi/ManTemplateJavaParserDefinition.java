package manifold.ij.template.psi;

import com.intellij.psi.JavaTokenType;
import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.lang.PsiParser;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.java.stubs.JavaStubElementType;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LazyParseableElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.java.MethodElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import manifold.ij.template.ManTemplateJavaLanguage;
import manifold.ij.util.ManVersionUtil;
import manifold.util.ReflectUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class ManTemplateJavaParserDefinition extends JavaParserDefinition
{
  public static final IFileElementType FILE = new ManTemplateJavaFileElementType();

  @Override
  @NotNull
  public Lexer createLexer( @Nullable Project project )
  {
    return new ManTemplateJavaLexer( project, null );
  }

  @Override
  public PsiParser createParser( Project project )
  {
    return new ManTemplateJavaParser();
  }

  @Override
  public IFileElementType getFileNodeType()
  {
    return FILE;
  }

  @Override
  @NotNull
  public TokenSet getWhitespaceTokens()
  {
    if( ManVersionUtil.is2018_2_orGreater.get() )
    {
      return ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET;
    }
    return (TokenSet)ReflectUtil.field( ElementType.class, "JAVA_WHITESPACE_BIT_SET" ).getStatic();
  }

  @Override
  @NotNull
  public TokenSet getCommentTokens()
  {
    return ElementType.JAVA_COMMENT_BIT_SET;
  }

  @Override
  @NotNull
  public TokenSet getStringLiteralElements()
  {
    return TokenSet.create( JavaElementType.LITERAL_EXPRESSION );
  }

  @Override
  @NotNull
  public PsiElement createElement( final ASTNode node )
  {
    final IElementType type = node.getElementType();
    if( type instanceof JavaStubElementType )
    {
      return ((JavaStubElementType)type).createPsi( node );
    }

    throw new IllegalStateException( "Incorrect node for JavaParserDefinition: " + node + " (" + type + ")" );
  }

  @Override
  public PsiFile createFile( FileViewProvider viewProvider )
  {
    return new ManTemplateJavaFile( viewProvider );
  }

  public static class ManTemplateJavaFileElementType extends IFileElementType
  {
    private ManTemplateJavaFileElementType()
    {
      super( ManTemplateJavaLanguage.INSTANCE );
    }

    protected ASTNode doParseContents( @NotNull ASTNode chameleon, @NotNull PsiElement psi )
    {
      Project project = psi.getProject();
      Language languageForParser = getLanguageForParser( psi );
      PsiBuilder builder = PsiBuilderFactory.getInstance().createBuilder( project, chameleon, new ManTemplateJavaLexer( project, chameleon ), languageForParser, chameleon.getChars() );
      PsiParser parser = LanguageParserDefinitions.INSTANCE.forLanguage( languageForParser ).createParser( project );
      ASTNode node = parser.parse( this, builder );
      return node.getFirstChildNode();
    }

    @Nullable
    @Override
    public ASTNode parseContents( ASTNode chameleon )
    {
      LazyParseableElement rootFileNode = ASTFactory.lazy( (IFileElementType)chameleon.getElementType(), null );

      // Parse the template's directives and Java as a Java AST
      ASTNode innards = super.parseContents( chameleon );

      if( innards != null )
      {
        // Remove the imports, which must appear before other directives, and add them to psi file before class
        ASTNode remainingInnards = removeAndAddImports( innards, rootFileNode );

        // Add a class to host a method
        CompositeElement classStmtNode = makeClassStmt( rootFileNode );

        // Append the extends list to the class decl
        remainingInnards = makeExtendsList( classStmtNode, remainingInnards );

        // Add the method, the code block of the method is the parent of the template's parsed Java AST
        CompositeElement block = makeMethodStmt( classStmtNode );

        if( remainingInnards != null )
        {
          block.rawAddChildrenWithoutNotifications( (TreeElement)remainingInnards );
        }
        return rootFileNode.rawFirstChild();
      }

      return null;
    }

    private ASTNode removeAndAddImports( ASTNode innards, LazyParseableElement rootFileNode )
    {
      makePackageStmt( rootFileNode );

      CompositeElement importList = new CompositeElement( JavaElementType.IMPORT_LIST );
      rootFileNode.rawAddChildrenWithoutNotifications( importList );

      // The imports must appear before other directives, we grab the beginning
      // of the file until the first non-import element type.  The rest will be
      // a child of the code block.
      ASTNode csr = innards;
      while( csr != null &&
             (csr.getElementType() == JavaElementType.IMPORT_STATEMENT ||
              csr.getElementType() == JavaTokenType.WHITE_SPACE ||
              csr.getElementType() == JavaTokenType.C_STYLE_COMMENT ||
              csr.getElementType() == JavaTokenType.END_OF_LINE_COMMENT) )
      {
        csr = csr.getTreeNext();
      }
      if( csr != innards )
      {
        if( csr != null )
        {
          ReflectUtil.method( innards, "rawRemoveUpToWithoutNotifications", TreeElement.class, boolean.class ).invoke( csr, false );
        }
        importList.rawAddChildrenWithoutNotifications( (TreeElement)innards );
      }
      return csr;
    }

    private void makePackageStmt( LazyParseableElement rootFileNode )
    {
//      PsiPackageStatementImpl pkgStmt = new PsiPackageStatementImpl();
//      rootFileNode.rawAddChildrenWithoutNotifications( pkgStmt );
//
//      PsiJavaCodeReferenceElementImpl codeRef = new PsiJavaCodeReferenceElementImpl();
//      pkgStmt.rawAddChildrenWithoutNotifications( codeRef );
//
//      CompositeElement pkgId = new LightIdentifierImpl( "stuff" );
//      codeRef.rawAddChildrenWithoutNotifications( pkgId );
//
//      PsiJavaCodeReferenceElementImpl codeRef2 = new PsiJavaCodeReferenceElementImpl();
//      codeRef.rawAddChildrenWithoutNotifications( codeRef2 );
//
//      CompositeElement pkgId2 = new LightIdentifierImpl( "res" );
//      codeRef2.rawAddChildrenWithoutNotifications( pkgId2 );
    }

    @NotNull
    private CompositeElement makeClassStmt( LazyParseableElement rootFileNode )
    {
      CompositeElement classStmtNode = new CompositeElement( JavaElementType.CLASS );
      rootFileNode.rawAddChildrenWithoutNotifications( classStmtNode );

      CompositeElement synthModifierList = new CompositeElement( JavaElementType.MODIFIER_LIST );
      classStmtNode.rawAddChildrenWithoutNotifications( synthModifierList );

      CompositeElement programClassDecl = new LightIdentifierImpl( "Foo" );
      classStmtNode.rawAddChildrenWithoutNotifications( programClassDecl );

      CompositeElement typeParamList = new CompositeElement( JavaElementType.TYPE_PARAMETER_LIST );
      classStmtNode.rawAddChildrenWithoutNotifications( typeParamList );
      
      return classStmtNode;
    }

    private ASTNode makeExtendsList( CompositeElement classStmt, ASTNode innards )
    {
      // The imports must appear before other directives, we grab the beginning
      // of the file until the first non-import element type.  The rest will be
      // a child of the code block.
      ASTNode csr = innards;
      while( csr != null &&
             (csr.getElementType() == JavaElementType.EXTENDS_LIST ||
              csr.getElementType() == JavaTokenType.WHITE_SPACE ||
              csr.getElementType() == JavaTokenType.C_STYLE_COMMENT ||
              csr.getElementType() == JavaTokenType.END_OF_LINE_COMMENT) )
      {
        csr = csr.getTreeNext();
      }
      if( csr != innards )
      {
        if( csr != null )
        {
          ReflectUtil.method( innards, "rawRemoveUpToWithoutNotifications", TreeElement.class, boolean.class ).invoke( csr, false );
        }
        classStmt.rawAddChildrenWithoutNotifications( (TreeElement)innards );
      }
      else
      {
        // empty
        CompositeElement extendsList = new CompositeElement( JavaElementType.EXTENDS_LIST );
        classStmt.rawAddChildrenWithoutNotifications( extendsList );
      }

      return csr;
    }

    private CompositeElement makeMethodStmt( CompositeElement classStmtNode )
    {
      CompositeElement methodStmt = new MethodElement();
      classStmtNode.rawAddChildrenWithoutNotifications( methodStmt );

      CompositeElement synthModifierList = new CompositeElement( JavaElementType.MODIFIER_LIST );
      methodStmt.rawAddChildrenWithoutNotifications( synthModifierList );

      CompositeElement typeParamList = new CompositeElement( JavaElementType.TYPE_PARAMETER_LIST );
      methodStmt.rawAddChildrenWithoutNotifications( typeParamList );

      CompositeElement methodId = new LightIdentifierImpl( "rwds" );
      methodStmt.rawAddChildrenWithoutNotifications( methodId );

      CompositeElement params = new CompositeElement( JavaElementType.PARAMETER_LIST );
      methodStmt.rawAddChildrenWithoutNotifications( params );

      CompositeElement throwsList = new CompositeElement( JavaElementType.THROWS_LIST );
      methodStmt.rawAddChildrenWithoutNotifications( throwsList );

      CompositeElement block = ASTFactory.lazy( JavaElementType.CODE_BLOCK, null );
      methodStmt.rawAddChildrenWithoutNotifications( block );

      return block;
    }
  }
}

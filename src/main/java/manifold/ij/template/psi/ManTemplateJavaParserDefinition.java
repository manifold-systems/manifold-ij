package manifold.ij.template.psi;

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
    return ElementType.JAVA_WHITESPACE_BIT_SET;
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

  @Override
  public SpaceRequirements spaceExistanceTypeBetweenTokens( ASTNode left, ASTNode right )
  {
    return SpaceRequirements.MAY;
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

      // Wrap the template code in a class and method
      CompositeElement classStmtNode = makeClassStmt( rootFileNode );
      CompositeElement block = makeMethodStmt( classStmtNode );

      ASTNode innards = super.parseContents( chameleon );

      if( innards != null )
      {
        block.rawAddChildrenWithoutNotifications( (TreeElement)innards );
      }

      return block;
    }

    @NotNull
    private CompositeElement makeClassStmt( LazyParseableElement rootFileNode )
    {
      CompositeElement classStmtNode = new CompositeElement( JavaElementType.CLASS );
      rootFileNode.rawAddChildrenWithoutNotifications( classStmtNode );

      CompositeElement synthModifierList = new CompositeElement( JavaElementType.MODIFIER_LIST );
      classStmtNode.rawAddChildrenWithoutNotifications( synthModifierList );

      CompositeElement typeParamList = new CompositeElement( JavaElementType.TYPE_PARAMETER_LIST );
      classStmtNode.rawAddChildrenWithoutNotifications( typeParamList );

      CompositeElement programClassDecl = new LightIdentifierImpl( "Foo" );
      classStmtNode.rawAddChildrenWithoutNotifications( programClassDecl );

      return classStmtNode;
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

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

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.impl.PsiBuilderFactoryImpl;
import com.intellij.lang.impl.TokenSequence;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lang.java.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.TokenList;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.impl.source.tree.java.JavaFileElement;
import com.intellij.psi.text.BlockSupport;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.indexing.IndexingDataKeys;
import manifold.ext.rt.api.Jailbreak;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.util.FileUtil;
import manifold.rt.api.util.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ManPsiBuilderFactoryImpl extends PsiBuilderFactoryImpl
{
  private ThreadLocal<Stack<ASTNode>> _buildingNodes;

  public ManPsiBuilderFactoryImpl()
  {
    super();
    _buildingNodes = ThreadLocal.withInitial( Stack::new );
  }

  void pushNode( @NotNull ASTNode chameleon )
  {
    if( chameleon.getElementType().getLanguage() instanceof JavaLanguage )
    {
      _buildingNodes.get().push( chameleon );
    }
  }

  void popNode( ASTNode building )
  {
    if( building.getElementType().getLanguage() instanceof JavaLanguage )
    {
      assert _buildingNodes.get().peek() == building;
      _buildingNodes.get().pop();
    }
  }

  ASTNode peekNode()
  {
    Stack<ASTNode> nodes = _buildingNodes.get();
    return nodes.isEmpty() ? null : nodes.peek();
  }

  @NotNull
  @Override
  public PsiBuilder createBuilder( @NotNull Project project, @NotNull ASTNode chameleon, @Nullable Lexer lexer, @NotNull Language lang, @NotNull CharSequence seq )
  {
    if( !ManProject.isManifoldInUse( project ) )
    {
      return super.createBuilder( project, chameleon, lexer, lang, seq );
    }

    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage( lang );

    if( lexer instanceof JavaLexer )
    {
      // Replace lexer to handle Preprocessor
      lexer = new ManJavaLexer( (JavaLexer)lexer );
    }
    else if( lexer == null )
    {
      lexer = createLexer( project, lang );
    }
    else if( chameleon instanceof JavaFileElement )
    {
      // With stubbed PSI IntelliJ uses a JavaLexer directly to collect all tokens and jam them into a cached WrappedLexer,
      // this will not do...

      // This logic is taken from JavaParserUtil.createBuilder(), basically we create a cached WrappedLexer based on the
      // tokens emitted from a ManJavaLexer

      @Jailbreak JavaFileElement javaFile = (JavaFileElement)chameleon;
      PsiElement cachedPsi = javaFile.getCachedPsi();
      if( cachedPsi != null )
      {
        ManModule module = ManProject.getModule( cachedPsi );
        if( module != null && module.isPreprocessorEnabled() )
        {
          CharSequence indexedText = cachedPsi.getUserData( IndexingDataKeys.FILE_TEXT_CONTENT_KEY);
          if( cachedPsi instanceof PsiFile && indexedText != null )
          {
            lexer = obtainTokens( (PsiFile)cachedPsi, chameleon ).asLexer();
//            lexer = createLexer( project, lang );
          }
        }
      }

    }

    if( lexer instanceof ManJavaLexer )
    {
      // For preprocessor
      try
      {
        ((ManJavaLexer)lexer).setChameleon( chameleon );
      }
      catch( Exception ignore )
      {
        // this can happen in the debugger when it evaluates expressions
      }
    }
    
    return new ManPsiBuilderImpl( project, parserDefinition, lexer, chameleon, seq );
  }

  public static TokenList obtainTokens( @NotNull PsiFile file, ASTNode chameleon ) {
    return CachedValuesManager.getCachedValue(file, () ->
    {
      ManJavaLexer lexer = (ManJavaLexer)ManJavaParserDefinition.createLexer( PsiUtil.getLanguageLevel( file ) );
      lexer.setChameleon( chameleon );
      return CachedValueProvider.Result.create(
        TokenSequence.performLexing( file.getViewProvider().getContents(), lexer ),
        file );
    } );
  }

  @NotNull
  private static Lexer createLexer( final Project project, final Language lang )
  {
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage( lang );
    assert parserDefinition != null : "ParserDefinition absent for language: " + lang.getID();
    return parserDefinition.createLexer( project );
  }

  static PsiJavaFile getPsiFile( ASTNode chameleon )
  {
    PsiFile containingFile = null;
    PsiElement psi = chameleon.getPsi();
    if( psi != null )
    {
      if( psi instanceof PsiFile )
      {
        containingFile = (PsiFile)psi;
      }
      else if( psi.getContainingFile() != null )
      {
        containingFile = psi.getContainingFile();
        if( containingFile.getVirtualFile() == null || containingFile.getVirtualFile() instanceof LightVirtualFile )
        {
          containingFile = null;
        }
      }
    }

    if( containingFile == null )
    {
      ASTNode originalNode = Pair.getFirst( chameleon.getUserData( BlockSupport.TREE_TO_BE_REPARSED ) );
      if( originalNode == null )
      {
        return null;
      }

      containingFile = originalNode.getPsi().getContainingFile();
    }

    return containingFile instanceof PsiJavaFile && FileUtil.toVirtualFile( containingFile ) != null
           ? (PsiJavaFile)containingFile
           : null;
  }
}

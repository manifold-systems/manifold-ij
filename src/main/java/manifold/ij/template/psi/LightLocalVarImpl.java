package manifold.ij.template.psi;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.ResolveState;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiConstantEvaluationHelperImpl;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiVariableEx;
import com.intellij.psi.impl.ResolveScopeManager;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.JavaDummyHolder;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import gnu.trove.THashSet;
import java.util.Arrays;
import java.util.Set;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

public class LightLocalVarImpl extends CompositePsiElement implements PsiLocalVariable, PsiVariableEx, Constants
{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiLocalVariableImpl");
  private final String _name;

  private volatile String myCachedName;

  @SuppressWarnings({"UnusedDeclaration"})
  public LightLocalVarImpl( String name ) {
    super(LOCAL_VARIABLE);
    _name = name;
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    myCachedName = null;
  }

  @Override
  @NotNull
  public final PsiIdentifier getNameIdentifier() {
    final PsiElement element = findChildByRoleAsPsiElement( ChildRole.NAME);
    assert element instanceof PsiIdentifier : getText();
    return (PsiIdentifier)element;
  }

  @Override
  @NotNull
  public final String getName() {
    return _name;
  }

  @Override
  public void setInitializer(PsiExpression initializer) throws IncorrectOperationException
  {
    JavaSharedImplUtil.setInitializer(this, initializer);
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  @Override
  @NotNull
  public final PsiType getType() {
    return JavaSharedImplUtil.getType(getTypeElement(), getNameIdentifier());
  }

  @Override
  @NotNull
  public PsiTypeElement getTypeElement() {
    PsiTypeElement typeElement = PsiTreeUtil.getChildOfType(this, PsiTypeElement.class);
    if (typeElement != null) return typeElement;

    final PsiElement parent = getParent();
    assert parent != null : "no parent; " + this + "; [" + getText() + "]";
    final PsiLocalVariable localVariable = PsiTreeUtil.getChildOfType(parent, PsiLocalVariable.class);
    assert localVariable != null : "no local variable in " + Arrays.toString(parent.getChildren());
    typeElement = PsiTreeUtil.getChildOfType(localVariable, PsiTypeElement.class);
    assert typeElement != null : "no type element in " + Arrays.toString(localVariable.getChildren());
    return typeElement;
  }

  @Override
  public PsiModifierList getModifierList() {
    final CompositeElement parent = getTreeParent();
    if (parent == null) return null;
    final CompositeElement first = (CompositeElement)parent.findChildByType(LOCAL_VARIABLE);
    return first != null ? (PsiModifierList)first.findChildByRoleAsPsiElement(ChildRole.MODIFIER_LIST) : null;
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    final PsiModifierList modifierList = getModifierList();
    return modifierList != null && modifierList.hasModifierProperty(name);
  }

  @Override
  public PsiExpression getInitializer() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.INITIALIZER);
  }

  @Override
  public boolean hasInitializer() {
    return getInitializer() != null;
  }

  @Override
  public Object computeConstantValue() {
    return computeConstantValue(new THashSet<>());
  }

  @Override
  public Object computeConstantValue(Set<PsiVariable> visitedVars) {
    if (!hasModifierProperty( PsiModifier.FINAL)) return null;

    PsiType type = getType();
    // javac rejects all non primitive and non String constants, although JLS states constants "variables whose initializers are constant expressions"
    if ( !(type instanceof PsiPrimitiveType) && !type.equalsToText("java.lang.String")) return null;

    PsiExpression initializer = getInitializer();
    if (initializer == null) return null;
    return PsiConstantEvaluationHelperImpl.computeCastTo(initializer, getType(), visitedVars);
  }

  @Override
  public int getTextOffset() {
    return getNameIdentifier().getTextOffset();
  }

  @Override
  public void normalizeDeclaration() throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);

    final CompositeElement statement = getTreeParent();
    final PsiElement psiElement = SourceTreeToPsiMap.treeElementToPsi(statement);
    final PsiElement[] variables = psiElement instanceof PsiDeclarationStatement
                                   ? ((PsiDeclarationStatement)psiElement).getDeclaredElements() : PsiElement.EMPTY_ARRAY;
    if (variables.length > 1) {
      final PsiModifierList modifierList = getModifierList();
      final PsiTypeElement typeElement = getTypeElement();
      assert modifierList != null : getText();
      ASTNode last = statement;
      for (int i = 1; i < variables.length; i++) {
        ASTNode typeCopy = typeElement.copy().getNode();
        ASTNode modifierListCopy = modifierList.copy().getNode();
        CompositeElement variable = (CompositeElement)SourceTreeToPsiMap.psiToTreeNotNull(variables[i]);

        ASTNode comma = PsiImplUtil.skipWhitespaceAndCommentsBack(variable.getTreePrev());
        if (comma != null && comma.getElementType() == JavaTokenType.COMMA) {
          CodeEditUtil.removeChildren(statement, comma, variable.getTreePrev());
        }

        CodeEditUtil.removeChild(statement, variable);
        final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(statement);
        CompositeElement statement1 = Factory.createCompositeElement(DECLARATION_STATEMENT, charTableByTree, getManager());
        statement1.addChild(variable, null);

        ASTNode space = Factory.createSingleLeafElement( TokenType.WHITE_SPACE, " ", 0, 1, treeCharTab, getManager());
        variable.addChild(space, variable.getFirstChildNode());

        variable.addChild(typeCopy, variable.getFirstChildNode());

        if (modifierListCopy.getTextLength() > 0) {
          space = Factory.createSingleLeafElement(TokenType.WHITE_SPACE, " ", 0, 1, treeCharTab, getManager());
          variable.addChild(space, variable.getFirstChildNode());
        }

        variable.addChild(modifierListCopy, variable.getFirstChildNode());

        ASTNode semicolon = Factory.createSingleLeafElement(JavaTokenType.SEMICOLON, ";", 0, 1, treeCharTab, getManager());
        SourceTreeToPsiMap.psiToTreeNotNull(variables[i - 1]).addChild(semicolon, null);

        CodeEditUtil.addChild(statement.getTreeParent(), statement1, last.getTreeNext());

        last = statement1;
      }
    }

    JavaSharedImplUtil.normalizeBrackets(this);
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (getChildRole(child) == ChildRole.INITIALIZER){
      ASTNode eq = findChildByRole(ChildRole.INITIALIZER_EQ);
      if (eq != null){
        deleteChildInternal(eq);
      }
    }
    super.deleteChildInternal(child);
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.MODIFIER_LIST:
        return findChildByType(MODIFIER_LIST);

      case ChildRole.TYPE:
        return findChildByType(TYPE);

      case ChildRole.NAME:
        return findChildByType(JavaTokenType.IDENTIFIER);

      case ChildRole.INITIALIZER_EQ:
        return findChildByType(JavaTokenType.EQ);

      case ChildRole.INITIALIZER:
        return findChildByType( ElementType.EXPRESSION_BIT_SET);

      case ChildRole.CLOSING_SEMICOLON:
        return TreeUtil.findChildBackward(this, JavaTokenType.SEMICOLON);
    }
  }

  @Override
  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == MODIFIER_LIST) {
      return ChildRole.MODIFIER_LIST;
    }
    else if (i == TYPE) {
      return getChildRole(child, ChildRole.TYPE);
    }
    else if (i == JavaTokenType.IDENTIFIER) {
      return getChildRole(child, ChildRole.NAME);
    }
    else if (i == JavaTokenType.EQ) {
      return getChildRole(child, ChildRole.INITIALIZER_EQ);
    }
    else if (i == JavaTokenType.SEMICOLON) {
      return getChildRole(child, ChildRole.CLOSING_SEMICOLON);
    }
    else {
      if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return ChildRole.INITIALIZER;
      }
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor ) {
      ((JavaElementVisitor)visitor).visitLocalVariable(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public boolean processDeclarations( @NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    if (lastParent == null) return true;
    if (lastParent.getContext() instanceof JavaDummyHolder ) {
      return processor.execute(this, state);
    }

    if (lastParent.getParent() != this) return true;
    final ASTNode lastParentTree = SourceTreeToPsiMap.psiElementToTree(lastParent);

    return getChildRole(lastParentTree) != ChildRole.INITIALIZER ||
           processor.execute(this, state);
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProviders.getItemPresentation(this);
  }

  public String toString() {
    return "PsiLocalVariable:" + getName();
  }

  @Override
  @NotNull
  public SearchScope getUseScope() {
    final PsiElement parentElement = getParent();
    if (parentElement instanceof PsiDeclarationStatement) {
      return new LocalSearchScope(parentElement.getParent());
    }
    else {
      return ResolveScopeManager.getElementUseScope(this);
    }
  }

  @Override
  public Icon getElementIcon( final int flags) {
    RowIcon baseIcon = IconManager.getInstance().createLayeredIcon( this, PlatformIcons.VARIABLE_ICON,
      ElementPresentationUtil.getFlags( this, false ) );
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }
}

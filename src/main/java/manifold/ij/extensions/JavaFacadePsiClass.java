/*
 * Manifold
 */

package manifold.ij.extensions;

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.HierarchicalMethodSignature;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.ResolveState;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.IncorrectOperationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.swing.Icon;
import manifold.api.fs.IFile;
import manifold.ij.fs.IjFile;

public class JavaFacadePsiClass implements PsiClass
{
  public static final Key<JavaFacadePsiClass> KEY_JAVAFACADE = new Key<>("Facade");

  private List<PsiFile> _files;
  private List<IFile> _ifiles;
  private String _fqn;
  private PsiClass _delegate;

  public JavaFacadePsiClass( PsiClass delegate, List<IFile> files, String fqn )
  {
    super();

    initialize( delegate, files, fqn );
  }

  public void initialize( PsiClass delegate, List<IFile> files, String fqn )
  {
    _delegate = delegate;
    _ifiles = files;
    _fqn = fqn;
    PsiManager manager = PsiManagerImpl.getInstance( delegate.getProject() );
    _files = new ArrayList<>( _ifiles.size() );
    for( IFile ifile: _ifiles )
    {
      VirtualFile vfile = ((IjFile)ifile).getVirtualFile();
      if( vfile != null )
      {
        PsiFile file = manager.findFile( vfile );
        _files.add( file );

        Module module = vfile.getUserData( ModuleUtil.KEY_MODULE );
        if( module != null )
        {
          file.putUserData( ModuleUtil.KEY_MODULE, module );
        }
      }
    }
    _delegate.getContainingFile().putUserData( KEY_JAVAFACADE, this );
  }

  @Override
  public String getQualifiedName()
  {
    return _fqn;
  }

  @Override
  public String getName()
  {
    return ClassUtil.extractClassName( _fqn );
  }

  public String getNamespace()
  {
    return ClassUtil.extractPackageName( _fqn );
  }

  @Override
  public boolean isInterface()
  {
    return _delegate.isInterface();
  }

  @Override
  public boolean isEnum()
  {
    return _delegate.isEnum();
  }

  @Override
  public boolean isAnnotationType()
  {
    return _delegate.isAnnotationType();
  }

  
  @Override
  public PsiMethod[] getConstructors()
  {
    return _delegate.getConstructors();
  }

  
  @Override
  public PsiMethod[] getMethods()
  {
    return _delegate.getMethods();
  }

  
  @Override
  public PsiMethod[] findMethodsByName( String name, boolean checkBases )
  {
    return _delegate.findMethodsByName( name, checkBases );
  }

  @Override
  public boolean isValid()
  {
    return _delegate.isValid();
  }

  public PsiClass getDelegate()
  {
    return _delegate;
  }

  
  @Override
  public Project getProject() throws PsiInvalidElementAccessException
  {
    return _delegate.getProject();
  }

  @Override
  public boolean isWritable()
  {
    return true;
  }

  
  @Override
  public PsiManagerEx getManager()
  {
    return _files.isEmpty() ? null : (PsiManagerEx)_files.get( 0 ).getManager();
  }

  @Override
  public boolean isPhysical()
  {
    return false;
  }

  @Override
  public PsiClass getContainingClass()
  {
    return _delegate.getContainingClass();
  }

  
  @Override
  public PsiFile getContainingFile() throws PsiInvalidElementAccessException
  {
    return _delegate.getContainingFile(); //_file;
  }
  public List<PsiFile> getRawFiles()
  {
    return _files;
  }

  @Override
  public boolean hasModifierProperty( @PsiModifier.ModifierConstant String name )
  {
    return _delegate.hasModifierProperty( name );
  }

  @Override
  public PsiField findFieldByName( String name, boolean checkBases )
  {
    return _delegate.findFieldByName( name, checkBases );
  }

  @Override
  public PsiReferenceList getExtendsList()
  {
    return _delegate.getExtendsList();
  }

  @Override
  public PsiReferenceList getImplementsList()
  {
    return _delegate.getImplementsList();
  }

  
  @Override
  public PsiClassType[] getExtendsListTypes()
  {
    return _delegate.getExtendsListTypes();
  }

  
  @Override
  public PsiClassType[] getImplementsListTypes()
  {
    return _delegate.getImplementsListTypes();
  }

  @Override
  public PsiClass getSuperClass()
  {
    return _delegate.getSuperClass();
  }

  
  @Override
  public PsiClass[] getInterfaces()
  {
    return _delegate.getInterfaces();
  }

  
  @Override
  public PsiClass[] getSupers()
  {
    return _delegate.getSupers();
  }

  
  @Override
  public PsiClassType[] getSuperTypes()
  {
    return _delegate.getSuperTypes();
  }

  
  @Override
  public PsiField[] getFields()
  {
    return _delegate.getFields();
  }

  
  @Override
  public PsiClass[] getInnerClasses()
  {
    return _delegate.getInnerClasses();
  }

  
  @Override
  public PsiClassInitializer[] getInitializers()
  {
    return _delegate.getInitializers();
  }

  
  @Override
  public PsiField[] getAllFields()
  {
    return _delegate.getAllFields();
  }

  
  @Override
  public PsiMethod[] getAllMethods()
  {
    return _delegate.getAllMethods();
  }

  
  @Override
  public PsiClass[] getAllInnerClasses()
  {
    return _delegate.getAllInnerClasses();
  }

  @Override
  public PsiClass findInnerClassByName( String name, boolean checkBases )
  {
    return _delegate.findInnerClassByName( name, checkBases );
  }

  @Override
  public PsiJavaToken getLBrace()
  {
    return (PsiJavaToken)_delegate.getLBrace();
  }

  @Override
  public PsiJavaToken getRBrace()
  {
    return (PsiJavaToken)_delegate.getRBrace();
  }

  @Override
  public PsiIdentifier getNameIdentifier()
  {
    return _delegate.getNameIdentifier();
  }

  
  @Override
  public PsiElement getScope()
  {
    return _delegate.getScope();
  }

  @Override
  public boolean isInheritor( PsiClass baseClass, boolean checkDeep )
  {
    return _delegate.isInheritor( baseClass, checkDeep );
  }

  @Override
  public boolean isInheritorDeep( PsiClass baseClass,  PsiClass classToByPass )
  {
    return _delegate.isInheritorDeep( baseClass, classToByPass );
  }

  
  @Override
  public Collection<HierarchicalMethodSignature> getVisibleSignatures()
  {
    return _delegate.getVisibleSignatures();
  }

  
  @Override
  public PsiElement setName( String name ) throws IncorrectOperationException
  {
    return _delegate.setName( name );
  }

  @Override
  public PsiDocComment getDocComment()
  {
    return _delegate.getDocComment();
  }

  @Override
  public boolean isDeprecated()
  {
    return _delegate.isDeprecated();
  }

  @Override
  public boolean hasTypeParameters()
  {
    return _delegate.hasTypeParameters();
  }

  @Override
  public PsiTypeParameterList getTypeParameterList()
  {
    return _delegate.getTypeParameterList();
  }

  
  @Override
  public PsiTypeParameter[] getTypeParameters()
  {
    return _delegate.getTypeParameters();
  }

  @Override
  public ItemPresentation getPresentation()
  {
    return _delegate.getPresentation();
  }

  @Override
  public void navigate( boolean requestFocus )
  {
    final Navigatable navigatable = PsiNavigationSupport.getInstance().getDescriptor( this );
    if( navigatable != null )
    {
      navigatable.navigate( requestFocus );
    }
  }

  @Override
  public boolean canNavigate()
  {
    return true;
  }

  @Override
  public boolean canNavigateToSource()
  {
    return true;
  }

  @Override
  public PsiModifierList getModifierList()
  {
    return _delegate.getModifierList();
  }

  
  @Override
  public String getText()
  {
    //todo: handle multiple files somehow
    return _files.isEmpty() ? "" : _files.get( 0 ).getText();
  }

  
  @Override
  public char[] textToCharArray()
  {
    return getText().toCharArray();
  }

  
  @Override
  public PsiElement getNavigationElement()
  {
    return _files.isEmpty() ? null : _files.get( 0 ).getNavigationElement();
  }

  
  @Override
  public PsiElement getOriginalElement()
  {
    return _delegate.getOriginalElement();
  }

//  @Override
//  public void checkAdd( PsiElement element ) throws IncorrectOperationException
//  {
//
//  }

  
  @Override
  public GlobalSearchScope getResolveScope()
  {
    return GlobalSearchScope.allScope( getProject() );
  }

  
  @Override
  public SearchScope getUseScope()
  {
    return PsiClassImplUtil.getClassUseScope( this );
  }

  @Override
  public Icon getIcon( int flags )
  {
    return _files.isEmpty() ? null : _files.get( 0 ).getIcon( flags );
  }

  public PsiElement add( PsiElement element ) throws IncorrectOperationException
  {
    return _delegate.add( element );
  }

  
  @Override
  public String toString()
  {
    return _delegate.toString();
  }

  ///


  
  @Override
  public PsiMethod findMethodBySignature( PsiMethod patternMethod, boolean checkBases )
  {
    return _delegate.findMethodBySignature( patternMethod, checkBases );
  }

  
  @Override
  public PsiMethod[] findMethodsBySignature( PsiMethod patternMethod, boolean checkBases )
  {
    return _delegate.findMethodsBySignature( patternMethod, checkBases );
  }

  
  @Override
  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName( String name, boolean checkBases )
  {
    return _delegate.findMethodsAndTheirSubstitutorsByName( name, checkBases );
  }

  
  @Override
  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors()
  {
    return _delegate.getAllMethodsAndTheirSubstitutors();
  }

  
  @Override
  public Language getLanguage()
  {
    return _delegate.getLanguage();
  }

  
  @Override
  public PsiElement[] getChildren()
  {
    return _delegate.getChildren();
  }

  @Override
  public PsiElement getParent()
  {
    return _delegate.getParent();
  }

  @Override
  public PsiElement getFirstChild()
  {
    return _delegate.getFirstChild();
  }

  @Override
  public PsiElement getLastChild()
  {
    return _delegate.getLastChild();
  }

  @Override
  public PsiElement getNextSibling()
  {
    return _delegate.getNextSibling();
  }

  @Override
  public PsiElement getPrevSibling()
  {
    return _delegate.getPrevSibling();
  }

  @Override
  public TextRange getTextRange()
  {
    return _delegate.getTextRange();
  }

  @Override
  public int getStartOffsetInParent()
  {
    return _delegate.getStartOffsetInParent();
  }

  @Override
  public int getTextLength()
  {
    return _delegate.getTextLength();
  }

  
  @Override
  public PsiElement findElementAt( int offset )
  {
    return _delegate.findElementAt( offset );
  }

  
  @Override
  public PsiReference findReferenceAt( int offset )
  {
    return _delegate.findReferenceAt( offset );
  }

  @Override
  public int getTextOffset()
  {
    return _delegate.getTextOffset();
  }

  @Override
  public boolean textMatches( CharSequence text )
  {
    return _delegate.textMatches( text );
  }

  @Override
  public boolean textMatches( PsiElement element )
  {
    return _delegate.textMatches( element );
  }

  @Override
  public boolean textContains( char c )
  {
    return _delegate.textContains( c );
  }

  @Override
  public void accept( PsiElementVisitor visitor )
  {
    _delegate.accept( visitor );
  }

  @Override
  public void acceptChildren( PsiElementVisitor visitor )
  {
    _delegate.acceptChildren( visitor );
  }

  @Override
  public PsiElement copy()
  {
    return new JavaFacadePsiClass( (PsiClass)_delegate.copy(), _ifiles, _fqn );
  }

  @Override
  public PsiElement addBefore( PsiElement element,  PsiElement anchor ) throws IncorrectOperationException
  {
    return _delegate.addBefore( element, anchor );
  }

  @Override
  public PsiElement addAfter( PsiElement element,  PsiElement anchor ) throws IncorrectOperationException
  {
    return _delegate.addAfter( element, anchor );
  }

  @Override
  public void checkAdd( PsiElement element ) throws IncorrectOperationException
  {
    _delegate.checkAdd( element );
  }

  @Override
  public PsiElement addRange( PsiElement first, PsiElement last ) throws IncorrectOperationException
  {
    return _delegate.addRange( first, last );
  }

  @Override
  public PsiElement addRangeBefore( PsiElement first, PsiElement last, PsiElement anchor ) throws IncorrectOperationException
  {
    return _delegate.addRangeBefore( first, last, anchor );
  }

  @Override
  public PsiElement addRangeAfter( PsiElement first, PsiElement last, PsiElement anchor ) throws IncorrectOperationException
  {
    return _delegate.addRangeAfter( first, last, anchor );
  }

  @Override
  public void delete() throws IncorrectOperationException
  {
    _delegate.delete();
  }

  @Override
  public void checkDelete() throws IncorrectOperationException
  {
    _delegate.checkDelete();
  }

  @Override
  public void deleteChildRange( PsiElement first, PsiElement last ) throws IncorrectOperationException
  {
    _delegate.deleteChildRange( first, last );
  }

  @Override
  public PsiElement replace( PsiElement newElement ) throws IncorrectOperationException
  {
    return _delegate.replace( newElement );
  }

  
  @Override
  public PsiReference getReference()
  {
    return _delegate.getReference();
  }

  
  @Override
  public PsiReference[] getReferences()
  {
    return _delegate.getReferences();
  }

  
  @Override
  public <T> T getCopyableUserData( Key<T> key )
  {
    return _delegate.getCopyableUserData( key );
  }

  @Override
  public <T> void putCopyableUserData( Key<T> key,  T value )
  {
    _delegate.putCopyableUserData( key, value );
  }

  @Override
  public boolean processDeclarations( PsiScopeProcessor processor, ResolveState state,  PsiElement lastParent, PsiElement place )
  {
    return _delegate.processDeclarations( processor, state, lastParent, place );
  }

  
  @Override
  public PsiElement getContext()
  {
    return _delegate.getContext();
  }

  @Override
  public ASTNode getNode()
  {
    return _delegate.getNode();
  }

  @Override
  public boolean isEquivalentTo( PsiElement another )
  {
    return _delegate.isEquivalentTo( another );
  }

  
  @Override
  public <T> T getUserData( Key<T> key )
  {
    return _delegate.getUserData( key );
  }

  @Override
  public <T> void putUserData( Key<T> key,  T value )
  {
    _delegate.putUserData( key, value );
  }

  public Module getModule()
  {
    return ProjectRootManager.getInstance( getProject() ).getFileIndex()
      .getModuleForFile( getRawFiles().get( 0 ).getVirtualFile() );
  }
}

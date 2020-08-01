package manifold.ij.core;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.util.PsiUtil;
import manifold.api.fs.IFileFragment;
import manifold.api.fs.IFileSystem;
import manifold.api.host.IModule;
import manifold.api.host.ITypeSystemListener;
import manifold.internal.host.AbstractManifoldHost;
import manifold.internal.javac.JavaParser;

/**
 * Override the default behavior to support listening to IJ's file system and type system
 */
public class IjManifoldHost extends AbstractManifoldHost
{
  private final ManProject _project;

  public IjManifoldHost( ManProject project )
  {
    _project = project;
  }

  public ManProject getProject()
  {
    return _project;
  }

  @Override
  public IFileSystem getFileSystem()
  {
    return _project.getFileSystem();
  }

  @Override
  public JavaParser getJavaParser()
  {
    throw new UnsupportedOperationException( "JavaParser is not intended for use within IntelliJ, " +
                                             "instead you should use the IntelliJ's type system and services" );
  }

  @Override
  public IModule getSingleModule()
  {
    throw new UnsupportedOperationException( "The single module is only for use with runtime and javac parsing," +
                                             "IntelliJ plugin has its own model of module system." );
  }

  @Override
  public void addTypeSystemListenerAsWeakRef( Object ctx, ITypeSystemListener listener )
  {
    ManProject manProject = ctx == null ? _project : getManProject( ctx );
    if( !manProject.equals( _project ) )
    {
      throw new IllegalStateException( "Unrelated context: " + ctx + " belongs to project '" + manProject.getNativeProject().getName() + "', execting project '" + _project.getNativeProject().getName() + "'" );
    }

    manProject.getFileModificationManager().getManRefresher().addTypeSystemListenerAsWeakRef( listener );
  }

  @Override
  public void createdType( IFileFragment iFile, String[] strings )
  {
  }

  @Override
  public String getArrayTypeName()
  {
    Project project = _project.getNativeProject();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory( project );
    PsiClass arrayClass = factory.getArrayClass( PsiUtil.getLanguageLevel( project ) );
    return arrayClass.getQualifiedName();
  }

  private ManProject getManProject( Object ctx )
  {
    ManProject manProject;
    if( ctx instanceof Project )
    {
      manProject = ManProject.manProjectFrom( (Project)ctx );
    }
    else if( ctx instanceof Module )
    {
      manProject = ManProject.manProjectFrom( (Module)ctx );
    }
    else if( ctx instanceof ManModule )
    {
      manProject = ((ManModule)ctx).getProject();
    }
    else if( ctx instanceof ManProject )
    {
      manProject = (ManProject)ctx;
    }
    else
    {
      throw new IllegalArgumentException( "Context is invalid: " + ctx );
    }
    return manProject;
  }
}

package manifold.ij.core;

import com.intellij.compiler.server.BuildManagerListener;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import manifold.api.fs.IDirectory;
import manifold.api.fs.IFile;
import manifold.api.type.ContributorKind;
import manifold.api.type.ITypeManifold;

/**
 */
public class ManStaleClassCleaner implements BuildManagerListener
{

  @Override
  public void buildStarted( Project project, UUID sessionId, boolean isAutomake )
  {
    ManProject manProject = ManProject.manProjectFrom( project );
    List<ManModule> modules = manProject.getModules();
    for( ManModule manModule: modules )
    {
      List<IDirectory> outputPath = manModule.getOutputPath();
      List<ITypeManifold> tms = manModule.getTypeManifolds().stream()
        .filter( tm -> tm.getContributorKind() != ContributorKind.Supplemental )
        .collect( Collectors.toList() );
      for( ITypeManifold tm: tms )
      {
        Collection<String> allTypeNames = tm.getAllTypeNames();
        for( String fqn: allTypeNames )
        {
          List<IFile> filesForType = tm.findFilesForType( fqn );
          for( File classFile: findClassFiles( fqn, outputPath ) )
          {
            long classTimestamp = classFile.lastModified();
            for( IFile file: filesForType )
            {
              if( file.exists() && file.toJavaFile().lastModified() > classTimestamp )
              {
                //noinspection ResultOfMethodCallIgnored
                classFile.delete();
                break;
              }
            }
          }
        }
      }
    }
  }

  private Set<File> findClassFiles( String fqn, List<IDirectory> outputPath )
  {
    Set<File> classFiles = new HashSet<>( 2 );
    for( IDirectory path: outputPath )
    {
      findClassFiles( fqn, path, classFiles );
    }
    return classFiles;
  }

  private void findClassFiles( String fqn, IDirectory outputPath, Set<File> classFiles )
  {
    if( outputPath == null )
    {
      return;
    }
    
    File baseOutputDir = outputPath.toJavaFile();
    if( !baseOutputDir.exists() )
    {
      return;
    }

    String pkg = "";
    int iDot = fqn.lastIndexOf( '.' );
    if( iDot > 0 )
    {
      pkg = fqn.substring( 0, iDot );
    }
    pkg = pkg.replace( '.', File.separatorChar );
    File pkgDir = new File( baseOutputDir, pkg );
    if( !pkgDir.exists() )
    {
      return;
    }

    String className = fqn.substring( iDot+1 );

    File[] relevantClasses = pkgDir.listFiles( ( dir, name ) -> name.equals( className + ".class" ) ||
                                                                (name.endsWith( ".class" ) && name.startsWith( className + '$' )) );
    classFiles.addAll( Arrays.asList( relevantClasses ) );
  }
}

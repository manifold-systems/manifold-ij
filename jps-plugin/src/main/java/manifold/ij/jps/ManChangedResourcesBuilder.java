package manifold.ij.jps;

import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.impl.BuildOutputConsumerImpl;
import org.jetbrains.jps.builders.java.ResourceRootDescriptor;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.FSOperations;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.ResourcesTarget;
import org.jetbrains.jps.incremental.fs.CompilationRound;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.resources.ResourcesBuilder;
import org.jetbrains.jps.model.JpsTypedElement;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;

public class ManChangedResourcesBuilder extends ResourcesBuilder
{
  private BuildOutputConsumerImpl _oc;
  private ResourcesTarget _target;
  private List<File> _tempMainClasses;

  @Override
  public void buildStarted( CompileContext context )
  {
    super.buildStarted( context );
    IjIncrementalCompileDriver.FILES = new ArrayList<>();
  }

  public void buildFinished( CompileContext context )
  {
    if( _tempMainClasses != null )
    {
      deleteTempMainSourceClasses( context );

      registerClasses();

      _tempMainClasses = null;
    }
  }

  private void deleteTempMainSourceClasses( CompileContext context )
  {
    for( File tempMainClass : _tempMainClasses )
    {
      try
      {
        FSOperations.markDeleted( context, tempMainClass );
        FSOperations.markDeleted( context, tempMainClass.getParentFile() );
      }
      catch( Throwable ignore )
      {
      }

      //noinspection ResultOfMethodCallIgnored
      tempMainClass.delete();
      //noinspection ResultOfMethodCallIgnored
      tempMainClass.getParentFile().delete();
    }
  }

  @Override
  public void build( @NotNull ResourcesTarget target, @NotNull DirtyFilesHolder<ResourceRootDescriptor, ResourcesTarget> holder,
                     @NotNull BuildOutputConsumer outputConsumer, @NotNull CompileContext context ) throws ProjectBuildException
  {
//## We need to handle rebuild as well to maintain mappings from resource files to class files (for hot swap).
//## Also, because we are rebuilding resources here and maintaining a complete mapping, we no longer need ManStaleClassCleaner.
//    if( JavaBuilderUtil.isForcedRecompilationAllJavaModules( context ) )
//    {
//      // only care about incremental builds
//      return;
//    }

    try
    {
      List<File> changedFiles = new ArrayList<>();
      Map<ResourceRootDescriptor, Boolean> skippedRoots = new HashMap<>();
      holder.processDirtyFiles( ( target_, file, sourceRoot ) -> {
        Boolean isSkipped = skippedRoots.get( sourceRoot );
        if( isSkipped == null )
        {
          File outputDir = target_.getOutputDir();
          isSkipped = outputDir == null || FileUtil.filesEqual( outputDir, sourceRoot.getRootFile() );
          skippedRoots.put( sourceRoot, isSkipped );
        }
        if( isSkipped )
        {
          return true;
        }

        changedFiles.add( file );
        return !context.getCancelStatus().isCanceled();
      } );


      if( !changedFiles.isEmpty() )
      {
        IjIncrementalCompileDriver.FILES.addAll( changedFiles );
        _tempMainClasses = makeTempMainClasses( context, target );
        _oc = (BuildOutputConsumerImpl)outputConsumer;
        _target = target;
      }

      context.checkCanceled();

      context.processMessage( new ProgressMessage( "" ) );
    }
    catch( BuildDataCorruptedException | ProjectBuildException e )
    {
      throw e;
    }
    catch( Exception e )
    {
      deleteTempMainSourceClasses( context );
      throw new ProjectBuildException( e.getMessage(), e );
    }
  }

  private void registerClasses()
  {
    if( IjIncrementalCompileDriver.INSTANCE == null )
    {
      return;
    }

    Map<File, Set<String>> typesToFile = IjIncrementalCompileDriver.INSTANCE.getTypesToFile();
    for( Map.Entry<File, Set<String>> entry : typesToFile.entrySet() )
    {
      Set<String> types = entry.getValue();
      for( String fqn : types )
      {
        try
        {
          File classFile = findClassFile( fqn, _target.getOutputDir() );
          if( classFile != null )
          {
            File resourceFile = entry.getKey();
            _oc.registerOutputFile( classFile, Collections.singleton( resourceFile.getPath() ) );
          }
        }
        catch( IOException e )
        {
          throw new RuntimeException( e );
        }
      }
    }

    // Send FileGeneratedEvent for the changed class files (for hot swap debugging)
    _oc.fireFileGeneratedEvent();
  }

  private File findClassFile( String fqn, File outputPath )
  {
    String rootRelativeClassFile = fqn.replace( '.', File.separatorChar ) + ".class";
    File classFile = new File( outputPath, rootRelativeClassFile );
    return classFile.isFile() ? classFile : null;
  }

  private List<File> makeTempMainClasses( CompileContext context, ResourcesTarget target )
  {
    String manifold_temp_main_ = "_Manifold_Temp_Main_";

    List<File> tempMainClasses = new ArrayList<>();
    int index = 0;
    for( JpsModuleSourceRoot jpsSourceRoot : target.getModule().getSourceRoots() )
    {
      if( !(jpsSourceRoot instanceof JpsTypedElement) || !(((JpsTypedElement)jpsSourceRoot).getType() instanceof JavaSourceRootType) )
      {
        continue;
      }

      // generate file in '_temp_' package, Java 9 modular projects do not support the default/empty package
      File sourceRoot = new File( jpsSourceRoot.getFile(), "_temp_" );
      //noinspection ResultOfMethodCallIgnored
      sourceRoot.mkdir();

      index++;
      File tempMainClass = new File( sourceRoot, manifold_temp_main_ + index + ".java" );
      tempMainClass.deleteOnExit(); // in case the compiler exits abnormally
      try
      {
        //noinspection ResultOfMethodCallIgnored
        tempMainClass.createNewFile();
        FileWriter writer = new FileWriter( tempMainClass );
        writer.write(
          "//!! Temporary generated file to facilitate incremental compilation of Manifold resources\n" +
          "package _temp_;\n" +
          "\n" +
          "import manifold.api.type.IncrementalCompile;\n" +
          "\n" +
          "@IncrementalCompile( driverClass = \"manifold.ij.jps.IjIncrementalCompileDriver\" )\n" +
          "public class " + manifold_temp_main_ + index + "\n" +
          "{\n" +
          "}\n"
        );
        writer.flush();
        writer.close();
        FSOperations.markDirty( context, CompilationRound.CURRENT, tempMainClass );
        tempMainClasses.add( tempMainClass );
      }
      catch( IOException e )
      {
        throw new RuntimeException( "Failed to create Manifold 'main' class: " + tempMainClass.getAbsolutePath(), e );
      }
    }
    return tempMainClasses;
  }
}

package manifold.ij.jps;

import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;

/**
 * The sequence of events for a build:
 *
 * - buildStarted()
 * --- foreach module (test) call build()
 * --- foreach module (production) call build()
 * --- foreach module call javac
 * - buildFinished()
 */
public class ManChangedResourcesBuilder extends ResourcesBuilder
{
  private List<File> _tempMainClasses;
  private Map<File, Data> _fileToData;

  @Override
  public void buildStarted( CompileContext context )
  {
    super.buildStarted( context );
    _tempMainClasses = new ArrayList<>();
     _fileToData = new ConcurrentHashMap<>();
  }

  public void buildFinished( CompileContext context )
  {
    if( !_tempMainClasses.isEmpty() )
    {
      deleteTempMainSourceClasses( context );

      registerClasses();

      IjIncrementalCompileDriver.INSTANCES.set( null );
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
        File outputDir = target_.getOutputDir();
        if( isSkipped == null )
        {
          isSkipped = outputDir == null || FileUtil.filesEqual( outputDir, sourceRoot.getRootFile() );
          skippedRoots.put( sourceRoot, isSkipped );
        }
        if( isSkipped )
        {
          return true;
        }

        changedFiles.add( file );
        _fileToData.put( file, new Data( (BuildOutputConsumerImpl)outputConsumer, target ) );
        return !context.getCancelStatus().isCanceled();
      } );


      if( !changedFiles.isEmpty() )
      {
        _tempMainClasses = makeTempMainClasses( context, target );
        if( !_tempMainClasses.isEmpty() )
        {
          IjIncrementalCompileDriver driver = IjIncrementalCompileDriver.INSTANCES.get().get( _tempMainClasses.iterator().next().getAbsolutePath() );
          driver.getResourceFiles().addAll( changedFiles );
        }
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

  static class Data
  {
    BuildOutputConsumerImpl _oc;
    ResourcesTarget _target;

    public Data( BuildOutputConsumerImpl oc, ResourcesTarget target )
    {
      _oc = oc;
      _target = target;
    }
  }

  @Nullable
  private File useOutputFile( File file, ResourceRootDescriptor sourceRoot, File outputDir )
  {
    String relativePath = FileUtil.getRelativePath( sourceRoot.getRootFile(), file );
    if( relativePath != null && outputDir != null )
    {
      File copiedFile = new File( outputDir, relativePath );
      if( copiedFile.isFile() )
      {
        file = copiedFile;
      }
    }
    return file;
  }

  private void registerClasses()
  {
    if( IjIncrementalCompileDriver.INSTANCES.get().isEmpty() )
    {
      return;
    }

    Set<BuildOutputConsumerImpl> ocs = new LinkedHashSet<>();
    for( IjIncrementalCompileDriver instance: IjIncrementalCompileDriver.INSTANCES.get().values() )
    {
      Map<File, Set<String>> typesToFile = instance.getTypesToFile();
      for( Map.Entry<File, Set<String>> entry : typesToFile.entrySet() )
      {
        Set<String> types = entry.getValue();
        for( String fqn : types )
        {
          try
          {
            File resourceFile = entry.getKey();
            Data data = _fileToData.get( resourceFile );

            File classFile = findClassFile( fqn, data._target.getOutputDir() );
            if( classFile != null )
            {
              data._oc.registerOutputFile( classFile, Collections.singleton( resourceFile.getPath() ) );
              ocs.add( data._oc );
            }
          }
          catch( IOException e )
          {
            throw new RuntimeException( e );
          }
        }
      }
    }

    // Send FileGeneratedEvent for the changed class files (for hot swap debugging)
    ocs.forEach( BuildOutputConsumerImpl::fireFileGeneratedEvent );
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

    List<File> resourceRoots = getResourceRoots( context, target );
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
      sourceRoot.deleteOnExit(); // in case the compiler exits abnormally

      index++;
      File tempMainClass = new File( sourceRoot, manifold_temp_main_ + index + ".java" );
      tempMainClass.deleteOnExit(); // in case the compiler exits abnormally
      try
      {
        IjIncrementalCompileDriver driver = new IjIncrementalCompileDriver();
        //noinspection ResultOfMethodCallIgnored
        tempMainClass.createNewFile();
        FileWriter writer = new FileWriter( tempMainClass );
        writer.write(
          "//!! Temporary generated file to facilitate incremental compilation of Manifold resources\n" +
          "package _temp_;\n" +
          "\n" +
          "import manifold.api.type.IncrementalCompile;\n" +
          "\n" +
          addResourceRoots( resourceRoots ) +
          "@IncrementalCompile( driverClass = \"manifold.ij.jps.IjIncrementalCompileDriver\",\n" +
          "                     driverInstance = " + System.identityHashCode( driver ) + " )\n" +
          "public class " + manifold_temp_main_ + index + "\n" +
          "{\n" +
          "}\n"
        );
        writer.flush();
        writer.close();
        FSOperations.markDirty( context, CompilationRound.CURRENT, tempMainClass );
        if( tempMainClasses.isEmpty() )
        {
          Map<String, IjIncrementalCompileDriver> drivers = IjIncrementalCompileDriver.INSTANCES.get();
          if( drivers == null )
          {
            IjIncrementalCompileDriver.INSTANCES.set( drivers = new HashMap<>() );
          }
          drivers.put( tempMainClass.getAbsolutePath(), driver );
        }
        tempMainClasses.add( tempMainClass );
      }
      catch( IOException e )
      {
        throw new RuntimeException( "Failed to create Manifold 'main' class: " + tempMainClass.getAbsolutePath(), e );
      }
    }
    return tempMainClasses;
  }

  private String addResourceRoots( List<File> resourceRoots )
  {
    if( resourceRoots.isEmpty() )
    {
      return "";
    }

    StringBuilder sb = new StringBuilder();
    for( File file: resourceRoots )
    {
      if( sb.length() > 0 )
      {
        sb.append( File.pathSeparator );
      }
      sb.append( file.getAbsolutePath() );
    }
    sb.insert( 0, "//## ResourceRoots: " );
    sb.append( "\n" );
    return sb.toString();
  }

  private List<File> getResourceRoots( CompileContext context, ResourcesTarget target )
  {
    List<File> resourceRoots = new ArrayList<>();
    for( JpsModuleSourceRoot jpsSourceRoot : target.getModule().getSourceRoots() )
    {
      if( !(jpsSourceRoot instanceof JpsTypedElement) || !(((JpsTypedElement)jpsSourceRoot).getType() instanceof JavaResourceRootType) )
      {
        continue;
      }

      File resourceRoot = jpsSourceRoot.getFile();
      if( resourceRoot.isDirectory() )
      {
        resourceRoots.add( resourceRoot );
      }
    }
    return resourceRoots;
  }
}
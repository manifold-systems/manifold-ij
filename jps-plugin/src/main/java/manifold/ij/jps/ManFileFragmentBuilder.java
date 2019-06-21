package manifold.ij.jps;

import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.incremental.BuilderCategory;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.incremental.ModuleLevelBuilder;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.messages.ProgressMessage;

/**
 * Registers fragment .class files to correspond with enclosing .java file so that .class files are cleaned between
 * builds.
 */
public class ManFileFragmentBuilder extends ModuleLevelBuilder
{
  private Map<File, Data> _fileToData;

  ManFileFragmentBuilder()
  {
    super( BuilderCategory.CLASS_POST_PROCESSOR );
  }

  @Override
  public ExitCode build( CompileContext context, ModuleChunk chunk, DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> holder, OutputConsumer outputConsumer ) throws ProjectBuildException, IOException
  {
    if( !holder.hasDirtyFiles() )
    {
      return ExitCode.NOTHING_DONE;
    }

    try
    {
      Map<JavaSourceRootDescriptor, Boolean> skippedRoots = new HashMap<>();
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

        // Both incremental build and rebuild need this mapping, see registerClasses()
        _fileToData.put( file, new Data( outputConsumer, chunk.representativeTarget() ) );

        return !context.getCancelStatus().isCanceled();
      } );

      context.checkCanceled();

      context.processMessage( new ProgressMessage( "" ) );
    }
    catch( BuildDataCorruptedException | ProjectBuildException e )
    {
      throw e;
    }
    catch( Exception e )
    {
      throw new ProjectBuildException( e.getMessage(), e );
    }
    return ExitCode.OK;
  }

  @NotNull
  @Override
  public String getPresentableName()
  {
    return "File Fragment Builder";
  }

  @Override
  public void buildStarted( CompileContext context )
  {
    super.buildStarted( context );
    _fileToData = new ConcurrentHashMap<>();
  }

  public void buildFinished( CompileContext context )
  {
    registerClasses();
    IjFileFragmentIncrementalCompileDriver.removeInstance();
  }

  static class Data
  {
    OutputConsumer _oc;
    ModuleBuildTarget _target;

    Data( OutputConsumer oc, ModuleBuildTarget target )
    {
      _oc = oc;
      _target = target;
    }
  }

  private void registerClasses()
  {
    Map<File, Set<String>> typesToFile = IjFileFragmentIncrementalCompileDriver.getInstance().getTypesToFile();
    for( Map.Entry<File, Set<String>> entry: typesToFile.entrySet() )
    {
      Set<String> types = entry.getValue();
      for( String fqn: types )
      {
        try
        {
          File sourceFile = entry.getKey();
          Data data = _fileToData.get( sourceFile );
          if( data != null )
          {
            File classFile = findClassFile( fqn, data._target.getOutputDir() );
            if( classFile != null )
            {
              data._oc.registerOutputFile( data._target, classFile, Collections.singleton( sourceFile.getPath() ) );
            }
          }
        }
        catch( IOException e )
        {
          throw new RuntimeException( e );
        }
      }
    }

//## todo: is this necessary (for file fragments?)
//    // Send FileGeneratedEvent for the changed class files (for hot swap debugging)
//    ocs.forEach( oc -> ReflectUtil.method( oc, "fireFileGeneratedEvent" ) );
  }

  private File findClassFile( String fqn, File outputPath )
  {
    String rootRelativeClassFile = fqn.replace( '.', File.separatorChar ) + ".class";
    File classFile = new File( outputPath, rootRelativeClassFile );
    return classFile.isFile() ? classFile : null;
  }
}

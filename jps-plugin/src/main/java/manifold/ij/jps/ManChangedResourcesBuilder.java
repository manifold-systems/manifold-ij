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

package manifold.ij.jps;

import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.impl.BuildOutputConsumerImpl;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.java.ResourceRootDescriptor;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.incremental.BuildListener;
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
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleDependency;
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
public class ManChangedResourcesBuilder extends ResourcesBuilder implements BuildListener
{
  private List<File> _tempMainClasses;
  private Map<File, Data> _fileToData;
  private Map<String, BuildOutputConsumerImpl> _outputDirToOc;

  @Override
  public void buildStarted( CompileContext context )
  {
    super.buildStarted( context );
    _tempMainClasses = new ArrayList<>();
    _fileToData = new ConcurrentHashMap<>();
    _outputDirToOc = new HashMap<>();
  }

  public void buildFinished( CompileContext context )
  {
    if( !_tempMainClasses.isEmpty() )
    {
      deleteTempMainSourceClasses( context );
    }

    registerClasses();

    IjResourceIncrementalCompileDriver.INSTANCES.set( null );

    //!! Do not clear changed resource files list, it is needed in ManFileFragmentBuilder (java sources must be mapped
    // in a ModuleLevelBuilder, not this ResourceBuilder)
    // IjChangedResourceFiles.clear();
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
    boolean incremental = JavaBuilderUtil.isCompileJavaIncrementally( context );
    System.setProperty( "manifold.compiler.incremental", String.valueOf( incremental ) );

    File targetOutputDir = target.getOutputDir();
    if( targetOutputDir == null )
    {
      return;
    }

    addOutputConsumer( (BuildOutputConsumerImpl)outputConsumer, targetOutputDir );

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

        if( incremental )
        {
          // Incremental compilation -- add the resource file for Manifold to compile, otherwise
          // if it is not referenced by a Java file included in the build, it will not be be recompiled.
          changedFiles.add( file );
        }

        // Both incremental build and rebuild need this mapping, see registerClasses()
        _fileToData.put( file, new Data( (BuildOutputConsumerImpl)outputConsumer, target ) );
        
        return !context.getCancelStatus().isCanceled();
      } );

      //
      // With an *incremental build* this JPS plugin tells Manifold about changed resource files via
      // IjChangedResourceFiles#getChangedFiles().
      //
      // Note regardless of incremental or full build, Manifold tells this JPS plugin about all resource files it
      // compiles via IjChangedResourceFiles#getTypesToFile(), which this JPS plugin uses in registerClasses() to map
      // .class files corresponding with a resource file.
      //
      // Note also ManFileFragmentBuilder also uses IjChangedResourceFiles#getTypesToFile() in its registerClasses()
      // to map .class files corresponding with fragments within a .java source file. This is necessary so that JSP can
      // delete the .class files on subsequent incremental build to enable recompilation of the fragments.
      //

      if( incremental && !changedFiles.isEmpty() )
      {
        List<File> tempMainClasses = makeTempMainClasses( context, target );
        if( !tempMainClasses.isEmpty() )
        {
          IjResourceIncrementalCompileDriver driver = getDrivers().get( tempMainClasses.iterator().next().getAbsolutePath() );
          driver.getChangedFiles().addAll( changedFiles );
          IjChangedResourceFiles.getChangedFiles().addAll( changedFiles ); // aggregated list
        }
        _tempMainClasses.addAll( tempMainClasses );
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

  private void addOutputConsumer( BuildOutputConsumerImpl outputConsumer, File outputDir )
  {
    _outputDirToOc.put( outputDir.getAbsolutePath(), outputConsumer );
  }

  static class Data
  {
    BuildOutputConsumerImpl _oc;
    ResourcesTarget _target;

    Data( BuildOutputConsumerImpl oc, ResourcesTarget target )
    {
      _oc = oc;
      _target = target;
    }
  }

  private void registerClasses()
  {
    Set<BuildOutputConsumerImpl> ocs = new LinkedHashSet<>();
    Map<File, Set<String>> typesToFile = IjChangedResourceFiles.getTypesToFile();
    for( Map.Entry<File, Set<String>> entry : typesToFile.entrySet() )
    {
      Set<String> types = entry.getValue();
      for( String fqn : types )
      {
        try
        {
          File resourceFile = entry.getKey();
          Data data = _fileToData.get( resourceFile );
          if( data != null )
          {
            File classFile = findClassFile( fqn, data._target.getOutputDir() );
            if( classFile != null )
            {
              data._oc.registerOutputFile( classFile, Collections.singleton( resourceFile.getPath() ) );
              ocs.add( data._oc );
            }
          }
        }
        catch( IOException e )
        {
          throw new RuntimeException( e );
        }
      }
    }

    // Send FileGeneratedEvent for the changed class files (for hot swap debugging)
    ocs.forEach( oc -> oc.fireFileGeneratedEvent() );
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
      if( !(jpsSourceRoot instanceof JpsTypedElement) ||
          !(((JpsTypedElement)jpsSourceRoot).getType() instanceof JavaSourceRootType) ||
          !hasManifoldDependency( target.getModule() ) )
      {
        continue;
      }

      // The source root dir may not be there, ensure it is before we make the '_temp_' dir e.g.,
      //   'target/generated-sources/annotations'
      if( !jpsSourceRoot.getFile().mkdirs() )
      {
        if( !jpsSourceRoot.getFile().exists() )
        {
          continue;
        }
      }

      // generate file in '_temp_' package, Java 9 modular projects do not support the default/empty package
      File sourceRoot = new File( jpsSourceRoot.getFile(), "_temp_" );
      //noinspection ResultOfMethodCallIgnored
      sourceRoot.mkdir();
      sourceRoot.deleteOnExit(); // in case the compiler exits abnormally

      index++;
      File tempMainClass = new File( sourceRoot, manifold_temp_main_ + index + ".java" );
      if( tempMainClass.isFile() )
      {
        // already there
        continue;
      }

      tempMainClass.deleteOnExit(); // in case the compiler exits abnormally
      try
      {
        IjResourceIncrementalCompileDriver driver = new IjResourceIncrementalCompileDriver( context );
        //noinspection ResultOfMethodCallIgnored
        tempMainClass.createNewFile();
        FileWriter writer = new FileWriter( tempMainClass );
        writer.write(
          "//!! Temporary generated file to facilitate incremental compilation of Manifold resources\n" +
          "package _temp_;\n" +
          "\n" +
          "import manifold.rt.api.IncrementalCompile;\n" +
          "\n" +
          addResourceRoots( resourceRoots ) +
          "@IncrementalCompile( driverClass = \"manifold.ij.jps.IjResourceIncrementalCompileDriver\",\n" +
          "                     driverInstance = " + System.identityHashCode( driver ) + " )\n" +
          "public class " + manifold_temp_main_ + index + "\n" +
          "{\n" +
          "}\n"
        );
        writer.flush();
        writer.close();
        FSOperations.markDirty( context, CompilationRound.CURRENT, tempMainClass );

        Map<String, IjResourceIncrementalCompileDriver> drivers = getDrivers();
        drivers.put( tempMainClass.getAbsolutePath(), driver );

        tempMainClasses.add( tempMainClass );
      }
      catch( IOException e )
      {
        throw new RuntimeException( "Failed to create Manifold 'main' class: " + tempMainClass.getAbsolutePath(), e );
      }
    }
    return tempMainClasses;
  }

  private boolean hasManifoldDependency( JpsModule module )
  {
    return _hasManifoldDependency( module, new HashSet<>() );
  }
  private boolean _hasManifoldDependency( JpsModule module, Set<JpsModule> visited )
  {
    if( visited.contains( module ) )
    {
      return false;
    }
    visited.add( module );

    if( module.getDependenciesList().getDependencies().stream()
        .anyMatch( e -> // must be at least manifold.jar, not utilities etc.
          e.toString().contains( "manifold-" ) &&
          !e.toString().contains( "manifold-util" ) &&
          !e.toString().contains( "manifold-bootstrap" ) ) )
    {
      return true;
    }

    List<JpsLibrary> libraries = module.getLibraryCollection().getLibraries();
    for( JpsLibrary lib: libraries )
    {
      if( lib.getRoots( JpsOrderRootType.COMPILED ).stream()
          .anyMatch( e -> // must be at least manifold.jar, not utilities etc.
            e.getUrl().contains( "manifold-" ) &&
            !e.getUrl().contains( "manifold-util" ) &&
            !e.getUrl().contains( "manifold-bootstrap" ) ) )
      {
        return true;
      }
    }

    return module.getDependenciesList().getDependencies().stream()
           .anyMatch( dep -> dep instanceof JpsModuleDependency &&
                             _hasManifoldDependency( ((JpsModuleDependency)dep).getModule(), visited ) );
  }

  @NotNull
  private Map<String, IjResourceIncrementalCompileDriver> getDrivers()
  {
    Map<String, IjResourceIncrementalCompileDriver> drivers = IjResourceIncrementalCompileDriver.INSTANCES.get();
    if( drivers == null )
    {
      IjResourceIncrementalCompileDriver.INSTANCES.set( drivers = new ConcurrentHashMap<>() );
    }
    return drivers;
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

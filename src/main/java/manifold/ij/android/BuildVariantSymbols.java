package manifold.ij.android;

import com.intellij.openapi.vfs.VirtualFile;
import manifold.api.fs.IFile;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.extensions.ManDefinitions;
import manifold.preprocessor.api.SymbolProvider;
import manifold.preprocessor.definitions.Definitions;
import manifold.rt.api.util.StreamUtil;

import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BuildVariantSymbols implements SymbolProvider
{
  public static BuildVariantSymbols INSTANCE = null;

  private Map<ManModule, Map<String, String>> _buildConfigSyms;

  public BuildVariantSymbols()
  {
    INSTANCE = this;
  }

  public void reset()
  {
    _buildConfigSyms = null;
  }

  @Override
  public boolean isDefined( Definitions rootDef, IFile sourceFile, String def )
  {
    return loadBuildConfigSymbols( rootDef ).containsKey( def );
  }

  @Override
  public String getValue( Definitions rootDef, IFile sourceFile, String def )
  {
    return loadBuildConfigSymbols( rootDef ).get( def );
  }

  private Map<String, String> loadBuildConfigSymbols( Definitions rootDef )
  {
    ManModule module = ((ManDefinitions)rootDef).getModule();
    if( _buildConfigSyms == null )
    {
      _buildConfigSyms = new ConcurrentHashMap<>();
    }

    return _buildConfigSyms.computeIfAbsent( module, m -> {
      String generatedClassesDir = getBuildConfigSourcePath( rootDef );
      if( generatedClassesDir != null )
      {
        File dir = new File( generatedClassesDir );
        File buildConfig = findBuildConfig( dir );
        if( buildConfig != null )
        {
          return parseFields( buildConfig );
        }
      }
      return Collections.emptyMap();
    } );
  }

  private Map<String, String> parseFields( File buildConfig )
  {
    Map<String, String> map = new HashMap<>();
    FileReader fileReader = new FileReader( buildConfig );
    String content = StreamUtil.getContent( fileReader );
    String p_s_f = "public static final";
    for( int psf = content.indexOf( p_s_f ); psf > 0; psf = content.indexOf( p_s_f, psf + p_s_f.length() ) )
    {
      int iEq = content.indexOf( '=', psf + p_s_f.length() );
      if( iEq > 0 )
      {
        String name = getVarName( content, iEq );
        String init = getInitializer( content, iEq );

        String value = null;
        if( init.startsWith( "\"" ) )
        {
          value = init.substring( 1, init.length()-1 );
        }
        else
        {
          try
          {
            long l = Long.parseLong( init );
            value = init;
          }
          catch( Exception e )
          {
            try
            {
              double d = Double.parseDouble( init );
              value = init;
            }
            catch( Exception e2 )
            {
              // hack to handle DEBUG init, where for some reason initializer is: Boolean.parseBooean("true")
              if( init.contains( "true" ) )
              {
                // preprocessor definition will be just defined, a "false" value will not be defined
                value = "";
              }
            }
          }
        }
        if( value != null )
        {
          map.put( name, value );
        }
      }
    }
    return map;
  }

  private String getVarName( String content, int iEq )
  {
    StringBuilder sb = new StringBuilder();
    for( int i = iEq-1; i > 0; i-- )
    {
      char c = content.charAt( i );
      if( !Character.isWhitespace( c ) )
      {
        sb.insert( 0, c );
      }
      else if( sb.length() > 0 )
      {
        break;
      }
    }
    return sb.toString();
  }

  private String getInitializer( String content, int iEq )
  {
    int i = iEq + 1;
    char c = content.charAt( i );
    while( Character.isWhitespace( c ) )
    {
      c = content.charAt( ++i );
    }

    StringBuilder sb = new StringBuilder();
    for( ; i < content.length(); i++ )
    {
      c = content.charAt( i );
      if( c != '\r' && c != '\n' )
      {
        sb.append( c );
      }
      else if( sb.length() > 0 )
      {
        while( sb.charAt( sb.length()-1 ) == ';' )
        {
          sb.deleteCharAt( sb.length()-1 );
        }
        break;
      }
    }
    return sb.toString();
  }

//  private Map<String, String> extractBuildConfigSymbols( ManModule m, File buildConfig )
//  {
//    Map<String, String> map = new HashMap<>();
//    try
//    {
//      FileReader fileReader = new FileReader( buildConfig );
////      ArrayList<CompilationUnitTree> trees = new ArrayList<>();
//
//      ArrayList<JavaFileObject> javaStringObjects = new ArrayList<>();
//      javaStringObjects.add( new StringJavaFileObject( "sample", StreamUtil.getContent( fileReader ) ) );
//      StringWriter errors = new StringWriter();
//
//      JavacTask task = JavacTool.create().getTask( errors, null, null, null, null, javaStringObjects );
//      Iterable<? extends CompilationUnitTree> compUnitTrees = task.parse();
////      m.getProject().getHost().getJavaParser()
////        .parseText( StreamUtil.getContent( fileReader ), trees, null, null, null );
//      Iterator<? extends CompilationUnitTree> iter = compUnitTrees.iterator();
//      if( iter.hasNext() )
//      {
//        CompilationUnitTree tree = iter.next();
//        List<? extends Tree> typeDecls = tree.getTypeDecls();
//        if( typeDecls != null && !typeDecls.isEmpty() )
//        {
//          Tree cls = typeDecls.get( 0 );
//          if( cls instanceof JCTree.JCClassDecl )
//          {
//            com.sun.tools.javac.util.List<JCTree> defs = ((JCTree.JCClassDecl)cls).defs;
//            if( !defs.isEmpty() )
//            {
//              for( JCTree def: defs )
//              {
//                if( def instanceof JCTree.JCVariableDecl )
//                {
//                  processConstant( map, (JCTree.JCVariableDecl)def );
//                }
//              }
//            }
//          }
//        }
//      }
//    }
//    catch( IOException e )
//    {
//      throw ManExceptionUtil.unchecked( e );
//    }
//    return map;
//  }
//
//  private void processConstant( Map<String, String> map, JCTree.JCVariableDecl def )
//  {
//    JCTree.JCModifiers modifiers = def.getModifiers();
//    long mods = modifiers == null ? 0 : modifiers.flags;
//    int psf = Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL;
//    if( (mods & psf) == psf )
//    {
//      JCTree.JCExpression initializer = def.getInitializer();
//      if( initializer != null )
//      {
//        String value = null;
//        String init = initializer.toString();
//        if( init.startsWith( "\"" ) )
//        {
//          value = init.substring( 1, init.length()-1 );
//        }
//        else
//        {
//          try
//          {
//            long l = Long.parseLong( init );
//            value = init;
//          }
//          catch( Exception e )
//          {
//            try
//            {
//              double d = Double.parseDouble( init );
//              value = init;
//            }
//            catch( Exception e2 )
//            {
//              // hack to handle DEBUG init, which can be like: Boolean.parseBooean("true")
//              if( init.contains( "true" ) )
//              {
//                // preprocessor definition will be just defined, a "false" value will not be defined
//                value = "";
//              }
//            }
//          }
//        }
//        if( value != null )
//        {
//          map.put( def.getName().toString(), value );
//        }
//      }
//    }
//  }

  private String getBuildConfigSourcePath( Definitions rootDef )
  {
    ManDefinitions manDefs = (ManDefinitions)rootDef;
    ManModule module = manDefs.getModule();

    String generatedClassesDir = null;
    for( VirtualFile vf: ManProject.getSourceRoots( module.getIjModule() ) )
    {
      String path = vf.getPath();
      if( path.contains( "/app/build/generated/source/buildConfig/" ) )
      {
        generatedClassesDir = path.replace( '/', File.separatorChar );
        break;
      }
    }
    return generatedClassesDir;
  }

  private File findBuildConfig( File file )
  {
    if( file.isFile() )
    {
      if( file.getName().equals( "BuildConfig.java" ) )
      {
        return file;
      }
      return null;
    }
    else
    {
      File[] listing = file.listFiles();
      if( listing != null )
      {
        for( File f : listing )
        {
          File buildConfig = findBuildConfig( f );
          if( buildConfig != null )
          {
            return buildConfig;
          }
        }
      }
    }
    return null;
  }

  //  private Map<String, String> loadBuildConfigSymbols( Definitions rootDef )
//  {
////    if( _buildConfigSyms != null )
////    {
////      return _buildConfigSyms;
////    }
//
//    ManDefinitions manDefs = (ManDefinitions)rootDef;
//    ManModule module = manDefs.getModule();
//    Project project = module.getIjModule().getProject();
//
//    if( DumbService.getInstance( project ).isDumb() )
//    {
//      // skip processing during index rebuild
//      return Collections.emptyMap();
//    }
//
//    PsiShortNamesCache shortNamesCache = PsiShortNamesCache.getInstance( project );
//    @NotNull PsiClass[] buildConfigs = shortNamesCache.getClassesByName( "BuildConfig", GlobalSearchScope.projectScope( project ) );
//    if( buildConfigs.length > 0 )
//    {
//      return _buildConfigSyms = extractBuildConfigSymbols( buildConfigs[0] );
//    }
//    return Collections.emptyMap();
//  }
//
//  private Map<String, String> extractBuildConfigSymbols( @NotNull PsiClass buildConfig )
//  {
//    Map<String, String> map = new HashMap<>();
//    PsiField @NotNull [] fields = buildConfig.getFields();
//    for( PsiField field: fields )
//    {
//      processConstant( map, field );
//    }
//    return map;
//  }
//
//  private void processConstant( Map<String, String> map, PsiField field )
//  {
//    if( field.hasModifier( JvmModifier.PUBLIC ) &&
//      field.hasModifier( JvmModifier.STATIC ) &&
//      field.hasModifier( JvmModifier.FINAL ) )
//    {
//      @Nullable PsiExpression initializer = field.getInitializer();
//      if( initializer != null )
//      {
//        String value = null;
//        String init = initializer.getText();
//        if( init.startsWith( "\"" ) )
//        {
//          value = init.substring( 1, init.length()-1 );
//        }
//        else
//        {
//          try
//          {
//            long l = Long.parseLong( init );
//            value = init;
//          }
//          catch( Exception e )
//          {
//            try
//            {
//              double d = Double.parseDouble( init );
//              value = init;
//            }
//            catch( Exception e2 )
//            {
//              // hack to handle DEBUG init, which can be like: Boolean.parseBooean("true")
//              if( init.contains( "true" ) )
//              {
//                // preprocessor definition will be just defined, a "false" value will not be defined
//                value = "";
//              }
//            }
//          }
//        }
//        if( value != null )
//        {
//          map.put( field.getName().toString(), value );
//        }
//      }
//    }
//  }
}
package json.parse;

import json.sample.Junk;
import json.sample.Outside;

import java.util.Collections;
import java.util.List;

/**
 * no errors or wanrings here
 */
public class TestJsonParsing_1
{
  void testComplicatedReferences()
  {
    Junk junk = Junk.create();
    junk.setElem( Collections.singletonList( Junk.A.create() ) );
    Junk.A a = junk.getElem().get( 0 );
    System.out.println( a );

    Junk.B b = Junk.B.create();
    b.setX( Junk.A.create() );
    b.getX().setFoo( "hi" );
    junk.setDing( Collections.singletonList( b ) );
    System.out.println( junk.getDing() );
    junk.getDing().get( 0 ).getX().getFoo().<caret>;

    Outside.Alpha alpha = Outside.Alpha.create();
    Outside outside = Outside.create();
    outside.setGamma( Collections.singletonList( alpha ) );
    junk.setOutside( outside );
    Outside result = junk.getOutside();
    List<Outside.Alpha> gamma = result.getGamma();
    System.out.println( gamma );
  }
}
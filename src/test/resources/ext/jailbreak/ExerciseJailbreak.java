package ext.jailbreak;

import java.lang.reflect.Array;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import ext.jailbreak.stuff.Leaf;
import ext.jailbreak.stuff.Sample;
import manifold.ext.rt.api.Jailbreak;
import manifold.util.ReflectUtil;

public class ExerciseJailbreak extends ext.jailbreak.stuff.Base
{
  public void testJailbreakMethod()
  {
    ext.jailbreak.stuff.@Jailbreak SecretParam secretParam =
      new ext.jailbreak.stuff.@Jailbreak SecretParam();
    secretParam._foo = 9;

    ext.jailbreak.stuff.@Jailbreak SecretClass secret =
      new ext.jailbreak.stuff.@Jailbreak SecretClass( secretParam );

    int foo = secret.getParam().jailbreak()._foo;
    assertEquals( 9, foo );
    secret.getParam().jailbreak()._foo = 10;
    assertEquals( 10, secret.getParam().jailbreak()._foo );

    assertEquals( getName(), jailbreak().fName );
  }

  public void testAccessPrivateMembersDeclaredInSupers()
  {
    @Jailbreak Leaf leaf = new Leaf();
    leaf.foo();
    assertEquals( 9, leaf.foo(9) );
    assertEquals( 9d, leaf.foo(9d) );

    assertEquals( 8, leaf.foo(leaf.foo(8)) );
    assertEquals( 7, leaf.foo(leaf.foo(leaf.foo(7))) );
    assertEquals( 6, leaf.foo(leaf.foo(leaf.foo(6)), this.toString()) );
    assertEquals( 5, leaf.foo(this.toString(), leaf.foo(leaf.foo(5))) );

//    leaf.foo(leaf.foo(leaf.foo(8)), false);
//    leaf.foo(leaf.foo());
//
//    leaf.foo("err");
//    leaf.foooo();
//
//    Leaf leaf2 = new Leaf();
//    leaf2.foo();
//    leaf2.foo(9);
//    leaf2.foo(9.0d);
//
//    leaf2.foo(leaf2.foo(8));
//    leaf2.foo(leaf2.foo());
//
//    leaf2.foo("err");
//    leaf2.foooo();
  }

  public void testType()
  {
    @Jailbreak AbstractStringBuilder sb = new @Jailbreak StringBuilder();
    sb.append( 8 );

    ext.jailbreak.stuff.@Jailbreak SecretParam secretParam =
      new ext.jailbreak.stuff.@Jailbreak SecretParam();
    secretParam._foo = 9;
    ext.jailbreak.stuff.@Jailbreak SecretClass secret =
      new ext.jailbreak.stuff.@Jailbreak SecretClass( secretParam );
    secretParam = secret.getParam();
    assertEquals( 9, secretParam._foo );
  }

  public void testJailbreak()
  {
    // instance method
    @Jailbreak LocalTime time = LocalTime.now();
    time.writeReplace();

    // static method
    @Jailbreak LocalTime staticTime = null;
    LocalTime localTime = staticTime.create( 7, 59, 30, 99 );
    assertEquals( localTime.withHour( 7 ).withMinute( 59 ).withSecond( 30 ).withNano( 99 ), localTime );

    // instance field
    @Jailbreak LocalTime lt = null;
    lt = LocalTime.of( 11, 12 );
    assertEquals( 11, lt.hour );
    lt.hour = 12;
    assertEquals( 12, lt.hour );

    // static field
    int hoursPerDay = staticTime.HOURS_PER_DAY;
    assertEquals( ReflectUtil.field( LocalTime.class, "HOURS_PER_DAY" ).getStatic(), hoursPerDay );
    staticTime.HOURS_PER_DAY = hoursPerDay + 1;
    assertEquals( ReflectUtil.field( LocalTime.class, "HOURS_PER_DAY" ).getStatic(), hoursPerDay + 1 );

    // Test a class that is extended
    @Jailbreak ArrayList<String> list = new ArrayList<>();
    list.ensureCapacityInternal( 100 );
  }

  public void testAllTypesAssignField()
  {
    @Jailbreak Sample s = new Sample();
    s._booleanField = true;
    assertTrue( s._booleanField );
    s._charField = 'a';
    assertEquals( 'a', s._charField );
    s._byteField = Byte.MAX_VALUE;
    assertEquals( Byte.MAX_VALUE, s._byteField );
    s._shortField = Short.MAX_VALUE;
    assertEquals( Short.MAX_VALUE, s._shortField );
    s._intField = Integer.MAX_VALUE;
    assertEquals( Integer.MAX_VALUE, s._intField );
    s._longField = Long.MAX_VALUE;
    assertEquals( Long.MAX_VALUE, s._longField );
    s._floatField = Float.MAX_VALUE;
    assertEquals( Float.MAX_VALUE, s._floatField );
    s._doubleField = Double.MAX_VALUE;
    assertEquals( Double.MAX_VALUE, s._doubleField );
    s._stringField = "hello";
    assertEquals( "hello", s._stringField );
  }

  public void testAllTypesAssignFieldAsExpr()
  {
    @Jailbreak Sample s = new Sample();
    boolean b = s._booleanField = true;
    assertTrue( b );
    char c = s._charField = 'a';
    assertEquals( 'a', c );
    byte bt = s._byteField = Byte.MAX_VALUE;
    assertEquals( Byte.MAX_VALUE, bt );
    short sh = s._shortField = Short.MAX_VALUE;
    assertEquals( Short.MAX_VALUE, sh );
    int i = s._intField = Integer.MAX_VALUE;
    assertEquals( Integer.MAX_VALUE, i );
    long l = s._longField = Long.MAX_VALUE;
    assertEquals( Long.MAX_VALUE, l );
    float f = s._floatField = Float.MAX_VALUE;
    assertEquals( Float.MAX_VALUE, f );
    double d = s._doubleField = Double.MAX_VALUE;
    assertEquals( Double.MAX_VALUE, d );
    String str = s._stringField = "hello";
    assertEquals( "hello", str );
  }

  public void testUnaryExpr()
  {
    @Jailbreak Sample s = new Sample();
    s._intField = 8;
    assertEquals( -8, -s._intField );
    assertEquals( 8, +s._intField );
    assertEquals( ~8, ~s._intField );

    Sample ss = new Sample();
    ss.jailbreak()._intField = 9;
    assertEquals( -9, -ss.jailbreak()._intField );
    assertEquals( 9, ss.jailbreak()._intField );
    assertEquals( ~9, ~ss.jailbreak()._intField );

    s._booleanField = true;
    assertFalse( !s._booleanField );
  }

  private void assertEquals( Object o1, Object o2 )
  {
  }
  private void assertFalse( boolean condition )
  {
  }
  private void assertTrue( boolean condition )
  {
  }
}
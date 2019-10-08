package science;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import manifold.ext.api.ComparableUsing;
import manifold.science.Length;
import manifold.science.util.Rational;


import static manifold.science.util.UnitConstants.m;
import static manifold.science.util.CoercionConstants.*;
import static manifold.collections.api.range.RangeFun.*;


// !! Note: This file is tested in IJ to haveno compile errors, it is not executed (it is copied from the manifold lib)

public class ExerciseBindingExpressions
{
  public void testBigDecimal()
  {
    BigDecimal bd1 = 1.2bd;
    BigDecimal bd2 = 2.4bd;

    assertTrue( new BigDecimal( "1.2" ) == bd1 );
    assertTrue( new BigDecimal( "2.4" ) == bd2 );

    assertTrue( new BigDecimal( "-1.2" ) == -bd1 );
    assertTrue( new BigDecimal( "3.6" ) == bd1 + bd2 );
    assertTrue( new BigDecimal( "-1.2" ) == bd1 - bd2 );
    assertTrue( new BigDecimal( "2.88" ) == bd1 * bd2 );
    assertTrue( new BigDecimal( "0.5" ) == bd1 / bd2 );
    assertTrue( new BigDecimal( "1.2" ) == bd1 % bd2 );
  }

  public void testFuzz()
  {
    Fuzz bar = new Fuzz(3.0);
    Fuzz baz = new Fuzz(12.0);
    Fuzz boz = new Fuzz(5.0);

    assertTrue( new Fuzz(15.0) == bar + baz );
    assertTrue( new Fuzz(-9.0) == bar - baz );
    assertTrue( new Fuzz(36.0) == bar * baz );
    assertTrue( new Fuzz(4.0) == baz / bar );
    assertTrue( new Fuzz(2.0) == baz % boz );
    assertTrue( new Fuzz(-3.0) == -bar );
    assertTrue( new Fuzz(-3.0) == -(bar) );

    assertFalse( bar == baz );
    assertTrue( bar != baz );
    assertFalse( bar > baz );
    assertFalse( bar >= baz );
    assertTrue( bar < baz );
    assertTrue( bar <= baz );
    assertFalse( -bar == bar );
    assertFalse( bar == -bar );
    assertTrue( -bar != bar );
    assertTrue( bar != -bar );
    assertTrue( -bar < bar );
    assertTrue( bar > -bar );
    assertTrue( -bar <= bar );
    assertTrue( bar >= -bar );
  }

  static class Fuzz implements ComparableUsing<Fuzz>
  {
    final double _value;

    public Fuzz( double value )
    {
      _value = value;
    }

    public Fuzz unaryMinus()
    {
      return new Fuzz( -_value );
    }

    public Fuzz plus( Fuzz op )
    {
      return new Fuzz( _value + op._value );
    }

    public Fuzz minus( Fuzz op )
    {
      return new Fuzz( _value - op._value );
    }

    public Fuzz times( Fuzz op )
    {
      return new Fuzz( _value * op._value);
    }

    public Fuzz div( Fuzz op )
    {
      return new Fuzz( _value / op._value);
    }

    public Fuzz rem( Fuzz op )
    {
      return new Fuzz( _value % op._value);
    }

    @Override
    public int compareTo( Fuzz o )
    {
      double diff = _value - o._value;
      return diff == 0 ? 0 : diff < 0 ? -1 : 1;
    }
  }

  public void testRange()
  {
    for( Length l : 5m to 10m )
    {
      System.out.println( l );
    }
    for( Rational rat : 5r to 20r/2 )
    {
      System.out.println( rat );
    }
    for( Rational rat : 5r to (10r) )
    {
      System.out.println( rat );
    }
  }

  public void testNegation()
  {
    Length l = -(5m);
    Length l2 = -5m;
    Length l3 = -5r m;
  }

  private void assertTrue( boolean b ) {}
  private void assertFalse( boolean b ) {}
}
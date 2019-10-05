package science;

import java.util.ArrayList;
import java.util.List;
import manifold.science.Length;
import manifold.science.util.Rational;

import static manifold.science.MetricScaleUnit.r;
import static manifold.science.util.UnitConstants.m;
import static manifold.collections.api.range.RangeFun.*;

public class ExerciseBindingExpressions
{
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
}
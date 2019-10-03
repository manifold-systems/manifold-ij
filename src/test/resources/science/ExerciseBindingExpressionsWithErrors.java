package science;

import java.util.ArrayList;
import java.util.List;
import manifold.science.Length;
import manifold.science.util.Rational;

import static manifold.science.MetricScaleUnit.r;
import static manifold.science.util.UnitConstants.m;
import static manifold.science.util.RangeConstants.*;

public class ExerciseBindingExpressionsWithErrors
{
  public void testRange()
  {
    for( Length l : 5m to 10 )
    {
      System.out.println( l );
    }
    for( Rational rat : 5r to 10 )
    {
      System.out.println( rat );
    }
  }
}
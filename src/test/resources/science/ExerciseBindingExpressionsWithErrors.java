package science;

import java.util.ArrayList;
import java.util.List;
import manifold.science.measures.Length;
import manifold.science.util.Rational;

import static manifold.science.util.CoercionConstants.r;
import static manifold.science.util.UnitConstants.m;
import static manifold.collections.api.range.RangeFun.*;

public class ExerciseBindingExpressionsWithErrors
{
  public void testRange()
  {
    for( Length l : 5m to 10 ) // error 10
    {
      System.out.println( l );
    }
    for( Rational rat : 5r to 10 ) // error 10
    {
      System.out.println( rat );
    }
  }
}
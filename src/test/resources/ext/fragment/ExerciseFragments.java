package ext.fragment;

public class ExerciseFragments
{
  public void testInComment()
  {
    /*[>MyProps.properties<]
    foo=bar
    hi=hello
    */
    String bar = MyProps.foo;
    String hello = MyProps.hi;
  }
}
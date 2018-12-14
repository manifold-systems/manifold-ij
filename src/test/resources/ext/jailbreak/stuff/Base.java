package ext.jailbreak.stuff;

public class Base
{
  private String fName = "foo";
  private void foo() {}
  private int foo(int i) { return i;}
  private double foo(double d) {return d;}
  private int foo(int i, String s) { return i; }
  private int foo(String s, int i) { return i; }
  protected String getName() { return fName; }
}

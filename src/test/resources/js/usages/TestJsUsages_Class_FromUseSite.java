package js.usages;

import js.sample.MyJsClass;

public class TestJsUsage_Class_FromUseSite {
  public static void main(String[] args) {
    <caret>MyJsClass js = new MyJsClass();
    js.hi();
    System.out.println( js.getYeah() );
    js.setFong( "hi" );
  }
}
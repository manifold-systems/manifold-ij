package js.usages;

import js.usages.MyJsClass_Caret_ClassDeclaration;

public class TestJsUsage_Class_FromDeclaration {
  public static void main(String[] args) {
    MyJsClass_Caret_ClassDeclaration js = new MyJsClass_Caret_ClassDeclaration();
    js.hi();
    String yeah = js.getYeah();
    js.setYeah( "yeah" );
  }
}
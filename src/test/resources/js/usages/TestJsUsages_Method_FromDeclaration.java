package js.usages;

import js.usages.MyJsClass_Caret_MethodDeclaration;

public class TestJsUsage_Method_FromDeclaration {
  public static void main(String[] args) {
    MyJsClass_Caret_MethodDeclaration js = new MyJsClass_Caret_MethodDeclaration();
    js.hi();
    String yeah = js.getYeah();
    js.setYeah( "yeah" );
  }
}
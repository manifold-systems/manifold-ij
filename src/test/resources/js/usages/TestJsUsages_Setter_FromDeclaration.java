package js.usages;

import js.usages.MyJsClass_Caret_SetterDeclaration;

public class TestJsUsage_Setter_FromDeclaration {
  public static void main(String[] args) {
    MyJsClass_Caret_SetterDeclaration js = new MyJsClass_Caret_SetterDeclaration();
    js.hi();
    String yeah = js.getYeah();
    js.setYeah( "yeah" );
  }
}
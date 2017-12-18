package js.usages;

import js.usages.MyJsClass_Caret_GetterDeclaration;

public class TestJsUsage_Getter_FromDeclaration {
  public static void main(String[] args) {
    MyJsClass_Caret_GetterDeclaration js = new MyJsClass_Caret_GetterDeclaration();
    js.hi();
    String yeah = js.getYeah();
    js.setYeah( "yeah" );
  }
}
package js.rename;

import js.sample.MyJsClass;

public class TestJsRename_Setter {
  public static void main(String[] args) {
    MyJsClass jsClass;
    jsClass.<caret>setYeah( "yeah" );
  }
}
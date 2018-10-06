package extensions.java.lang.String;

import manifold.ext.api.Extension;
import manifold.ext.api.This;
import java.lang.String;

@Extension
public class MyStringExt_Caret {
  public static void hiWorld(@This String thiz) {
    System.out.println("hello world!");
  }
}
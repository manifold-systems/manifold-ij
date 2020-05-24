package extensions.java.lang.String;

import manifold.ext.rt.api.Extension;
import manifold.ext.rt.api.This;
import java.lang.String;

@Extension
public class MyStringExt {
  public static void hiWorld(@This String thiz) {
    System.out.println("hello world!");
  }
}
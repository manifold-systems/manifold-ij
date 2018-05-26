package extensions.ext.sample.Foo;

import manifold.ext.api.Extension;
import manifold.ext.api.This;
import ext.sample.Foo;

@Extension
public class MyFooExt {
  public static void helloWorld(@This Foo thiz) {
    System.out.println("hello world!");
  }
}
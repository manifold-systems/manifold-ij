package extensions.ext.sample.Foo;

import manifold.ext.rt.api.Extension;
import manifold.ext.rt.api.This;
import ext.sample.Foo;

@Extension
public class MyFooExt {
  public static void helloWorld(@This Foo thiz) {
    System.out.println("hello world!");
  }
}
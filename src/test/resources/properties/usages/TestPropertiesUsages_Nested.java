package properties.usages;

public class TestPropertiesUsages_Nested {
  public static void main(String[] args) {
    Object bye = MyProperties_Caret_Nested.bye;
    String hello = MyProperties_Caret_Nested.hello;
    String good = MyProperties_Caret_Nested.bye.good;
  }
}
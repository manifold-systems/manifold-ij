package xml.usages;

import xml.sample.Stuff_Caret_PropertyDeclaration;

public class TestXmlUsages_Property {
  public static void main(String[] args) {
    <caret>Stuff_Caret_PropertyDeclaration myXml = Stuff_Caret_PropertyDeclaration.create();
    Stuff_Caret_PropertyDeclaration.stuff.things t = myXml.getStuff().getThings();
    t.setOne( "hi" ); // 1 usage
    String one = t.getOne(); // 1 usage
  }
}
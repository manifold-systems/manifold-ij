package xml.usages;

import xml.sample.Stuff; // <-- 1 usage here

public class TestXmlUsage_Class_FromUseSite {
  public static void main(String[] args) {
    <caret>Stuff myXml = Stuff.create(); // // <-- 2 usages here
    myXml.getStuff().setOne( "hi" );
  }
}
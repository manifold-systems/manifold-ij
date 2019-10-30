package xml.usages;

import xml.sample.Stuff;

public class TestXmlUsage_Setter_FromUseSite {
  public static void main(String[] args) {
    Stuff myXml = Stuff.create();
    myXml.getStuff().setOne( "hi" );
    myXml.<caret>setStuff( null ); // <--- 1 usage of setter here
  }
}
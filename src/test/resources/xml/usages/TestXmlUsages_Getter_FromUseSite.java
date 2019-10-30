package xml.usages;

import xml.sample.Stuff;

public class TestXmlUsage_Getter_FromUseSite {
  public static void main(String[] args) {
    Stuff myXml = Stuff.create();
    myXml.<caret>getStuff().setOne( "hi" ); // <--- 1 usage of getter here
    myXml.setStuff( null );
  }
}
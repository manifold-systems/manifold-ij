package xml.rename;

import xml.rename.Stuff_ElementProperty_Declaration;

public class TestXmlRename_ElementProperty_Declaration {
  public static void main(String[] args) {
    Stuff_ElementProperty_Declaration myXml = Stuff_ElementProperty_Declaration.create();
    Stuff_ElementProperty_Declaration.stuff.thingzzz t = myXml.getStuff().getThingzzz();
    myXml.getStuff().setThingzzz( null );
  }
}
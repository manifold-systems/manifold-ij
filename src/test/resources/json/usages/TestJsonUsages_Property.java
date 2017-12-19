package json.usages;

import json.sample.Person_Caret_PropertyDeclaration;

public class TestJsonUsages {
  public static void main(String[] args) {
    Person_Caret_PropertyDeclaration person = Person_Caret_PropertyDeclaration.create();
    person.getLastName();
    person.setLastName( "Smith" );
  }
}
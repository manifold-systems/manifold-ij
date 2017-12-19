package json.usages;

import json.sample.Person;

public class TestJsonUsage_Setter_FromUseSite {
  public static void main(String[] args) {
    Person person = Person.create();
    person.getLastName();
    person.<caret>setLastName( "Smith" );
  }
}
package json.usages;

import json.sample.Person;

public class TestJsonUsage_Getter_FromUseSite {
  public static void main(String[] args) {
    Person person = Person.create();
    person.<caret>getLastName();
    person.setLastName( "Smith" );
  }
}
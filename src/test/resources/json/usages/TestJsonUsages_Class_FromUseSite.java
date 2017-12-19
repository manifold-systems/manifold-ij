package json.usages;

import json.sample.Person;

public class TestJsonUsage_Class_FromUseSite {
  public static void main(String[] args) {
    <caret>Person person = Person.create();
    person.getLastName();
    person.setLastName( "Smith" );
  }
}
package json.completion;

import json.sample.Person;

public class TestJsonCompletion {
  public static void main(String[] args) {
    Person person = Person.create();
    person.<caret>;
  }
}
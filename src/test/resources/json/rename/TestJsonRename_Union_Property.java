package json.rename;

import json.sample.StrangeUriFormats;

public class TestJsonRename_Union_Property {
  public static void main(String[] args) {
    StrangeUriFormats json = StrangeUriFormats.create();
    json.getNc_Vehicle();
    json.getNc_VehicleAsnc_VehicleType();
    json.<caret>getNc_VehicleAsListOfnc_VehicleType();
    json.setNc_Vehicle( null );
    json.setNc_VehicleAsnc_VehicleType( null );
    json.setNc_VehicleAsListOfnc_VehicleType( null );
  }
}
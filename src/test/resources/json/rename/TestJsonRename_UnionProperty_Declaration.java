package json.rename;

import json.rename.StrangeUriFormats_UnionProperty_Declaration;

public class TestJsonRename_UnionProperty_Declaration {
  public static void main(String[] args) {
    StrangeUriFormats_UnionProperty_Declaration json = StrangeUriFormats_UnionProperty_Declaration.create();
    json.getNc_Vehicle();
    json.getNc_VehicleAsnc_VehicleType();
    json.getNc_VehicleAsListOfnc_VehicleType();
    json.setNc_Vehicle( null );
    json.setNc_VehicleAsnc_VehicleType( null );
    json.setNc_VehicleAsListOfnc_VehicleType( null );
  }
}
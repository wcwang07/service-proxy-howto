package io.vertx.howtos.ebservice.beers;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.howtos.ebservice.beers.Beer}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.howtos.ebservice.beers.Beer} original class using Vert.x codegen.
 */
public class BeerConverter {

  public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, Beer obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "name":
          if (member.getValue() instanceof String) {
            obj.setName((String)member.getValue());
          }
          break;
        case "style":
          if (member.getValue() instanceof String) {
            obj.setStyle((String)member.getValue());
          }
          break;
        case "price":
          if (member.getValue() instanceof Number) {
            obj.setPrice(((Number)member.getValue()).intValue());
          }
          break;
      }
    }
  }

  public static void toJson(Beer obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

  public static void toJson(Beer obj, java.util.Map<String, Object> json) {
    if (obj.getName() != null) {
      json.put("name", obj.getName());
    }
    if (obj.getStyle() != null) {
      json.put("style", obj.getStyle());
    }
    json.put("price", obj.getPrice());
  }
}

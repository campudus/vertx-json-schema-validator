package com.campudus.jsonvalidator

import io.vertx.busmod.ScalaBusMod
import org.vertx.scala.core.eventbus.Message
import scala.concurrent.Future
import org.vertx.scala.core.json._
import org.vertx.java.core.json.JsonObject
import com.github.fge.jsonschema.main.JsonSchema
import org.vertx.scala.platform.Verticle
import com.github.fge.jsonschema.util.JsonLoader

class SchemaValidatorBusMod(verticle: Verticle, schemas: Map[String, JsonSchema]) extends ScalaBusMod {
  import scala.collection.JavaConverters._

  override def asyncReceive(msg: Message[JsonObject]) = {
    case "validate" =>
      val reply = Option(msg.body.getString("key")) match {
        case Some(key) =>
          schemas.get(key) match {
            case Some(schema) =>
              Ok(Json.obj())
              Option(msg.body.getObject("json")) match {
                case Some(json) =>
                  val report = schema.validate(JsonLoader.fromString(json.encode()))
                  if (report.isSuccess()) {
                    Ok(Json.obj())
                  } else {
                    Error("Invalid JSON given: " + report.toString(),
                      Some("VALIDATION_ERROR"),
                      Some(Json.obj("report" ->
                        new JsonArray(report.asScala.map(_.asJson())
//                          case (key, value) => "\"" + key + "\":\"" + value + "\""
                        .mkString ("[", ",", "]")))))
                  }
                case None => Error("No JSON given!", Some("MISSING_JSON"))
              }
            case None => Error("No schema found!", Some("INVALID_SCHEMA_KEY"))
          }
        case None => Error("No key for schema given!", Some("MISSING_SCHEMA_KEY"))
      }

      Future.successful(reply)
  }
}
package com.campudus.jsonvalidator

import scala.concurrent.Future
import org.vertx.java.core.json.JsonObject
import org.vertx.scala.core.eventbus.Message
import org.vertx.scala.core.json._
import org.vertx.scala.platform.Verticle
import com.github.fge.jsonschema.main.JsonSchema
import io.vertx.busmod.ScalaBusMod
import com.github.fge.jsonschema.util.JsonLoader
import com.github.fge.jsonschema.main.JsonSchemaFactory

class SchemaValidatorBusMod(verticle: Verticle, var schemas: Map[String, JsonSchema]) extends ScalaBusMod {
  import scala.collection.JavaConverters._

  def getObjectOrArray(obj: JsonObject, key: String): Option[String] = {
    Option(obj.getField(key)) map { value: JsonElement =>
      if (value.isObject()) {
        value.asObject().encode()
      } else {
        value.asArray().encode()
      }
    }
  }

  override def asyncReceive(msg: Message[JsonObject]) = {
    case "validate" =>
      val reply = Option(msg.body.getString("key")) match {
        case Some(key) =>
          schemas.get(key) match {
            case Some(schema) =>
              try {
                getObjectOrArray(msg.body, "json") match {
                  case Some(json) =>
                    val report = schema.validate(JsonLoader.fromString(json))
                    if (report.isSuccess()) {
                      Ok(Json.obj())
                    } else {
                      Error("Invalid JSON given: " + report.toString(),
                        Some("VALIDATION_ERROR"),
                        Some(Json.obj("report" ->
                          new JsonArray(report.asScala.map(_.asJson()).mkString("[", ",", "]")))))
                    }
                  case None => Error("No JSON given!", Some("MISSING_JSON"))
                }
              } catch {
                case cce: ClassCastException => Error("Invalid JSON given!", Some("INVALID_JSON"))
              }
            case None => Error("No schema found!", Some("INVALID_SCHEMA_KEY"))
          }
        case None => Error("No key for schema given!", Some("MISSING_SCHEMA_KEY"))
      }
      Future.successful(reply)

    case "getSchemaKeys" =>
      val reply = Ok(Json.obj("schemas" -> Json.arr(schemas.keys.toSeq: _*)))
      Future.successful(reply)

    case "addSchema" =>
      val reply = Option(msg.body.getString("key")) match {
        case Some(key) =>
          try {
            val overwrite = msg.body.getBoolean("overwrite", false)
            if (schemas.contains(key) && !overwrite) {
              Error("Key exists and overwrite is not true", Some("EXISTING_SCHEMA_KEY"))
            } else {
              getObjectOrArray(msg.body, "json") match {
                case Some(json) =>
                  val factory = JsonSchemaFactory.byDefault()
                  val jsNode = JsonLoader.fromString(json)
                  if (!factory.getSyntaxValidator().schemaIsValid(jsNode)) {
                    Error("Schema is invalid: " + json, Some("INVALID_SCHEMA"))
                  } else {
                    schemas += (msg.body.getString("key") -> factory.getJsonSchema(jsNode))
                    Ok(Json.obj())
                  }
                case None => Error("No JSON given!", Some("MISSING_JSON"))
              }
            }
          } catch {
            case cce: ClassCastException => Error("Invalid OVERWRITE given!", Some("INVALID_OVERWRITE"))
          }
        case None => Error("No key for schema given!", Some("MISSING_SCHEMA_KEY"))
      }
      Future.successful(reply)
      
  }
}
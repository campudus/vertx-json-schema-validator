/*
 * Copyright 2013 Campudus
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.campudus.jsonvalidator

import org.vertx.scala.core.json._
import org.vertx.scala.platform.Verticle
import org.vertx.scala.mods.ScalaBusMod
import org.vertx.scala.mods.ScalaBusMod._
import org.vertx.scala.mods.replies.{Ok, Error}
import com.github.fge.jsonschema.main.JsonSchema
import com.github.fge.jsonschema.main.JsonSchemaFactory
import com.github.fge.jackson.JsonLoader

class SchemaValidatorBusMod(verticle: Verticle, var schemas: Map[String, JsonSchema]) extends ScalaBusMod {
  val container = verticle.container
  val vertx = verticle.vertx
  val logger = verticle.logger

  import scala.collection.JavaConverters._

  val schemaFactory = JsonSchemaFactory.byDefault()

  def getObjectOrArray(obj: JsonObject, key: String): Option[String] = {
    Option(obj.getField(key)) map {
      value: JsonElement =>
        if (value.isObject()) {
          value.asObject().encode()
        } else {
          value.asArray().encode()
        }
    }
  }

  def receive: Receive = msg => {
    case "validate" =>
      Option(msg.body.getString("key")) match {
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
                        "VALIDATION_ERROR",
                        Json.obj("report" ->
                          new JsonArray(report.asScala.map(_.asJson()).mkString("[", ",", "]"))))
                    }
                  case None => Error("No JSON given!", "MISSING_JSON")
                }
              } catch {
                case cce: ClassCastException => Error("Invalid JSON given!", "INVALID_JSON")
              }
            case None => Error("No schema found!", "INVALID_SCHEMA_KEY")
          }
        case None => Error("No key for schema given!", "MISSING_SCHEMA_KEY")
      }

    case "getSchemaKeys" =>
      Ok(Json.obj("schemas" -> Json.arr(schemas.keys.toSeq: _*)))

    case "addSchema" =>
      Option(msg.body.getString("key")) match {
        case Some(key) =>
          try {
            val overwrite = msg.body.getBoolean("overwrite", false)
            if (schemas.contains(key) && !overwrite) {
              Error("Key already exists and overwrite is not true", "EXISTING_SCHEMA_KEY")
            } else {
              getObjectOrArray(msg.body, "jsonSchema") match {
                case Some(jsonSchema) =>
                  val jsNode = JsonLoader.fromString(jsonSchema)
                  if (!schemaFactory.getSyntaxValidator().schemaIsValid(jsNode)) {
                    Error("Schema is invalid: " + jsonSchema, "INVALID_SCHEMA")
                  } else {
                    schemas += (msg.body.getString("key") -> schemaFactory.getJsonSchema(jsNode))
                    Ok(Json.obj())
                  }
                case None => Error("No JSON given!", "MISSING_JSON")
              }
            }
          } catch {
            case cce: ClassCastException => Error("Invalid OVERWRITE given!", "INVALID_OVERWRITE")
          }
        case None => Error("No key for schema given!", "MISSING_SCHEMA_KEY")
      }

  }
}
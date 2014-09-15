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

import com.github.fge.jackson.JsonLoader
import com.github.fge.jsonschema.core.exceptions.ProcessingException
import com.github.fge.jsonschema.core.load.configuration.LoadingConfigurationBuilder
import com.github.fge.jsonschema.main.JsonSchemaFactory
import org.vertx.scala.core.json._
import org.vertx.scala.mods.ScalaBusMod
import org.vertx.scala.mods.ScalaBusMod._
import org.vertx.scala.mods.replies.{Error, Ok}
import org.vertx.scala.platform.Verticle

class SchemaValidatorBusMod(verticle: Verticle, private var schemaKeys: Set[String], private var schemaFactory: JsonSchemaFactory, schemaUri: String, loadingCfg: LoadingConfigurationBuilder) extends ScalaBusMod {
  val container = verticle.container
  val vertx = verticle.vertx
  val logger = verticle.logger

  import scala.collection.JavaConverters._

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
          if (schemaKeys.contains(key)) {
            try {
              getObjectOrArray(msg.body, "json") match {
                case Some(json) =>
                  val schema = schemaFactory.getJsonSchema(s"$schemaUri$key")
                  try {
                    val report = schema.validate(JsonLoader.fromString(json))
                    if (report.isSuccess()) {
                      Ok(Json.obj())
                    } else {
                      Error("Invalid JSON given: " + report.toString(),
                        "VALIDATION_ERROR",
                        Json.obj("report" ->
                          new JsonArray(report.asScala.map(_.asJson()).mkString("[", ",", "]"))))
                    }
                  } catch {
                    case pe: ProcessingException => Error(pe.getMessage(), "VALIDATION_PROCESS_ERROR")
                  }
                case None => Error("No JSON given!", "MISSING_JSON")
              }

            } catch {
              case cce: ClassCastException => Error("Invalid JSON given!", "INVALID_JSON")
            }
          } else {
            Error("No schema found!", "INVALID_SCHEMA_KEY")
          }
        case None => Error("No key for schema given!", "MISSING_SCHEMA_KEY")
      }

    case "getSchemaKeys" =>
      Ok(Json.obj("schemas" -> Json.arr(schemaKeys.toSeq: _*)))

    case "addSchema" =>
      Option(msg.body.getString("key")) match {
        case Some(key) =>
          if (schemaKeys.contains(key)) {
            Error("A schema with this key already exists", "EXISTING_SCHEMA_KEY")
          } else {
            getObjectOrArray(msg.body, "jsonSchema") match {
              case Some(jsonSchema) =>
                val jsNode = JsonLoader.fromString(jsonSchema)
                if (!schemaFactory.getSyntaxValidator().schemaIsValid(jsNode)) {
                  Error("Schema is invalid: " + jsonSchema, "INVALID_SCHEMA")
                } else {
                  schemaKeys += key
                  schemaFactory = schemaFactory.thaw().setLoadingConfiguration(loadingCfg.preloadSchema(s"$schemaUri$key", jsNode).freeze()).freeze()
                  Ok(Json.obj())
                }
              case None => Error("No JSON given!", "MISSING_JSON")
            }
          }
        case None => Error("No key for schema given!", "MISSING_SCHEMA_KEY")
      }

  }

}
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
import com.github.fge.jsonschema.core.load.configuration.LoadingConfiguration
import com.github.fge.jsonschema.main.JsonSchemaFactory
import org.vertx.scala.core.FunctionConverters._
import org.vertx.scala.core.json._
import org.vertx.scala.platform.Verticle

import scala.concurrent.Promise
import scala.util.{Failure, Success, Try}

class Starter extends Verticle {

  override def start(p: Promise[Unit]) = try {
    import scala.collection.JavaConverters._
    val config = container.config
    val schemaUri = "vertxjsonschema://"
    val loadingCfg = LoadingConfiguration.newBuilder()
    val factory = JsonSchemaFactory.byDefault()

    val configSchema = new JsonObject( """{
      "$schema": "http://json-schema.org/draft-04/schema#",
      "type": "object",
      "properties": {
        "address": {
          "type": "string"
        },
        "schemas": {
		  "type": "array",
		  "items": {
		    "type": "object",
		    "properties": {
		      "key": {
		        "type": "string"
		      },
		      "schema": {
		        "type": "object"
		      }
		    },
		    "required": ["key", "schema"]
		  }
        }
      }
    }""").toString()

    val report = factory.getJsonSchema(JsonLoader.fromString(configSchema)).validate(JsonLoader.fromString(config.encode()))
    if (!report.isSuccess()) {
      throw new IllegalArgumentException("Invalid config: " + Json.obj("report" -> new JsonArray(report.asScala.map(_.asJson()).mkString("[", ",", "]"))))
    }

    val schemasConfig = config.getArray("schemas", Json.arr()).asScala


    val schemaKeys: Set[String] = Set(schemasConfig.zipWithIndex.toSeq.map {
      case (obj, idx) =>
        val schema = obj.asInstanceOf[JsonObject]
        val key = schema.getString("key")
        val jsonString = schema.getObject("schema", Json.obj()).encode()
        val jsNode = JsonLoader.fromString(jsonString)
        if (!factory.getSyntaxValidator().schemaIsValid(jsNode)) {
          throw new IllegalArgumentException("Schema is invalid: " + jsonString)
        }
        loadingCfg.preloadSchema(s"$schemaUri$key", jsNode)
        key
    }: _*)

    vertx.eventBus.registerHandler(
      config.getString("address", "campudus.jsonvalidator"),
      new SchemaValidatorBusMod(this, schemaKeys, factory.thaw().setLoadingConfiguration(loadingCfg.freeze()).freeze(), schemaUri, loadingCfg), {
        case Success(_) => p.success()
        case Failure(ex) => p.failure(ex)
      }: Try[Void] => Unit)

  } catch {
    case ex: Throwable => p.failure(ex)
  }

}
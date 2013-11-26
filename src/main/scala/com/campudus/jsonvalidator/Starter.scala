package com.campudus.jsonvalidator

import org.vertx.scala.platform.Verticle
import org.vertx.scala.core.json._
import scala.concurrent.Promise
import org.vertx.scala.core.eventbus.Message
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import org.vertx.scala.core.FunctionConverters._
import io.vertx.busmod.ScalaBusMod
import scala.concurrent.Future
import com.github.fge.jsonschema.main.JsonSchemaFactory
import com.github.fge.jsonschema.util.JsonLoader
import com.github.fge.jsonschema.main.JsonSchema

class Starter extends Verticle {

  override def start(p: Promise[Unit]) = try {
    import scala.collection.JavaConverters._
    val config = container.config
    val factory = JsonSchemaFactory.byDefault()
    
    val defaultSchema = new JsonObject("""
    {
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
    }
    """).toString()

    val report = factory.getJsonSchema(JsonLoader.fromString(defaultSchema)).validate(JsonLoader.fromString(config.toString()))
    if(!report.isSuccess()) {
      throw new IllegalArgumentException("Invalid JSON given: " + Json.obj("report" -> new JsonArray(report.asScala.map(_.asJson()).mkString("[", ",", "]"))))
    }

    val schemasConfig = config.getArray("schemas", Json.arr()).asScala

    val schemas: Map[String, JsonSchema] = Map(schemasConfig.zipWithIndex.toSeq.map {
      case (obj, idx) =>
        val schema = obj.asInstanceOf[JsonObject]
        val key = schema.getString("key")
        val jsonString = schema.getObject("schema", Json.obj()).encode()
        val jsNode = JsonLoader.fromString(jsonString)
        if (!factory.getSyntaxValidator().schemaIsValid(jsNode)) {
          throw new IllegalArgumentException("Schema is invalid: " + jsonString)
        }
        val value = factory.getJsonSchema(jsNode)
        (key, value)
    }: _*)

    vertx.eventBus.registerHandler(
      config.getString("address", "campudus.jsonvalidator"),
      new SchemaValidatorBusMod(this, schemas),
      {
        case Success(_) =>
          p.success()
        case Failure(ex) => p.failure(ex)
      }: Try[Void] => Unit)

  } catch {
    case ex: Throwable => p.failure(ex)
  }

}
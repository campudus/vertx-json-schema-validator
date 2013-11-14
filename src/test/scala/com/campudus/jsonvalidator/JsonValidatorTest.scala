package com.campudus.jsonvalidator

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import org.vertx.scala.core.FunctionConverters.tryToAsyncResultHandler
import org.vertx.scala.core.json.Json
import org.vertx.scala.testtools.ScalaClassRunner
import org.vertx.scala.testtools.TestVerticle
import org.junit.Test
import org.vertx.scala.core.eventbus.Message
import org.vertx.java.core.json.JsonObject
import org.vertx.testtools.VertxAssert._
import org.vertx.scala.core.json.JsonArray

class JsonValidatorTest extends TestVerticle {

  override def asyncBefore(): Future[Unit] = {
    val p = Promise[Unit]

    val config = Json.obj("schemas" -> Json.arr(Json.obj("schema" -> """
    {
    	"$schema": "http://json-schema.org/draft-04/schema#",
		"title": "Example Schema",
		"type": "object",
		"properties": {
			"firstName": {
				"type": "string"
			},
			"lastName": {
				"type": "string"
			},
			"age": {
				"description": "Age in years",
				"type": "integer",
				"minimum": 0
			}
		},
		"required": ["firstName", "lastName"]
    }
    """), Json.obj("key" -> "testSchema", "schema" -> """
    {
	    "$schema": "http://json-schema.org/draft-04/schema#",
	    "title": "Product set",
	    "type": "array",
	    "items": {
	        "title": "Product",
	        "type": "object",
	        "properties": {
	            "id": {
	                "description": "The unique identifier for a product",
	                "type": "number"
	            },
	            "name": {
	                "type": "string"
	            },
	            "price": {
	                "type": "number",
	                "minimum": 0,
	                "exclusiveMinimum": true
	            },
	            "tags": {
	                "type": "array",
	                "items": {
	                    "type": "string"
	                },
	                "minItems": 1,
	                "uniqueItems": true
	            },
	            "dimensions": {
	                "type": "object",
	                "properties": {
	                    "length": {"type": "number"},
	                    "width": {"type": "number"},
	                    "height": {"type": "number"}
	                },
	                "required": ["length", "width", "height"]
	            },
	            "warehouseLocation": {
	                "description": "Coordinates of the warehouse with the product",
	                "$ref": "http://json-schema.org/geo"
	            }
	        },
	        "required": ["id", "name", "price"]
	    }
    }
    """)))

    container.deployModule(System.getProperty("vertx.modulename"), config, 1, {
      case Success(deploymentId) => p.success()
      case Failure(ex) => p.failure(ex)
    }: Try[String] => Unit)
    p.future
  }

  /*@Test
  def loadedSchema(): Unit = {
    ???
  }*/

  @Test
  def missingKey(): Unit = {
    vertx.eventBus.send("campudus.jsonvalidator", Json.obj("action" -> "validate", "json" -> """
    {
    	"firstName" : "Hans",
     	"lastName" : "Dampf"
    }
    """), { msg: Message[JsonObject] =>
      assertEquals("error", msg.body.getString("status"))
      assertEquals("MISSING_SCHEMA_KEY", msg.body.getString("error"))
    })
  }

  @Test
  def validJson(): Unit = {
    vertx.eventBus.send("campudus.jsonvalidator", Json.obj("action" -> "validate", "key" -> "schema0", "json" -> """
    {
    	"firstName" : "Hans",
     	"lastName" : "Dampf"
    }
    """), { msg: Message[JsonObject] =>
      assertEquals("ok", msg.body.getString("status"))
    })
  }

  @Test
  def invalidJson(): Unit = {
    vertx.eventBus.send("campudus.jsonvalidator", Json.obj("action" -> "validate", "key" -> "schema0", "json" -> """
    {
    	"firstName" : "Hans"
    }
    """), { msg: Message[JsonObject] =>
      assertEquals("error", msg.body.getString("status"))
      assertEquals("VALIDATION_ERROR", msg.body.getString("error"))
      assertEquals(new JsonArray("""[ {
  "level" : "error",
  "schema" : {
    "loadingURI" : "#",
    "pointer" : ""
  },
  "instance" : {
    "pointer" : ""
  },
  "domain" : "validation",
  "keyword" : "required",
  "message" : "missing required property(ies)",
  "required" : [ "firstName", "lastName" ],
  "missing" : [ "lastName" ]
} ]"""), msg.body.getArray("report"))
    })
  }
}
package com.campudus.jsonvalidator

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import org.vertx.scala.core.FunctionConverters.tryToAsyncResultHandler
import org.vertx.scala.core.json._
import org.vertx.scala.testtools.ScalaClassRunner
import org.vertx.scala.testtools.TestVerticle
import org.junit.Test
import org.vertx.scala.core.eventbus.Message
import org.vertx.testtools.VertxAssert._

class JsonValidatorTest extends TestVerticle {

  override def asyncBefore(): Future[Unit] = {
    val p = Promise[Unit]

    val config = Json.obj("schemas" -> Json.arr(Json.obj("key" -> "schema0", "schema" -> new JsonObject("""
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
    """)), Json.obj("key" -> "testSchema", "schema" -> new JsonObject("""
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
    """))))

    container.deployModule(System.getProperty("vertx.modulename"), config, 1, {
      case Success(deploymentId) => p.success()
      case Failure(ex) => p.failure(ex)
    }: Try[String] => Unit)
    p.future
  }

  val validComplexJson = new JsonArray("""
	[
	 {
	    "id": 2,
	    "name": "An ice sculpture",
	    "price": 12.50,
	    "tags": ["cold", "ice"],
	    "dimensions": {
	      "length": 7.0,
	      "width": 12.0,
	      "height": 9.5
	    },
	    "warehouseLocation": {
	      "latitude": -78.75,
	      "longitude": 20.4
	    }
	  },
	  {
	    "id": 3,
	    "name": "A blue mouse",
	    "price": 25.50,
	      "dimensions": {
	      "length": 3.1,
	      "width": 1.0,
	      "height": 1.0
	    },
	    "warehouseLocation": {
	      "latitude": 54.4,
	      "longitude": -32.7
	    }
	  }
	]
  """)

  val invalidComplexJson = new JsonArray("""
	[
	 {
	    "id": 2,
	    "name": "An ice sculpture",
	    "price": 12.50,
	    "tags": ["cold", "ice"],
	    "dimensions": {
	      "length": 7.0,
	      "width": 12.0,
	      "height": 9.5
	    },
	    "warehouseLocation": {
	      "latitude": -78.75,
	      "longitude": 20.4
	    }
	  },
	  {
	    "id": 3,
	    "name": "A blue mouse",
	    "price": 25.50,
	      "dimensions": {
	      "length": 3.1,
	      "height": 1.0
	    },
	    "warehouseLocation": {
	      "latitude": 54.4,
	      "longitude": -32.7
	    }
	  }
	]
  """)

  val validSimpleJson = new JsonObject("""{
    "firstName" : "Hans",
    "lastName" : "Dampf"
  }""")

  val invalidSimpleJson = new JsonObject("""{
    "firstName" : "Hans"
  }""")

  val addNewSchema = new JsonObject("""
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
    """)
  
    val addNewInvalidSchema = new JsonObject("""
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
				"type": "blub",
				"minimum": 0
			}
		},
		"required": ["firstName", "lastName"]
    }
    """)
  
  @Test
  def addSchema(): Unit = {
    vertx.eventBus.send("campudus.jsonvalidator", Json.obj("action" -> "addSchema", "key" -> "addSchema", "jsonSchema" -> addNewSchema), { msg: Message[JsonObject] =>
      assertEquals("ok", msg.body.getString("status"))
      vertx.eventBus.send("campudus.jsonvalidator", Json.obj("action" -> "validate", "key" -> "addSchema", "json" -> validSimpleJson), { msg: Message[JsonObject] =>
        assertEquals("ok", msg.body.getString("status"))
        testComplete()
      })
    })
  }

  @Test
  def addSchemaWithoutKey(): Unit = {
    vertx.eventBus.send("campudus.jsonvalidator", Json.obj("action" -> "addSchema", "jsonSchema" -> addNewSchema), { msg: Message[JsonObject] =>
      assertEquals("error", msg.body.getString("status"))
      assertEquals("MISSING_SCHEMA_KEY", msg.body.getString("error"))
      testComplete()
    })
  }

  @Test
  def addSchemaWithInvalidJson(): Unit = {
    vertx.eventBus.send("campudus.jsonvalidator", Json.obj("action" -> "addSchema", "key" -> "schema123", "jsonSchema" -> addNewInvalidSchema), { msg: Message[JsonObject] =>
      assertEquals("error", msg.body.getString("status"))
      assertEquals("INVALID_SCHEMA", msg.body.getString("error"))
      testComplete()
    })
  }

  @Test
  def addSchemaWithSameKeyAndWithoutOverwrite(): Unit = {
    vertx.eventBus.send("campudus.jsonvalidator", Json.obj("action" -> "addSchema", "key" -> "testSchema", "jsonSchema" -> addNewSchema), { msg: Message[JsonObject] =>
      assertEquals("error", msg.body.getString("status"))
      assertEquals("EXISTING_SCHEMA_KEY", msg.body.getString("error"))
      testComplete()
    })
  }

  @Test
  def OverwriteTrue(): Unit = {
    vertx.eventBus.send("campudus.jsonvalidator", Json.obj("action" -> "addSchema", "key" -> "testSchema", "jsonSchema" -> addNewSchema, "overwrite" -> true), { msg: Message[JsonObject] =>
      assertEquals("ok", msg.body.getString("status"))
      testComplete()
    })
  }

  @Test
  def OverwriteFalse(): Unit = {
    vertx.eventBus.send("campudus.jsonvalidator", Json.obj("action" -> "addSchema", "key" -> "testSchema", "jsonSchema" -> addNewSchema, "overwrite" -> false), { msg: Message[JsonObject] =>
      assertEquals("error", msg.body.getString("status"))
      assertEquals("EXISTING_SCHEMA_KEY", msg.body.getString("error"))
      testComplete()
    })
  }

  @Test
  def invalidOverwrite(): Unit = {
    vertx.eventBus.send("campudus.jsonvalidator", Json.obj("action" -> "addSchema", "key" -> "testSchema", "jsonSchema" -> addNewSchema, "overwrite" -> "invalid"), { msg: Message[JsonObject] =>
      assertEquals("error", msg.body.getString("status"))
      assertEquals("INVALID_OVERWRITE", msg.body.getString("error"))
      testComplete()
    })
  }

  @Test
  def missingKey(): Unit = {
    vertx.eventBus.send("campudus.jsonvalidator", Json.obj("action" -> "validate", "json" -> validSimpleJson), { msg: Message[JsonObject] =>
      assertEquals("error", msg.body.getString("status"))
      assertEquals("MISSING_SCHEMA_KEY", msg.body.getString("error"))
      testComplete()
    })
  }

  @Test
  def invalidKey(): Unit = {
    vertx.eventBus.send("campudus.jsonvalidator", Json.obj("action" -> "validate", "key" -> "non-existent", "json" -> validSimpleJson), { msg: Message[JsonObject] =>
      assertEquals("error", msg.body.getString("status"))
      assertEquals("INVALID_SCHEMA_KEY", msg.body.getString("error"))
      testComplete()
    })
  }

  @Test
  def validJson(): Unit = {
    vertx.eventBus.send("campudus.jsonvalidator", Json.obj("action" -> "validate", "key" -> "schema0", "json" -> validSimpleJson), { msg: Message[JsonObject] =>
      assertEquals("ok", msg.body.getString("status"))
      testComplete()
    })
  }

  @Test
  def validJson2(): Unit = {
    vertx.eventBus.send("campudus.jsonvalidator", Json.obj("action" -> "validate", "key" -> "testSchema", "json" -> validComplexJson), { msg: Message[JsonObject] =>
      assertEquals("ok", msg.body.getString("status"))
      testComplete()
    })
  }

  @Test
  def missingJson(): Unit = {
    vertx.eventBus.send("campudus.jsonvalidator", Json.obj("action" -> "validate", "key" -> "schema0"), { msg: Message[JsonObject] =>
      assertEquals("error", msg.body.getString("status"))
      assertEquals("MISSING_JSON", msg.body.getString("error"))
      testComplete()
    })
  }

  @Test
  def invalidJson(): Unit = {
    vertx.eventBus.send("campudus.jsonvalidator", Json.obj("action" -> "validate", "key" -> "schema0", "json" -> invalidSimpleJson), { msg: Message[JsonObject] =>
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
      testComplete()
    })
  }

  @Test
  def invalidJson2(): Unit = {
    vertx.eventBus.send("campudus.jsonvalidator", Json.obj("action" -> "validate", "key" -> "testSchema", "json" -> invalidComplexJson), { msg: Message[JsonObject] =>
      assertEquals("error", msg.body.getString("status"))
      assertEquals("VALIDATION_ERROR", msg.body.getString("error"))
      assertEquals(new JsonArray("""[ {
  "level" : "error",
  "schema" : {
    "loadingURI" : "#",
    "pointer" : "/items/properties/dimensions"
  },
  "instance" : {
    "pointer" : "/1/dimensions"
  },
  "domain" : "validation",
  "keyword" : "required",
  "message" : "missing required property(ies)",
  "required" : [ "height", "length", "width" ],
  "missing" : [ "width" ]
} ]"""), msg.body.getArray("report"))
      testComplete()
    })
  }

  @Test
  def noRealJsonParameter: Unit = {
    vertx.eventBus.send("campudus.jsonvalidator", Json.obj("action" -> "validate", "key" -> "testSchema", "json" -> 1), { msg: Message[JsonObject] =>
      assertEquals("error", msg.body.getString("status"))
      assertEquals("INVALID_JSON", msg.body.getString("error"))
      testComplete()
    })
  }

  @Test def getAllSchemas(): Unit = {
    vertx.eventBus.send("campudus.jsonvalidator", Json.obj("action" -> "getSchemaKeys"), { msg: Message[JsonObject] =>
      assertEquals("ok", msg.body.getString("status"))
      assertEquals(Json.arr("schema0", "testSchema"), msg.body.getArray("schemas"))
      testComplete()
    })
  }
}

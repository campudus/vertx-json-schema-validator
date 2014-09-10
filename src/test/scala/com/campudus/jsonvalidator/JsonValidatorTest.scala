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

import org.junit.Test
import org.vertx.scala.core.FunctionConverters.tryToAsyncResultHandler
import org.vertx.scala.core.eventbus.Message
import org.vertx.scala.core.json._
import org.vertx.scala.testtools.TestVerticle
import org.vertx.testtools.VertxAssert._

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

class JsonValidatorTest extends TestVerticle {

  val address = "campudus.jsonvalidator"

  override def asyncBefore(): Future[Unit] = {
    val p = Promise[Unit]

    val config = Json.obj("schemas" -> Json.arr(
      Json.obj("key" -> "schema0", "schema" -> Json.fromObjectString(
        """
          |{
          |  	"$schema": "http://json-schema.org/draft-04/schema#",
          |	"title": "Example Schema",
          |	"type": "object",
          |	"properties": {
          |		"firstName": {
          |			"type": "string"
          |		},
          |		"lastName": {
          |			"type": "string"
          |		},
          |		"age": {
          |			"description": "Age in years",
          |			"type": "integer",
          |			"minimum": 0
          |		}
          |	},
          |	"required": ["firstName", "lastName"]
          |}""".stripMargin)),
      Json.obj("key" -> "geoschema", "schema" -> Json.fromObjectString(
        """
          |{
          |  "description": "A geographical coordinate",
          |  "type": "object",
          |  "properties": {
          |    "latitude": { "type": "number" },
          |    "longitude": { "type": "number" }
          |  },
          |  "required": ["latitude", "longitude"]
          |}
        """.stripMargin)),
      Json.obj("key" -> "testschema", "schema" -> Json.fromObjectString(
        """
          |{
          |  "$schema": "http://json-schema.org/draft-04/schema#",
          |  "title": "Product set",
          |  "type": "array",
          |  "items": {
          |    "title": "Product",
          |    "type": "object",
          |    "properties": {
          |      "id": {
          |        "description": "The unique identifier for a product",
          |        "type": "number"
          |      },
          |      "name": {
          |        "type": "string"
          |      },
          |      "price": {
          |        "type": "number",
          |        "minimum": 0,
          |        "exclusiveMinimum": true
          |      },
          |      "tags": {
          |        "type": "array",
          |        "items": {
          |          "type": "string"
          |        },
          |        "minItems": 1,
          |        "uniqueItems": true
          |      },
          |      "dimensions": {
          |        "type": "object",
          |        "properties": {
          |          "length": {"type": "number"},
          |          "width": {"type": "number"},
          |          "height": {"type": "number"}
          |        },
          |        "required": ["length", "width", "height"]
          |      },
          |      "warehouseLocation": {
          |        "description": "Coordinates of the warehouse with the product",
          |        "$ref": "vertxjsonschema://geoschema"
          |      }
          |    },
          |    "required": ["id", "name", "price"]
          |  }
          |}""".stripMargin))))

    container.deployModule(System.getProperty("vertx.modulename"), config, 1, {
      case Success(deploymentId) => p.success()
      case Failure(ex) => p.failure(ex)
    }: Try[String] => Unit)
    p.future
  }

  val validComplexJson = Json.fromArrayString(
    """
      |[
      |  {
      |    "id": 2,
      |    "name": "An ice sculpture",
      |    "price": 12.50,
      |    "tags": ["cold", "ice"],
      |    "dimensions": {
      |      "length": 7.0,
      |      "width": 12.0,
      |      "height": 9.5
      |    },
      |    "warehouseLocation": {
      |      "latitude": -78.75,
      |      "longitude": 20.4
      |    }
      |  },
      |  {
      |    "id": 3,
      |    "name": "A blue mouse",
      |    "price": 25.50,
      |    "dimensions": {
      |      "length": 3.1,
      |      "width": 1.0,
      |      "height": 1.0
      |    },
      |    "warehouseLocation": {
      |      "latitude": 54.4,
      |      "longitude": -32.7
      |    }
      |  }
      |]""".stripMargin)

  val invalidComplexJson = Json.fromArrayString(
    """
      |[
      |  {
      |    "id": 2,
      |    "name": "An ice sculpture",
      |    "price": 12.50,
      |    "tags": ["cold", "ice"],
      |    "dimensions": {
      |      "length": 7.0,
      |      "width": 12.0,
      |      "height": 9.5
      |    },
      |    "warehouseLocation": {
      |      "latitude": -78.75,
      |      "longitude": 20.4
      |    }
      |  },
      |  {
      |    "id": 3,
      |    "name": "A blue mouse",
      |    "price": 25.50,
      |    "dimensions": {
      |      "length": 3.1,
      |      "height": 1.0
      |    },
      |    "warehouseLocation": {
      |      "latitude": 54.4,
      |      "longitude": -32.7
      |    }
      |  }
      |]""".stripMargin)

  val validSimpleJson = Json.obj("firstName" -> "Hans", "lastName" -> "Dampf")

  val invalidSimpleJson = Json.obj("firstName" -> "Hans")

  val addRefSchema = Json.fromObjectString(
    """
      |{
      |  "$schema": "http://json-schema.org/draft-04/schema#",
      |  "title": "Example Schema",
      |  "type": "object",
      |  "properties": {
      |    "person": {
      |      "type" : "object",
      |      "properties": {
      |        "location" : {
      |          "$ref": "vertxjsonschema://geoschema"
      |        },
      |        "job": {
      |          "type": "string"
      |        }
      |      }
      |    }
      |  }
      |}""".stripMargin)

  val addMissingRefSchema = Json.fromObjectString(
    """
      |{
      |  "$schema": "http://json-schema.org/draft-04/schema#",
      |  "title": "Example Schema",
      |  "type": "object",
      |  "properties": {
      |    "person": {
      |      "type" : "object",
      |      "properties": {
      |        "location" : {
      |          "$ref": "vertxjsonschema://missinglocationschema"
      |        },
      |        "job": {
      |          "type": "string"
      |        }
      |      }
      |    }
      |  }
      |}""".stripMargin)

  val validRefSchema = Json.fromObjectString(
    """
      |{
      |  "person" : {
      |    "location" : {
      |      "latitude": 48,
      |      "longitude": 11
      |    },
      |    "job" : "Kunstfurzer"
      |  }
      |}
    """.stripMargin)


  val invalidRefSchema = Json.fromObjectString(
    """
      |{
      |  "person" : {
      |    "location" : {
      |      "latitude": 48
      |    },
      |    "job" : "Kunstfurzer"
      |  }
      |}
    """.stripMargin)

  val addNewSchema = Json.fromObjectString(
    """
      |{
      |  "$schema": "http://json-schema.org/draft-04/schema#",
      |  "title": "Example Schema",
      |  "type": "object",
      |  "properties": {
      |    "firstName": {
      |      "type": "string"
      |    },
      |    "lastName": {
      |      "type": "string"
      |    },
      |    "age": {
      |      "description": "Age in years",
      |      "type": "integer",
      |      "minimum": 0
      |    }
      |  },
      |  "required": ["firstName", "lastName"]
      |}""".stripMargin)

  val addNewInvalidSchema = Json.fromObjectString(
    """
      |{
      |  "$schema": "http://json-schema.org/draft-04/schema#",
      |  "title": "Example Schema",
      |  "type": "object",
      |  "properties": {
      |    "firstName": {
      |      "type": "string"
      |    },
      |    "lastName": {
      |      "type": "string"
      |    },
      |    "age": {
      |      "description": "Age in years",
      |      "type": "blub",
      |      "minimum": 0
      |    }
      |  },
      |  "required": ["firstName", "lastName"]
      |}""".stripMargin)

  def printError(msg: Message[JsonObject]): String = {
    import scala.collection.JavaConverters._
    if (msg.body.getArray("report") != null) {
      return s"Error: ${msg.body.getString("error")}\nMessage: ${msg.body.getString("message")}\nReport: ${msg.body.getArray("report").asScala}"
    }
    return s"Error: ${msg.body.getString("error")}\nMessage: ${msg.body.getString("message")}"
  }

  @Test def addSchema(): Unit = {
    vertx.eventBus.send(address, Json.obj("action" -> "addSchema", "key" -> "addSchema", "jsonSchema" -> addNewSchema), {
      msg: Message[JsonObject] =>
        assertEquals(printError(msg), "ok", msg.body.getString("status"))
        vertx.eventBus.send(address, Json.obj("action" -> "validate", "key" -> "addSchema", "json" -> validSimpleJson), {
          msg: Message[JsonObject] =>
            assertEquals("ok", msg.body.getString("status"))
            testComplete()
        })
    })
  }

  @Test def addSchemaWithoutKey(): Unit = {
    vertx.eventBus.send(address, Json.obj("action" -> "addSchema", "jsonSchema" -> addNewSchema), {
      msg: Message[JsonObject] =>
        assertEquals("error", msg.body.getString("status"))
        assertEquals("MISSING_SCHEMA_KEY", msg.body.getString("error"))
        testComplete()
    })
  }

  @Test def addSchemaWithInvalidJson(): Unit = {
    vertx.eventBus.send(address, Json.obj("action" -> "addSchema", "key" -> "schema123", "jsonSchema" -> addNewInvalidSchema), {
      msg: Message[JsonObject] =>
        assertEquals("error", msg.body.getString("status"))
        assertEquals("INVALID_SCHEMA", msg.body.getString("error"))
        testComplete()
    })
  }

  @Test def addSchemaWithSameKeyAndWithoutOverwrite(): Unit = {
    vertx.eventBus.send(address, Json.obj("action" -> "addSchema", "key" -> "testschema", "jsonSchema" -> addNewSchema), {
      msg: Message[JsonObject] =>
        assertEquals("error", msg.body.getString("status"))
        assertEquals("EXISTING_SCHEMA_KEY", msg.body.getString("error"))
        testComplete()
    })
  }

  @Test def OverwriteFalse(): Unit = {
    vertx.eventBus.send(address, Json.obj("action" -> "addSchema", "key" -> "testschema", "jsonSchema" -> addNewSchema), {
      msg: Message[JsonObject] =>
        assertEquals("error", msg.body.getString("status"))
        assertEquals("EXISTING_SCHEMA_KEY", msg.body.getString("error"))
        testComplete()
    })
  }

  @Test def missingKey(): Unit = {
    vertx.eventBus.send(address, Json.obj("action" -> "validate", "json" -> validSimpleJson), {
      msg: Message[JsonObject] =>
        assertEquals("error", msg.body.getString("status"))
        assertEquals("MISSING_SCHEMA_KEY", msg.body.getString("error"))
        testComplete()
    })
  }

  @Test def invalidKey(): Unit = {
    vertx.eventBus.send(address, Json.obj("action" -> "validate", "key" -> "non-existent", "json" -> validSimpleJson), {
      msg: Message[JsonObject] =>
        assertEquals("error", msg.body.getString("status"))
        assertEquals("INVALID_SCHEMA_KEY", msg.body.getString("error"))
        testComplete()
    })
  }

  @Test def validJson(): Unit = {
    vertx.eventBus.send(address, Json.obj("action" -> "validate", "key" -> "schema0", "json" -> validSimpleJson), {
      msg: Message[JsonObject] =>
        assertEquals("ok", msg.body.getString("status"))
        testComplete()
    })
  }

  @Test def validJson2(): Unit = {
    vertx.eventBus.send(address, Json.obj("action" -> "validate", "key" -> "testschema", "json" -> validComplexJson), {
      msg: Message[JsonObject] =>
        assertEquals("ok", msg.body.getString("status"))
        testComplete()
    })
  }

  @Test def missingJson(): Unit = {
    vertx.eventBus.send(address, Json.obj("action" -> "validate", "key" -> "schema0"), {
      msg: Message[JsonObject] =>
        assertEquals("error", msg.body.getString("status"))
        assertEquals("MISSING_JSON", msg.body.getString("error"))
        testComplete()
    })
  }

  @Test def invalidJson(): Unit = {
    vertx.eventBus.send(address, Json.obj("action" -> "validate", "key" -> "schema0", "json" -> invalidSimpleJson), {
      msg: Message[JsonObject] =>
        assertEquals("error", msg.body.getString("status"))
        assertEquals("VALIDATION_ERROR", msg.body.getString("error"))
        assertEquals(Json.fromArrayString(
          """
            |[ {
            |  "level" : "error",
            |  "schema" : {
            |    "loadingURI" : "vertxjsonschema://schema0#",
            |    "pointer" : ""
            |  },
            |  "instance" : {
            |    "pointer" : ""
            |  },
            |  "domain" : "validation",
            |  "keyword" : "required",
            |  "message" : "object has missing required properties ([\"lastName\"])",
            |  "required" : [ "firstName", "lastName" ],
            |  "missing" : [ "lastName" ]
            |} ]""".stripMargin), msg.body.getArray("report"))
        testComplete()
    })
  }

  @Test def invalidJson2(): Unit = {
    vertx.eventBus.send(address, Json.obj("action" -> "validate", "key" -> "testschema", "json" -> invalidComplexJson), {
      msg: Message[JsonObject] =>
        assertEquals("error", msg.body.getString("status"))
        assertEquals("VALIDATION_ERROR", msg.body.getString("error"))
        assertEquals(Json.fromArrayString(
          """
            |[ {
            |  "level" : "error",
            |  "schema" : {
            |    "loadingURI" : "vertxjsonschema://testschema#",
            |    "pointer" : "/items/properties/dimensions"
            |  },
            |  "instance" : {
            |    "pointer" : "/1/dimensions"
            |  },
            |  "domain" : "validation",
            |  "keyword" : "required",
            |  "message" : "object has missing required properties ([\"width\"])",
            |  "required" : [ "height", "length", "width" ],
            |  "missing" : [ "width" ]
            |} ]""".stripMargin), msg.body.getArray("report"))
        testComplete()
    })
  }

  @Test def noRealJsonParameter: Unit = {
    vertx.eventBus.send(address, Json.obj("action" -> "validate", "key" -> "testschema", "json" -> 1), {
      msg: Message[JsonObject] =>
        assertEquals("error", msg.body.getString("status"))
        assertEquals("INVALID_JSON", msg.body.getString("error"))
        testComplete()
    })
  }

  @Test def getAllSchemas(): Unit = {
    vertx.eventBus.send(address, Json.obj("action" -> "getSchemaKeys"), {
      msg: Message[JsonObject] =>
        assertEquals(printError(msg), "ok", msg.body.getString("status"))
        assertEquals(Json.arr("schema0", "geoschema", "testschema"), msg.body.getArray("schemas"))
        testComplete()
    })
  }

  @Test def useReferencedSchemaWithoutProvidingItFirst(): Unit = {
    vertx.eventBus.send(address, Json.obj("action" -> "addSchema", "key" -> "refschema", "jsonSchema" -> addMissingRefSchema), {
      msg: Message[JsonObject] =>
        assertEquals(printError(msg), "ok", msg.body.getString("status"))
        vertx.eventBus.send(address, Json.obj("action" -> "validate", "key" -> "refschema", "json" -> validRefSchema), {
          msg: Message[JsonObject] =>
            assertEquals(printError(msg), "error", msg.body.getString("status"))
            assertEquals("VALIDATION_PROCESS_ERROR", msg.body.getString("error"))
            testComplete()
        })
    })
  }

  @Test def useReferencedSchemaWithValidData(): Unit = {
    vertx.eventBus.send(address, Json.obj("action" -> "addSchema", "key" -> "refschema", "jsonSchema" -> addRefSchema), {
      msg: Message[JsonObject] =>
        assertEquals(printError(msg), "ok", msg.body.getString("status"))
        vertx.eventBus.send(address, Json.obj("action" -> "validate", "key" -> "refschema", "json" -> validRefSchema), {
          msg: Message[JsonObject] =>
            assertEquals(printError(msg), "ok", msg.body.getString("status"))
            testComplete()
        })
    })
  }

  @Test def useReferencedSchemaWithInvalidData(): Unit = {
    vertx.eventBus.send(address, Json.obj("action" -> "addSchema", "key" -> "refschema", "jsonSchema" -> addRefSchema), {
      msg: Message[JsonObject] =>
        assertEquals(printError(msg), "ok", msg.body.getString("status"))
        vertx.eventBus.send(address, Json.obj("action" -> "validate", "key" -> "refschema", "json" -> invalidRefSchema), {
          msg: Message[JsonObject] =>
            assertEquals(printError(msg), "error", msg.body.getString("status"))
            assertEquals(Json.fromArrayString("""
                |[ {
                |  "level" : "error",
                |  "schema" : {
                |    "loadingURI" : "vertxjsonschema://geoschema#",
                |    "pointer" : ""
                |  },
                |  "instance" : {
                |    "pointer" : "/person/location"
                |  },
                |  "domain" : "validation",
                |  "keyword" : "required",
                |  "message" : "object has missing required properties ([\"longitude\"])",
                |  "required" : ["latitude", "longitude"],
                |  "missing" : ["longitude"]
                |} ]""".stripMargin), msg.body.getArray("report"))
            testComplete()
        })
    })
  }
}

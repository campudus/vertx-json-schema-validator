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

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import org.vertx.scala.core.FunctionConverters.tryToAsyncResultHandler
import org.vertx.scala.core.json._
import org.vertx.scala.testtools.ScalaClassRunner
import org.vertx.scala.testtools.TestVerticle
import org.junit.{Ignore, Test}
import org.vertx.scala.core.eventbus.Message
import org.vertx.testtools.VertxAssert._

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
      Json.obj("key" -> "geoSchema", "schema" -> Json.fromObjectString(
        """
          |{
          |  "description": "A geographical coordinate",
          |  "type": "object",
          |  "properties": {
          |    "latitude": { "type": "number" },
          |    "longitude": { "type": "number" }
          |  }
          |}
        """.stripMargin)),
      Json.obj("key" -> "testSchema", "schema" -> Json.fromObjectString(
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
          |        "$ref": "vertxjsonschema://geoSchema"
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
      |      "location" : {
      |        "$ref": "vertxjsonschema://geoSchema"
      |      },
      |      "job": {
      |        "type": "string"
      |      }
      |    }
      |  }
      |}""".stripMargin)

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

  @Test def addSchema(): Unit = {
    vertx.eventBus.send(address, Json.obj("action" -> "addSchema", "key" -> "addSchema", "jsonSchema" -> addNewSchema), {
      msg: Message[JsonObject] =>
        assertEquals("ok", msg.body.getString("status"))
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
    vertx.eventBus.send(address, Json.obj("action" -> "addSchema", "key" -> "testSchema", "jsonSchema" -> addNewSchema), {
      msg: Message[JsonObject] =>
        assertEquals("error", msg.body.getString("status"))
        assertEquals("EXISTING_SCHEMA_KEY", msg.body.getString("error"))
        testComplete()
    })
  }

  @Test def OverwriteTrue(): Unit = {
    vertx.eventBus.send(address, Json.obj("action" -> "addSchema", "key" -> "testSchema", "jsonSchema" -> addNewSchema, "overwrite" -> true), {
      msg: Message[JsonObject] =>
        assertEquals("ok", msg.body.getString("status"))
        testComplete()
    })
  }

  @Test def OverwriteFalse(): Unit = {
    vertx.eventBus.send(address, Json.obj("action" -> "addSchema", "key" -> "testSchema", "jsonSchema" -> addNewSchema, "overwrite" -> false), {
      msg: Message[JsonObject] =>
        assertEquals("error", msg.body.getString("status"))
        assertEquals("EXISTING_SCHEMA_KEY", msg.body.getString("error"))
        testComplete()
    })
  }

  @Test def invalidOverwrite(): Unit = {
    vertx.eventBus.send(address, Json.obj("action" -> "addSchema", "key" -> "testSchema", "jsonSchema" -> addNewSchema, "overwrite" -> "invalid"), {
      msg: Message[JsonObject] =>
        assertEquals("error", msg.body.getString("status"))
        assertEquals("INVALID_OVERWRITE", msg.body.getString("error"))
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

  @Ignore
  @Test def validJson2(): Unit = {
    vertx.eventBus.send(address, Json.obj("action" -> "validate", "key" -> "testSchema", "json" -> validComplexJson), {
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
            |    "loadingURI" : "#",
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

  @Ignore
  @Test def invalidJson2(): Unit = {
    vertx.eventBus.send(address, Json.obj("action" -> "validate", "key" -> "testSchema", "json" -> invalidComplexJson), {
      msg: Message[JsonObject] =>
        assertEquals("error", msg.body.getString("status"))
        assertEquals("VALIDATION_ERROR", msg.body.getString("error"))
        assertEquals(Json.fromArrayString(
          """
            |[ {
            |  "level" : "error",
            |  "schema" : {
            |    "loadingURI" : "#",
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
    vertx.eventBus.send(address, Json.obj("action" -> "validate", "key" -> "testSchema", "json" -> 1), {
      msg: Message[JsonObject] =>
        assertEquals("error", msg.body.getString("status"))
        assertEquals("INVALID_JSON", msg.body.getString("error"))
        testComplete()
    })
  }

  @Test def getAllSchemas(): Unit = {
    vertx.eventBus.send(address, Json.obj("action" -> "getSchemaKeys"), {
      msg: Message[JsonObject] =>
        assertEquals("ok", msg.body.getString("status"))
        assertEquals(Json.arr("schema0", "geoSchema", "testSchema"), msg.body.getArray("schemas"))
        testComplete()
    })
  }

  @Ignore
  @Test def useReferencedSchemaWithoutProvidingItFirst(): Unit = {
    vertx.eventBus.send(address, Json.obj("action" -> "addSchema", "key" -> "testSchema", "jsonSchema" -> addRefSchema, "overwrite" -> true), {
      msg: Message[JsonObject] =>
        assertEquals("error", msg.body.getString("status"))
        testComplete()
    })
  }

  @Ignore
  @Test def useReferencedSchema(): Unit = {
    vertx.eventBus.send(address, Json.obj("action" -> "addSchema", "key" -> "refSchema", "jsonSchema" -> addNewSchema, "overwrite" -> true), {
      msg: Message[JsonObject] =>
        assertEquals("ok", msg.body.getString("status"))
        testComplete()
    })
  }
}

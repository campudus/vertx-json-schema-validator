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
import scala.io.Source
import scala.util.{Failure, Success, Try}

class JsonValidatorTest extends TestVerticle {

  val address = "campudus.jsonvalidator"

  override def asyncBefore(): Future[Unit] = {
    val p = Promise[Unit]

    val config = Json.obj("schemas" -> Json.arr(
      Json.obj("key" -> "schema0", "schema" -> jsonFromFile("schema0.json")),
      Json.obj("key" -> "geoschema", "schema" -> jsonFromFile("geoSchema.json")),
      Json.obj("key" -> "testschema", "schema" -> jsonFromFile("testSchema.json"))))

    container.deployModule(System.getProperty("vertx.modulename"), config, 1, {
      case Success(deploymentId) => p.success()
      case Failure(ex) => p.failure(ex)
    }: Try[String] => Unit)
    p.future
  }

  private def readJsonFile(f: String): String = Source.fromFile(s"src/test/resources/$f").getLines().mkString
  private def jsonFromFile(f: String): JsonObject = Json.fromObjectString(readJsonFile(f))
  private def jsonArrayFromFile(f: String): JsonArray = Json.fromArrayString(readJsonFile(f))

  val validComplexJson = jsonArrayFromFile("validComplexJson.json")

  val invalidComplexJson = jsonArrayFromFile("invalidComplexJson.json")

  val validSimpleJson = Json.obj("firstName" -> "Hans", "lastName" -> "Dampf")

  val invalidSimpleJson = Json.obj("firstName" -> "Hans")

  val addRefSchema = jsonFromFile("addRefSchema.json")

  val addMissingRefSchema = jsonFromFile("addMissingRefSchema.json")

  val validRefSchema = jsonFromFile("validRefSchema.json")

  val invalidRefSchema = jsonFromFile("invalidRefSchema.json")

  val addNewSchema = jsonFromFile("addNewSchema.json")

  val addNewInvalidSchema = jsonFromFile("addNewInvalidSchema.json")

  def printError(msg: Message[JsonObject]): String = {
    import scala.collection.JavaConverters._
    if (msg.body.getArray("report") != null) {
      s"Error: ${msg.body.getString("error")}\nMessage: ${msg.body.getString("message")}\nReport: ${msg.body.getArray("report").asScala}"
    } else {
      s"Error: ${msg.body.getString("error")}\nMessage: ${msg.body.getString("message")}"
    }
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
        assertEquals(jsonArrayFromFile("invalidJson.json"), msg.body.getArray("report"))
        testComplete()
    })
  }

  @Test def invalidJson2(): Unit = {
    vertx.eventBus.send(address, Json.obj("action" -> "validate", "key" -> "testschema", "json" -> invalidComplexJson), {
      msg: Message[JsonObject] =>
        assertEquals("error", msg.body.getString("status"))
        assertEquals("VALIDATION_ERROR", msg.body.getString("error"))
        assertEquals(jsonArrayFromFile("invalidJson2.json"), msg.body.getArray("report"))
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
            assertEquals(jsonArrayFromFile("useReferencedSchemaWithInvalidData.json"), msg.body.getArray("report"))
            testComplete()
        })
    })
  }
}

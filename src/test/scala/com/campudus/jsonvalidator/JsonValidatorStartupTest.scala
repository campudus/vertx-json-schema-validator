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
import org.junit.Test
import org.vertx.scala.core.eventbus.Message
import org.vertx.testtools.VertxAssert._

class JsonValidatorStartupTest extends TestVerticle {

  @Test def startupFailWithInvalidSchema() = {
    val config = Json.obj("schemas" -> Json.arr(Json.obj("key" -> "testSchema", "schema" -> new JsonObject("""
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
				"type": "blubb",
				"minimum": 0
			}
		},
		"required": ["firstName", "lastName"]
    }
    """))))

    container.deployModule(System.getProperty("vertx.modulename"), config, 1, {
      case Success(deploymentId) => fail("Deployment should fail with wrong Schema but was successful")
      case Failure(ex) => testComplete()
    }: Try[String] => Unit)
  }
  
  @Test def startupFailWithoutKey() = {
    val config = Json.obj("schemas" -> Json.arr(Json.obj("schema" -> new JsonObject("""
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
    """))))

    container.deployModule(System.getProperty("vertx.modulename"), config, 1, {
      case Success(deploymentId) => fail("Deployment should fail with wrong Schema but was successful")
      case Failure(ex) => testComplete()
    }: Try[String] => Unit)
  }

  @Test def startupFailWithoutSchema() = {
    val config = Json.obj("schemas" -> Json.arr(Json.obj("key" -> "testSchema")))

    container.deployModule(System.getProperty("vertx.modulename"), config, 1, {
      case Success(deploymentId) => fail("Deployment should fail with wrong Schema but was successful")
      case Failure(ex) => testComplete()
    }: Try[String] => Unit)
  }
  
}
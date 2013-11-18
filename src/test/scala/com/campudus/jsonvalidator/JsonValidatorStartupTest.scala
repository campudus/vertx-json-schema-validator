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

}
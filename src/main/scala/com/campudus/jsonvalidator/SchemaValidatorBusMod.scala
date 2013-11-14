package com.campudus.jsonvalidator

import io.vertx.busmod.ScalaBusMod
import org.vertx.scala.core.eventbus.Message
import scala.concurrent.Future
import org.vertx.scala.core.json._
import org.vertx.java.core.json.JsonObject
import com.github.fge.jsonschema.main.JsonSchema
import org.vertx.scala.platform.Verticle

class SchemaValidatorBusMod(verticle: Verticle, schemas: Map[String, JsonSchema]) extends ScalaBusMod {
  val logger = verticle.logger
  val container = verticle.container
  val vertx = verticle.container

  override def asyncReceive(msg: Message[JsonObject]) = {
    case "validate" =>
      logger.info("got a reply")
      val report = Json.obj()
      Future.successful(Ok(report))
  }
}
package com.campudus.jsonvalidator

import io.vertx.busmod.ScalaBusMod
import org.vertx.scala.core.eventbus.Message
import scala.concurrent.Future
import org.vertx.scala.core.json._
import org.vertx.java.core.json.JsonObject
import com.github.fge.jsonschema.main.JsonSchema

class SchemaValidatorBusMod(schemas: Map[String, JsonSchema]) extends ScalaBusMod {

  override def asyncReceive(msg: Message[JsonObject]) = {
    case "validate" =>
      val report = Json.obj()
      Future.successful(Ok(report))
  }
}
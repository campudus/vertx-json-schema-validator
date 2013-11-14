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

  override def start(p: Promise[Unit]) = {
    import scala.collection.JavaConverters._
    val config = container.config
    val schemasConfig = config.getArray("schemas", Json.arr()).asScala

    val factory = JsonSchemaFactory.byDefault();
    val schemas: Map[String, JsonSchema] = Map(schemasConfig.zipWithIndex.toSeq.map {
      case (obj, idx) =>
        val schema = obj.asInstanceOf[JsonObject]
        val key = schema.getString("key", "schema" + idx)
        val jsNode = JsonLoader.fromString(schema.getObject("schema", Json.obj()).encode())
        val value = factory.getJsonSchema(jsNode)
        (key, value)
    }: _*)

    vertx.eventBus.registerHandler(config.getString("address", "campudus.jsonvalidator"), new SchemaValidatorBusMod(schemas), {
      case Success(_) =>
        logger.info("deployed")
        p.success()
      case Failure(ex) => p.failure(ex)
    }: Try[Void] => Unit)
        logger.info("starting async")
  }

}
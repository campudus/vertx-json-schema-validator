package io.vertx.busmod

import org.vertx.scala.core.json._

trait MessageHelper {
  sealed trait Reply {
    def toJson: JsonObject
  }
  case class Ok(x: JsonObject) extends Reply {
    def toJson = x.mergeIn(new JsonObject().putString("status", "ok"))
  }
  case class Error(message: String, id: Option[String] = None, obj: Option[JsonObject] = None) extends Reply {
    def toJson = {
      val js = Json.obj("status" -> "error", "message" -> message)
      id map (x => js.putString("error", x))
      obj map (x => js.mergeIn(x))
      js
    }
  }
}

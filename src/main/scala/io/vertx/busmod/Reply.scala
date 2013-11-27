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

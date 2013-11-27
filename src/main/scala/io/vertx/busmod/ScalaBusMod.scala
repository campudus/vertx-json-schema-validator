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

import scala.concurrent.Future

import org.vertx.scala.core.VertxExecutionContext
import org.vertx.scala.core.eventbus.{ JsonObjectData, Message }
import org.vertx.scala.core.json.{ Json, JsonObject }

trait ScalaBusMod extends MessageHelper with VertxExecutionContext with (Message[JsonObject] => Unit) {

  override def apply(msg: Message[JsonObject]) = {
    val action = msg.body.getString("action")

    val fut: Future[Reply] = try {
      if (receive(msg).isDefinedAt(action)) {
        val res = receive(msg).apply(action)
        Future.successful(res)
      } else if (asyncReceive(msg).isDefinedAt(action)) {
        asyncReceive(msg)(action)
      } else {
        Future.failed(new UnknownActionException("Unknown action: " + action))
      }
    } catch {
      // case ex: BusException => Future.failed(ex)
      case ex: Throwable =>
        Future.failed(ex)
    }

    fut map { reply =>
      msg.reply(reply.toJson)
    } recover {
      // case x: BusException => msg.reply(new JsonObject().putString("status", "error").putString("message", x.getMessage()).putString("id", x.getId()))
      case x =>
        x.printStackTrace(System.err)
        msg.reply(Json.obj("status" -> "error", "message" -> x.getMessage()))
    }
  }

  def receive(msg: Message[JsonObject]): PartialFunction[String, Reply] = Map.empty
  def asyncReceive(msg: Message[JsonObject]): PartialFunction[String, Future[Reply]] = Map.empty
}
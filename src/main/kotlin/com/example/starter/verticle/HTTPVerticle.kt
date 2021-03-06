package com.example.starter.verticle

import com.example.starter.controller.restRoute
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.*
import io.vertx.ext.web.handler.sockjs.SockJSHandler
import io.vertx.ext.web.handler.sockjs.SockJSHandlerOptions
import io.vertx.json.schema.SchemaParser
import io.vertx.json.schema.SchemaRouter
import io.vertx.kotlin.core.http.webSocketConnectOptionsOf
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.ext.web.handler.sockjs.sockJSBridgeOptionsOf
import io.vertx.kotlin.json.schema.schemaRouterOptionsOf

class HTTPVerticle : CoroutineVerticle() {
  private val logger = LoggerFactory.getLogger(this::class.java)

  override suspend fun start() {
    val schemaParser = SchemaParser.createDraft201909SchemaParser(
      SchemaRouter.create(vertx, schemaRouterOptionsOf())
    )
    val router = Router.router(vertx)

    router
      .route()
      .handler(HSTSHandler.create())
      .handler(CorsHandler.create())
      .handler(CSRFHandler.create(vertx, config.getString("CSRF_SECRET")))
      .handler(XFrameHandler.create(XFrameHandler.DENY))
      .handler(CSPHandler.create().addDirective("default-src", ""))
      .handler(BodyHandler.create().setBodyLimit(config.getLong("BODY_LIMIT")))
      .handler(ResponseContentTypeHandler.create())
      .handler(ResponseTimeHandler.create())
      .handler(LoggerHandler.create())
      .failureHandler { ctx ->
        val statusCode = ctx.statusCode()
        val message = ctx.failure().message
        val cause = ctx.failure().cause

        if (statusCode == 400) {
          logger.error("[业务异常]: $message, $cause")
          ctx.json(
            jsonObjectOf (
              "statusCode" to statusCode,
              "msg" to message,
              "data" to null
            )
          )
        }else {
          logger.error("[未知路由异常]: $message, $cause")
          ctx.json(
            jsonObjectOf(
              "statusCode" to 500,
              "msg" to "服务器错误",
              "data" to null
            )
          )
        }
      }

    router.mountSubRouter("/rest", restRoute(vertx, schemaParser))

    try {
      vertx
        .createHttpServer()
        .requestHandler(router)
        .listen(8000)
        .await()
      println("Http server is running on port 8000")
    } catch (e: Throwable) {
      val message = e.message
      val cause = Throwable(this::class.java.name)
      logger.error(message, cause)
      throw Error(message, cause)
    }

  }

}



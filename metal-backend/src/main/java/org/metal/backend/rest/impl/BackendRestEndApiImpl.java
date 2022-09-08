package org.metal.backend.rest.impl;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.time.LocalDateTime;
import org.metal.backend.api.BackendService;
import org.metal.backend.rest.IBackendRestEndApi;
import org.metal.backend.rest.SendJson;
import org.metal.exception.MetalAnalyseAcquireException;
import org.metal.exception.MetalAnalysedException;
import org.metal.exception.MetalDraftException;
import org.metal.exception.MetalExecuteException;
import org.metal.exception.MetalServiceException;
import org.metal.exception.MetalSpecParseException;

public class BackendRestEndApiImpl implements IBackendRestEndApi {
  private BackendService backendService;

  @Override
  public void analyseAPI(RoutingContext ctx) {
    JsonObject resp = new JsonObject();
    JsonObject body = ctx.body().asJsonObject();
    backendService.analyse(body)
        .onSuccess((JsonObject ret) -> {
          resp.put("status", "OK")
              .put("data", ret);
          SendJson.send(ctx, resp, 200);
        })
        .onFailure((Throwable error) -> {
          resp.put("status", "FAIL");
          if (error instanceof MetalSpecParseException) {
            resp.put("msg", error.getLocalizedMessage());
            SendJson.send(ctx, resp, 400);
            return;
          }

          if (error instanceof MetalDraftException) {
            resp.put("msg", error.getLocalizedMessage());
            SendJson.send(ctx, resp, 400);
            return;
          }

          if (error instanceof MetalAnalysedException) {
            resp.put("msg", error.getLocalizedMessage());
            SendJson.send(ctx, resp, 400);
            return;
          }

          if (error instanceof MetalAnalyseAcquireException) {
            resp.put("msg", error.getLocalizedMessage());
            SendJson.send(ctx, resp, 409);
            return;
          }

          resp.put("msg", error.getLocalizedMessage());
          SendJson.send(ctx, resp, 500);
        });
  }

  @Override
  public void schemaAPI(RoutingContext ctx) {
    String mid = ctx.pathParam("mid");
    JsonObject resp = new JsonObject();
    backendService.schemaAPI(mid)
        .onSuccess((JsonObject ret) -> {
          resp.put("status", "OK")
              .put("data", resp);
          SendJson.send(ctx, resp, 200);
        })
        .onFailure((Throwable error) -> {
          resp.put("status", "FAIL");
          if (error instanceof MetalServiceException) {
            resp.put("msg", error.getLocalizedMessage());
            SendJson.send(ctx, resp, 400);
            return;
          }

          resp.put("msg", error.getLocalizedMessage());
          SendJson.send(ctx, resp, 500);
        });
  }

  @Override
  public void heartAPI(RoutingContext ctx) {
    JsonObject resp = new JsonObject();
    backendService.heartAPI()
        .onSuccess((JsonObject ret) -> {
          resp.put("status", "OK")
              .put("data", ret);
          SendJson.send(ctx, resp, 200);
        })
        .onFailure((Throwable error) -> {
          resp.put("status", "FAIL")
              .put("msg", error.getLocalizedMessage());
          SendJson.send(ctx, resp, 500);
        });
  }

  @Override
  public void statusAPI(RoutingContext ctx) {
    JsonObject resp = new JsonObject();
    backendService.statusAPI()
        .onSuccess((JsonObject ret) -> {
          resp.put("status", "OK")
              .put("data", ret);
          SendJson.send(ctx, resp, 200);
        })
        .onFailure((Throwable error) -> {
          resp.put("status", "FAIL")
              .put("msg", error.getLocalizedMessage());
          SendJson.send(ctx, resp, 500);
        });
  }

  @Override
  public void execAPI(RoutingContext ctx) {

    backendService.execAPI()
        .onSuccess((JsonObject ret) -> {
          /***
           * Report Finish
           * TODO
           */
          JsonObject resp = new JsonObject();
          resp.put("status", "OK")
              .put("data", ret);
//          SendJson.send(ctx, resp, 200);

        })
        .onFailure((Throwable error) -> {
          /***
           * Report Exception
           * TODO
           */
          JsonObject resp = new JsonObject();
          resp.put("status", "FAIL");
          if (error instanceof MetalExecuteException) {
//            resp.put("msg", error.getLocalizedMessage());
//            SendJson.send(ctx, resp, 500);
            return;
          }

//          resp.put("msg", error.getLocalizedMessage());
//          SendJson.send(ctx, resp, 500);

        });
    JsonObject resp = new JsonObject();
    resp.put("status", "OK")
        .put("submitTime", LocalDateTime.now());
    SendJson.send(ctx, resp, 202);
  }
}
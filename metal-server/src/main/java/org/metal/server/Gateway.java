package org.metal.server;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.authorization.RoleBasedAuthorization;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthorizationHandler;
import io.vertx.ext.web.handler.BasicAuthHandler;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import org.metal.server.auth.AttachRoles;
import org.metal.server.auth.Auth;
import org.metal.server.auth.Roles;
import org.metal.server.project.Project;
import org.metal.server.repo.Repo;

public class Gateway extends AbstractVerticle {

  private final static Logger LOGGER = LoggerFactory.getLogger(Gateway.class);
  public static final String CONF_METAL_SERVER_PATH = "conf/metal-server.json";
  public static final String MONGO_CONF = "mongoConf";
  public static final String GATEWAY_CONF = "gateway";
  public static final String PROJECT_CONF = "project";
  public static final String PROJECT_SERVICE_CONF = "projectService";
  public static final String PROJECT_SERVICE_ADDRESS_CONF = "address";
  public static final String GATEWAY_PORT_CONF = "port";
  public static final String EXEC_SERVICE_CONF = "execService";
  public static final String BACKEND_REPORT_SERVICE_CONF = "backendReportService";

  private HttpServer httpServer;
  private int gatewayPort = 19000;
  private MongoClient mongo;
  private Auth auth;
  private Repo repo;
  private Project.RestApi project;

  private Gateway() {}

  public static Gateway create() {
    return new Gateway();
  }

  private Future<Router> createRestAPI(Router router) {
    router.post("/api/v1/users")
        .produces("application/json")
        .handler(BodyHandler.create())
        .handler(JWTAuthHandler.create(this.auth.getJwtAuth()))
        .handler(AuthorizationHandler.create(this.auth.adminAuthor()))
        .handler(this.auth::registerUser);

    router.post("/api/v1/tokens")
        .produces("application/json")
        .handler(BodyHandler.create())
        .handler(BasicAuthHandler.create(
            this.auth.getAuthenticationProvider()
        ))
        .handler(BodyJsonValid::valid)
        .handler(this.auth::createJWT);

    router.route("/api/v1/something")
        .produces("application/json")
        .handler(JWTAuthHandler.create(this.auth.getJwtAuth()))
        .handler(AttachRoles.create(mongo)::attach)
        .handler(this::something);

    repo.createRepoProxy(router, getVertx());
    router.post("/api/v1/repo/package")
        .handler(repo::deploy);

    router.post("/api/v1/projects")
        .produces("application/json")
        .handler(BodyHandler.create())
        .handler(JWTAuthHandler.create(this.auth.getJwtAuth()))
        .handler(project::add);

    router.get("/api/v1/projects/:projectName")
        .produces("application/json")
        .handler(BodyHandler.create())
        .handler(JWTAuthHandler.create(this.auth.getJwtAuth()))
        .handler(project::get);

    router.get("/api/v1/projects")
        .produces("application/json")
        .handler(BodyHandler.create())
        .handler(JWTAuthHandler.create(this.auth.getJwtAuth()))
        .handler(project::getAllOfUser);

    router.get("/api/v1/all_projects")
        .produces("application/json")
        .handler(BodyHandler.create())
        .handler(JWTAuthHandler.create(this.auth.getJwtAuth()))
        .handler(AuthorizationHandler.create(Auth.adminAuthor()))
        .handler(project::getAll);

    router.put("/api/v1/projects/:projectName")
        .produces("application/json")
        .handler(BodyHandler.create())
        .handler(JWTAuthHandler.create(this.auth.getJwtAuth()))
        .handler(project::updatePath);

    router.post("/api/v1/projects/:projectName")
        .produces("application/json")
        .handler(BodyHandler.create())
        .handler(JWTAuthHandler.create(this.auth.getJwtAuth()))
        .handler(project::deploy);

    router.delete("/api/v1/projects/:projectName")
        .produces("application/json")
        .handler(BodyHandler.create())
        .handler(JWTAuthHandler.create(this.auth.getJwtAuth()))
        .handler(project::remove);

    router.delete("/api/v1/projects")
        .produces("application/json")
        .handler(BodyHandler.create())
        .handler(JWTAuthHandler.create(this.auth.getJwtAuth()))
        .handler(project::removeAllOfUser);

    router.delete("/api/v1/all_projects")
        .produces("application/json")
        .handler(BodyHandler.create())
        .handler(JWTAuthHandler.create(this.auth.getJwtAuth()))
        .handler(AuthorizationHandler.create(Auth.adminAuthor()))
        .handler(project::removeAll);

    return Future.succeededFuture(router);
  }

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    ConfigStoreOptions fileConfigStoreOptions = new ConfigStoreOptions()
        .setType("file")
        .setConfig(new JsonObject().put("path", CONF_METAL_SERVER_PATH))
        .setOptional(true);

    ConfigRetrieverOptions retrieverOptions = new ConfigRetrieverOptions()
        .addStore(fileConfigStoreOptions);
    ConfigRetriever retriever = ConfigRetriever.create(getVertx(), retrieverOptions);
    retriever.getConfig().compose((JsonObject conf) -> {
      JsonObject gatewayConf = conf.getJsonObject(GATEWAY_CONF);
      JsonObject mongoConf = conf.getJsonObject(MONGO_CONF);
      JsonObject projectService = gatewayConf.getJsonObject(PROJECT_SERVICE_CONF);
      JsonObject execService = gatewayConf.getJsonObject(EXEC_SERVICE_CONF);
      JsonObject backendReportService = gatewayConf.getJsonObject(BACKEND_REPORT_SERVICE_CONF);

      String projectServiceAddress = projectService.getString(PROJECT_SERVICE_ADDRESS_CONF);
      if (gatewayConf == null) {
        return Future.failedFuture(String.format("%s is not configured in %s.", GATEWAY_CONF, CONF_METAL_SERVER_PATH));
      }
      if (mongoConf == null) {
        return Future.failedFuture(String.format("%s is not configured in %s.", MONGO_CONF, CONF_METAL_SERVER_PATH));
      }
      if (projectService == null) {
        return Future.failedFuture(String.format("%s is not configured in %s.", PROJECT_CONF, CONF_METAL_SERVER_PATH));
      }
      if (projectServiceAddress == null || projectServiceAddress.isBlank()) {
        return Future.failedFuture(String.format("%s is not configured in %s.", PROJECT_SERVICE_ADDRESS_CONF, CONF_METAL_SERVER_PATH + "." + PROJECT_CONF));
      }
      try {
        if (gatewayConf.getInteger(GATEWAY_PORT_CONF) == null) {
          return Future.failedFuture(String.format("%s is not configured in %s.", GATEWAY_PORT_CONF, CONF_METAL_SERVER_PATH + "." + GATEWAY_CONF));
        }
      } catch (ClassCastException e) {
        return Future.failedFuture(e);
      }

      gatewayPort = gatewayConf.getInteger(GATEWAY_PORT_CONF);
      mongo = MongoClient.createShared(getVertx(), mongoConf);

      httpServer = getVertx().createHttpServer();
      repo = new Repo();
      project = Project.createRestApi(getVertx(), projectServiceAddress);
      return Auth.create(mongo);
    }).compose((Auth auth) -> {
      this.auth = auth;
      return Future.succeededFuture(auth);
    }).compose((Auth auth) -> {
      Router router = Router.router(getVertx());
      return Future.succeededFuture(router);
    }).compose(this::createRestAPI)
      .compose((Router router) -> {
        httpServer.requestHandler(router);
        return httpServer.listen(gatewayPort);
      }).onSuccess(srv -> {
        LOGGER.info(String.format("Success to start Server[%s] on port[%d].", Gateway.class,
              srv.actualPort()));
        startPromise.complete();
      }).onFailure(error -> {
        LOGGER.error(error);
        startPromise.fail(error);
      });
  }

  @Override
  public void stop(Promise<Void> stopPromise) throws Exception {
    httpServer.close()
        .compose(ret -> {
          return mongo.close();
        }, error -> {
          LOGGER.error(
              String.format("Fail to stop Server[%s] on port[%d].", Gateway.class, gatewayPort),
              error);
          return mongo.close();
        })
        .onSuccess(ret -> {
          LOGGER.info(String.format("Success to close mongoDB connection."));
        })
        .onFailure(error -> {
          LOGGER.info(String.format("Fail to close mongoDB connection."), error);
        });
  }

  private void something(RoutingContext ctx) {
    String author = ctx.user().authorizations().getProviderIds().toString();
    boolean isRole = RoleBasedAuthorization.create(Roles.USER.toString()).match(ctx.user());
    ctx.response().end(ctx.user().get("username").toString() + ":" + isRole + ":" + author);
  }
}
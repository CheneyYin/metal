package org.metal.server.project.service;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.uritemplate.UriTemplate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.metal.server.api.BackendState;
import org.metal.server.project.Platform;
import org.metal.server.project.ProjectDB;
import org.metal.server.util.JsonConvertor;
import org.metal.server.util.SpecJson;

public class ProjectServiceImpl implements IProjectService{
  private final static Logger LOGGER = LoggerFactory.getLogger(ProjectServiceImpl.class);

  private MongoClient mongo;
  private Vertx vertx;
  private WorkerExecutor workerExecutor;
  private JsonObject conf;

  public ProjectServiceImpl(Vertx vertx, MongoClient mongo, WorkerExecutor workerExecutor, JsonObject conf) {
    this.vertx = vertx;
    this.mongo = mongo;
    this.conf = conf.copy();
    this.workerExecutor = workerExecutor;
  }

  @Override
  public Future<String> createEmptyProject(String userId, String name) {
    return createProject(userId, name, null, null, null, null);
  }

  @Override
  public Future<String> createProject(
      String userId,
      String name,
      List<String> pkgs,
      JsonObject platform,
      List<String> backendArgs,
      JsonObject spec) {
    if (pkgs == null) {
      pkgs = new ArrayList<>();
    }
    if (backendArgs == null) {
      backendArgs = new ArrayList<>();
    }
    if (spec == null || spec.isEmpty()) {
      spec = SpecJson.empty();
    }
    if (platform == null || platform.isEmpty()) {
      LOGGER.info("Platform is not set and will be replaced with the default platform.");
      JsonObject defaultPlatform = conf.getJsonObject("platform");
      if (defaultPlatform == null || defaultPlatform.isEmpty()) {
        LOGGER.error("Fail to load default platform.");
        return Future.failedFuture("Fail to load default platform.");
      } else {
        platform = defaultPlatform;
      }
    }

    return ProjectDBEx.add(
        mongo,
        userId,
        name,
        pkgs,
        platform,
        backendArgs,
        spec
    );
  }

  @Override
  public Future<String> createProjectFrom(String userId, String name) {
    return ProjectDBEx.copyFromProject(mongo, userId, name);
  }

  @Override
  public Future<String> createProjectFromWithCopyName(String userId, String name, String copyName) {
    return ProjectDBEx.copyFromProject(mongo, userId, name, copyName);
  }

  @Override
  public Future<String> createProjectFromExec(String userId, String execId) {
    return ProjectDB.recoverFrom(mongo, userId, execId);
  }

  @Override
  public Future<JsonObject> updateName(String userId, String name, String newName) {
    return ProjectDBEx.updateName(mongo, userId, name, newName);
  }


  @Override
  public Future<JsonObject> updateSpec(String userId, String projectName, JsonObject spec) {
    return ProjectDBEx.updateSpec(mongo, userId, projectName, spec);
  }

  @Override
  public Future<JsonObject> updatePlatform(String deployId, JsonObject platform) {
    return ProjectDBEx.updatePlatform(mongo, deployId, platform);
  }

  @Override
  public Future<JsonObject> updateBackendArgs(String deployId, List<String> backendArgs) {
    return ProjectDBEx.updateBackendArgs(mongo, deployId, backendArgs);
  }

  @Override
  public Future<JsonObject> updatePkgs(String deployId, List<String> pkgs) {
    return ProjectDBEx.updatePkgs(mongo, deployId, pkgs);
  }

  @Override
  public Future<JsonObject> updateDeployConfsByPath(String deployId, JsonObject updateConfs) {
    return ProjectDBEx.updateDeployConfs(mongo, deployId, updateConfs);
  }

  @Override
  public Future<JsonObject> updateBackendStatus(String deployId, JsonObject updateStatus) {
    return ProjectDBEx.updateBackendStatus(mongo, deployId, updateStatus);
  }

  @Override
  public Future<JsonObject> updateBackendStatusOnUndeploy(String deployId) {
    return ProjectDBEx.updateBackendStatusOnUndeploy(mongo, deployId);
  }

  @Override
  public Future<JsonObject> updateBackendStatusOnUp(String deployId) {
    return ProjectDBEx.updateBackendStatusOnUp(mongo, deployId);
  }

  @Override
  public Future<JsonObject> updateBackendStatusOnDown(String deployId) {
    return ProjectDBEx.updateBackendStatusOnDown(mongo, deployId);
  }

  @Override
  public Future<JsonObject> updateBackendStatusOnFailure(String deployId, String failureMsg) {
    return ProjectDBEx.updateBackendStatusOnFailure(mongo, deployId, failureMsg);
  }

  @Override
  public Future<JsonObject> getOfId(String userId, String projectId) {
    return ProjectDBEx.getOfId(mongo, userId, projectId);
  }

  @Override
  public Future<JsonObject> getOfName(String userId, String projectName) {
    return ProjectDBEx.getOfName(mongo, userId, projectName);
  }

  @Override
  public Future<JsonObject> getBackendStatusOfDeployId(String deployId) {
    return ProjectDBEx.getBackendStatus(mongo, deployId);
  }

  @Override
  public Future<List<JsonObject>> getAllOfUser(String userId) {
    return ProjectDBEx.getAllOfUser(mongo, userId);
  }

  @Override
  public Future<List<JsonObject>> getAll() {
    return ProjectDBEx.getAll(mongo);
  }

  @Override
  public Future<JsonObject> removeOfId(String userId, String id) {
    return ProjectDBEx.removeOfId(mongo, userId, id);
  }

  @Override
  public Future<JsonObject> removeOfName(String userId, String name) {
    return ProjectDBEx.removeOfName(mongo, userId, name);
  }

  @Override
  public Future<JsonObject> removeAllOfUser(String userId) {
    return ProjectDBEx.removeAllOfUser(mongo, userId);
  }

  @Override
  public Future<JsonObject> removeAll() {
    return ProjectDBEx.removeAll(mongo);
  }

  @Override
  public Future<JsonObject> deploy(String userId, String name) {
    return getOfName(userId, name).compose((JsonObject project) -> {
      JsonObject deploy = project.getJsonObject(ProjectDBEx.DEPLOY);
      JsonObject backend = deploy.getJsonObject(ProjectDBEx.DEPLOY_BACKEND);
      JsonObject backendStatus = backend.getJsonObject(ProjectDBEx.DEPLOY_BACKEND_STATUS);
      if (backendStatus != null && !backendStatus.isEmpty()) {
        return Future.failedFuture("One backend has been deployed. User can\'t deploy the other new backend before that the deployed backend is cleaned.");
      }

      String deployId = deploy.getString(ProjectDBEx.DEPLOY_ID);
      int epoch = deploy.getInteger(ProjectDBEx.DEPLOY_EPOCH);
      List<String> pkgs = JsonConvertor.jsonArrayToList(deploy.getJsonArray(ProjectDBEx.DEPLOY_PKGS));
      JsonObject platform = deploy.getJsonObject(ProjectDBEx.DEPLOY_PLATFORM);
      List<String> backendArgs = JsonConvertor.jsonArrayToList(backend.getJsonArray(ProjectDBEx.DEPLOY_BACKEND_ARGS));

      backendArgs = antiInject(backendArgs);
      String reportServiceAddress = conf.getJsonObject("backendReportService").getString("address");
      List<String> defaultBackendArgs = List.of(
          "--interactive-mode",
          "--deploy-id", deployId,
          "--deploy-epoch", String.valueOf(epoch),
          "--report-service-address", reportServiceAddress,
          "--rest-api-port", String.valueOf(18000)
      );

      List<String> appArgs = new ArrayList<>();
      appArgs.addAll(defaultBackendArgs);
      appArgs.addAll(backendArgs);

      if (platform.fieldNames().contains("spark.standalone")) {
        JsonObject sparkStandalone = platform.getJsonObject("spark.standalone");
        JsonObject restApi = sparkStandalone.getJsonObject("rest.api");
        JsonObject conf = sparkStandalone.getJsonObject("conf");
        conf.put("appArgs", appArgs);

        WebClientOptions options = new WebClientOptions();
        String restApiHost = restApi.getString("host");
        int restApiPort = restApi.getInteger("port");
        WebClient webClient = WebClient.create(vertx);
        UriTemplate createURI = UriTemplate.of(restApi.getJsonObject("requestURI").getString("create"));

        return ProjectDBEx.updateBackendStatusOnUndeploy(mongo, deployId).compose(ret -> {
          return webClient.post(restApiPort, restApiHost, createURI)
              .sendJsonObject(conf);
        }).compose((HttpResponse<Buffer> response) -> {
          JsonObject resp = response.bodyAsJsonObject();
          boolean isSuccess = resp.getBoolean("success");
          if (isSuccess) {
            String driverId = resp.getString("submissionId");
            JsonObject tracer = new JsonObject()
                .put("driverId", driverId);
            return ProjectDBEx.updateBackendStatusTracer(mongo, deployId, tracer);
          }
          return Future.failedFuture(resp.toString());
        });
      }

      return Future.failedFuture("Fail to found any legal platform configuration.");
    });
  }


  private static List<String> antiInject(List<String> backendArgs) {
    List<String> ret = new ArrayList<>();
    for(int idx = 0; idx < backendArgs.size(); idx++) {
      String arg = backendArgs.get(idx).strip();
      if (arg.equals("--interactive-mode") || arg.equals("--cmd-mode")) {
        continue;
      }

      if (arg.equals("--deploy-id")) {
        idx++;
        continue;
      }

      if (arg.equals("--deploy-epoch")) {
        idx++;
        continue;
      }

      if (arg.equals("--report-service-address")) {
        idx++;
        continue;
      }

      ret.add(arg);
    }

   return Collections.unmodifiableList(ret);
  }

  public Future<JsonObject> deployed(String userId, String name) {
    return getOfName(userId, name).compose((JsonObject project) -> {
      String deployId = project.getString(ProjectDB.FIELD_DEPLOY_ID);
      if (deployId == null || deployId.strip().isEmpty()) {
        return Future.failedFuture("deployId is not set.");
      }

      JsonObject deployArgs = project.getJsonObject(ProjectDB.FIELD_DEPLOY_ARGS);
      if (deployArgs == null || deployArgs.isEmpty()) {
        return Future.failedFuture("deployArgs is not set.");
      }

      String platform = deployArgs.getString(ProjectDB.FIELD_DEPLOY_ARGS_PLATFORM);
      if (platform == null) {
        return Future.failedFuture("platform is not set.");
      }
      try {
        Platform.valueOf(platform);
      } catch (IllegalArgumentException e) {
        return Future.failedFuture(e);
      }

      JsonObject platformArgs = deployArgs.getJsonObject(ProjectDB.FIELD_DEPLOY_ARGS_PLATFORM_ARGS);
      JsonArray platformArgsArgs = platformArgs.getJsonArray(ProjectDB.FIELD_DEPLOY_ARGS_PLATFORM_ARGS_ARGS);
      JsonArray plataformArgsPkgs = platformArgs.getJsonArray(ProjectDB.FIELD_DEPLOY_ARGS_PLATFORM_ARGS_PKGS, new JsonArray());
      if (platformArgs == null || platformArgs.isEmpty()) {
        return Future.failedFuture("platformArgs is not set.");
      }
      if (platformArgsArgs == null) {
        return Future.failedFuture("platformArgs.args is not set.");
      }

      List<String> parseArgs = new ArrayList<>();
      switch (Platform.valueOf(platform)) {
        case SPARK: {
          try {
            parseArgs.addAll(parsePlatformArgs(platformArgsArgs, plataformArgsPkgs));
          } catch (IllegalArgumentException e) {
            return Future.failedFuture(e);
          }
        }; break;
        default: {
          return Future.failedFuture(String.format("%s is not supported.", platform));
        }
      }

      String backendJar = conf.getString("backendJar");
      if (backendJar == null || backendJar.strip().isEmpty()) {
        LOGGER.error("backendJar is not configured.");
        return Future.failedFuture("backendJar is not configured.");
      }
      parseArgs.add(backendJar);

      JsonObject backendArgs = deployArgs.getJsonObject(ProjectDB.FIELD_DEPLOY_ARGS_BACKEND_ARGS);
      if (backendArgs == null || backendArgs.isEmpty()) {
        return Future.failedFuture("backendArgs is not set.");
      }

      JsonArray backendArgsArgs = backendArgs.getJsonArray(ProjectDB.FIELD_DEPLOY_ARGS_BACKEND_ARGS_ARGS);
      try {
        parseArgs.addAll(parseBackendArgs(backendArgsArgs));
      } catch (IllegalArgumentException e) {
        return Future.failedFuture(e);
      }

      JsonObject backendStatus = project.getJsonObject(ProjectDB.FIELD_BACKEND_STATUS);
      if (backendStatus == null || backendStatus.isEmpty()) {
        return Future.failedFuture("backendStatus is not set.");
      }

      String backendStatusStatus = backendStatus.getString(ProjectDB.FIELD_BACKEND_STATUS_STATUS);
      if (backendStatusStatus == null || backendStatusStatus.isBlank()) {
        return Future.failedFuture("backendStatus.status is not set.");
      }
      try {
        BackendState.valueOf(backendStatusStatus);
      } catch (IllegalArgumentException e) {
        return Future.failedFuture(e);
      }
      if (!BackendState.valueOf(backendStatusStatus).equals(BackendState.UN_DEPLOY)) {
        return Future.failedFuture(String.format("Fail to deploy backend, because backend is in %s.", backendStatusStatus));
      }

      int epoch = backendStatus.getInteger(ProjectDB.FIELD_BACKEND_STATUS_EPOCH, ProjectDB.DEFAULT_EPOCH);
      if (epoch != -1) {
        String errorMsg = "The epoch of undeploy backend should be -1, now is " + epoch;
        LOGGER.error(errorMsg);
        return Future.failedFuture(errorMsg);
      }
      parseArgs.add("--interactive-mode ");
      parseArgs.add("--deploy-id");
      parseArgs.add(deployId);
      parseArgs.add("--deploy-epoch");
      parseArgs.add(String.valueOf(epoch));

      String reportServiceAddress = conf.getJsonObject("backendReportService").getString("address");
      parseArgs.add("--report-service-address");
      parseArgs.add(reportServiceAddress);
      parseArgs.add("--rest-api-port");
      parseArgs.add("18989");

      System.out.println(parseArgs);
      switch (Platform.valueOf(platform)) {
        case SPARK: {
//          String deployer = "org.metal.backend.spark.SparkBackendDeploy";
//          Optional<IBackendDeploy> backendDeploy = BackendDeployManager.getBackendDeploy(deployer);
//          if (backendDeploy.isEmpty()) {
//            return Future.failedFuture(String.format("Fail to create IBackendDeploy[%s] instance.", deployer));
//          }
//          return workerExecutor.executeBlocking((promise)->{
//            try {
//              backendDeploy.get().deploy(parseArgs.<String>toArray(String[]::new));
//              promise.complete();
//            } catch (Exception e) {
//              promise.fail(e);
//            }
//          }, true);
        }
        default: {
          return Future.failedFuture(String.format("%s is not supported.", platform));
        }
      }
    });
  }

  private List<String> parsePlatformArgs(JsonArray platformArgs, JsonArray platformPkgs) throws IllegalArgumentException{
    List<String> args = platformArgs.stream().map(Object::toString).collect(Collectors.toList());
    boolean classArgReady = false;
    try {
      for(int idx = 0; idx < args.size(); idx++) {
        String arg = args.get(idx);
        if ("--class".equals(arg)) {
          String classArg = args.get(idx + 1);
//          if (BackendLauncher.class.toString().equals(classArg)) {
//            classArgReady = true;
//          } else {
//            throw new IllegalArgumentException("platformArgs.args --class is set wrong.");
//          }
        }
      }
    } catch (IndexOutOfBoundsException e) {
      throw new IllegalArgumentException(e);
    }

//    if (!classArgReady) {
//      args.add("--class");
//      args.add(BackendLauncher.class.toString());
//    }

    String packagesArg = platformPkgs.stream().map(Object::toString).collect(Collectors.joining(","));
    if (!platformPkgs.isEmpty()) {
      args.add("--packages");
      args.add(packagesArg);
    }
    return args;
  }

  private List<String> parseBackendArgs(JsonArray backendArgs) throws IllegalArgumentException{
    List<String> args = backendArgs.stream().map(Object::toString).collect(Collectors.toList());

    boolean interactiveReady = false;
    for(int idx = 0; idx < args.size(); idx++) {
      String arg = args.get(idx);
      if ("--interactive-mode".equals(arg)) {
        interactiveReady = true;
      }
    }

    if (!interactiveReady){
      args.add("--interactive-mode");
    }

    return args;
  }
}

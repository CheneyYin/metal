package org.metal.server.project.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import java.util.List;
import java.util.UUID;
import org.metal.server.api.BackendState;
import org.metal.server.user.UserDB;
import org.metal.server.util.JsonKeyReplacer;
import org.metal.server.util.ReadStreamCollector;

public class ProjectDBEx {
  public final static String DB = "project";
  public static final String NAME = "name";
  public static final String CREATE_TIME = "createTime";
  public static final String DEPLOY = "deploy";
  public static final String DEPLOY_ID = "id";
  public static final String DEPLOY_PLATFORM = "platform";
  public static final String DEPLOY_BACKEND = "backend";
  public static final String SPEC = "spec";
  public static final String DEPLOY_BACKEND_STATUS = "status";
  public static final String DEPLOY_BACKEND_STATUS_CURRENT = "current";
  public static final String DEPLOY_BACKEND_ARGS = "args";
  public static final String DEPLOY_EPOCH = "epoch";
  public static final String DEPLOY_PKGS = "pkgs";
  public static final int DEPLOY_EPOCH_DEFAULT = 0;
  public static final String USER_REF = "userRef";
  public static final String USER_REF_REF = "$ref";
  public static final String USER_REF_ID = "$id";
  public static final String ID = "_id";
  public static final String USER = "user";

  public static Future<String> add(
      MongoClient mongo,
      String userId,
      String name,
      List<String> pkgs,
      JsonObject platform,
      List<String> backendArgs,
      JsonObject spec
      ) {
    JsonObject project = new JsonObject();
    JsonObject userRef = new JsonObject();
    JsonObject deploy = new JsonObject();
    JsonObject backend = new JsonObject();

    if (platform != null) {
      platform = JsonKeyReplacer.compatBson(platform);
    }

    userRef.put(USER_REF_REF, UserDB.DB).put(USER_REF_ID, userId);
    project.put(USER_REF, userRef);
    project.put(NAME, name);
    project.put(CREATE_TIME, System.currentTimeMillis());
    project.put(DEPLOY, deploy);
    deploy.put(DEPLOY_ID, UUID.randomUUID().toString());
    deploy.put(DEPLOY_EPOCH, DEPLOY_EPOCH_DEFAULT);
    deploy.put(DEPLOY_PKGS, pkgs);
    deploy.put(DEPLOY_PLATFORM, platform);
    deploy.put(DEPLOY_BACKEND, backend);
    backend.put(DEPLOY_BACKEND_ARGS, backendArgs);
//    When deploy one backend, backend status will be created. When redeploy or close down backend, backend status will be removed.
//    backend.put(DEPLOY_BACKEND_STATUS,
//        new JsonObject().put(DEPLOY_BACKEND_STATUS_CURRENT,BackendState.UN_DEPLOY.toString())
//    );
    project.put(SPEC, spec);

    return mongo.insert(DB, project);
  }

  public static Future<List<JsonObject>> getAllOfMatcher(MongoClient mongo, JsonObject matcher) {
    JsonObject match = new JsonObject();
    JsonObject lookup = new JsonObject();
    JsonObject project = new JsonObject();
    JsonObject privateProtect = new JsonObject();

    match.put("$match", matcher);

    lookup.put("$lookup",
        new JsonObject()
            .put("from", UserDB.DB)
            .put("localField", userIdPath())
            .put("foreignField", UserDB.FIELD_ID)
            .put("as",USER)
    );

    project.put("$project",
        new JsonObject()
            .put(ID, true)
            .put(NAME, true)
            .put(SPEC, true)
            .put(DEPLOY, true)
            .put(USER, new JsonObject()
                .put(
                    "$arrayElemAt",
                    new JsonArray().add("$" + USER).add(0)
                )
            )
    );

    privateProtect = project.copy();
    privateProtect.getJsonObject("$project")
        .put(USER, new JsonObject()
            .put(UserDB.FIELD_ID, true)
            .put(UserDB.FIELD_USER_NAME, true)
        );

    JsonArray pipeline = new JsonArray()
        .add(match)
        .add(lookup)
        .add(project)
        .add(privateProtect);
    return ReadStreamCollector.<JsonObject>toList(
        mongo.aggregate(DB, pipeline).handler(ProjectDBEx::compatJsonOnPlatform)
    );
  }

  private static JsonObject compatJsonOnPlatform(JsonObject proj) {
    JsonObject platform = proj.getJsonObject(DEPLOY).getJsonObject(DEPLOY_PLATFORM);
    platform = JsonKeyReplacer.compatJson(platform);
    proj.getJsonObject(DEPLOY).put(DEPLOY_PLATFORM, platform);
    return proj;
  }

  public static Future<JsonObject> getOfMatcher(MongoClient mongo, JsonObject matcher) {
    return getAllOfMatcher(mongo, matcher).compose(projects -> {
      try {
        return Future.succeededFuture(projects.get(0));
      } catch (IndexOutOfBoundsException e) {
        return Future.succeededFuture(new JsonObject());
      }
    });
  }

  public static Future<JsonObject> getOfId(MongoClient mongo, String userId, String projectId) {
    JsonObject matcher = new JsonObject();
    matcher.put(ID, projectId)
           .put(userIdPath(), userId);
    return getOfMatcher(mongo, matcher);
  }

  public static Future<JsonObject> getOfName(MongoClient mongo, String userId, String projectName) {
    JsonObject matcher = new JsonObject();
    matcher.put(NAME, projectName)
        .put(userIdPath(), userId);
    return getOfMatcher(mongo, matcher);
  }

  public static Future<List<JsonObject>> getAllOfUser(MongoClient mongo, String userId) {
    JsonObject matcher = new JsonObject();
    matcher.put(userIdPath(), userId);
    return getAllOfMatcher(mongo, matcher);
  }

  public static Future<List<JsonObject>> getAll(MongoClient mongo) {
    return getAllOfMatcher(mongo, new JsonObject());
  }

  public static Future<JsonObject> getBackendStatus(MongoClient mongo, String deployId) {
    JsonObject matcher = new JsonObject();
    matcher.put(deployIdPath(), deployId);
    return mongo.findOne(DB, matcher, new JsonObject())
        .compose(proj -> {
          JsonObject deploy = proj.getJsonObject(DEPLOY);
          JsonObject status = deploy.getJsonObject(DEPLOY_BACKEND).getJsonObject(DEPLOY_BACKEND_STATUS);
          status.put("deployId", deploy.getValue(DEPLOY_ID));
          status.put("epoch", deploy.getValue(DEPLOY_EPOCH));
          return Future.succeededFuture(status);
        });
  }

  public static Future<JsonObject> update(MongoClient mongo, JsonObject matcher, JsonObject updater) {
    return mongo.updateCollection(DB, matcher, updater)
        .compose(result -> {
          return Future.succeededFuture(result.toJson());
        });
  }
  public static Future<JsonObject> update(MongoClient mongo, String userId, String name, JsonObject updater) {
    JsonObject matcher = new JsonObject();
    matcher.put(NAME, name)
        .put(userIdPath(), userId);

    return update(mongo, matcher, updater);
  }

  public static Future<JsonObject> updateName(MongoClient mongo, String userId, String name, String newName) {
    JsonObject updater = new JsonObject();
    updater.put("$set", new JsonObject().put(NAME, newName));
    return update(mongo, userId, name, updater);
  }

  public static Future<JsonObject> updateSpec(MongoClient mongo, String userId, String name, JsonObject spec) {
    JsonObject updater = new JsonObject();
    updater.put("$set", new JsonObject().put(SPEC, spec));
    return update(mongo, userId, name, updater);
  }

  public static Future<JsonObject> updatePlatform(MongoClient mongo, String userId, String name, JsonObject platform) {
    JsonObject updater = new JsonObject();
    updater.put("$set", new JsonObject().put(platformPath(), platform));
    return update(mongo, userId, name, updater);
  }

  public static Future<JsonObject> updatePlatform(MongoClient mongo, String deployId, JsonObject platform) {
    JsonObject matcher = new JsonObject();
    matcher.put(deployIdPath(), deployId);
    JsonObject updater = new JsonObject();
    updater.put("$set", new JsonObject().put(platformPath(), platform));
    return update(mongo, matcher, updater);
  }

  public static Future<JsonObject> updatePkgs(MongoClient mongo, String userId, String name, List<String> pkgs) {
    JsonObject updater = new JsonObject();
    updater.put("$set", new JsonObject().put(pkgsPath(), pkgs));
    return update(mongo, userId, name, updater);
  }

  public static Future<JsonObject> updatePkgs(MongoClient mongo, String deployId, List<String> pkgs) {
    JsonObject matcher = new JsonObject();
    matcher.put(deployIdPath(), deployId);
    JsonObject updater = new JsonObject();
    updater.put("$set", new JsonObject().put(pkgsPath(), pkgs));
    return update(mongo, matcher, updater);
  }

  public static Future<JsonObject> updateBackendArgs(MongoClient mongo, String userId, String name, List<String> backendArgs) {
    JsonObject updater = new JsonObject();
    updater.put("$set", new JsonObject().put(backendArgsPath(), backendArgs));
    return update(mongo, userId, name, updater);
  }

  public static Future<JsonObject> updateBackendArgs(MongoClient mongo, String deployId, List<String> backendArgs) {
    JsonObject matcher = new JsonObject();
    matcher.put(deployIdPath(), deployId);
    JsonObject updater = new JsonObject();
    updater.put("$set", new JsonObject().put(backendArgsPath(), backendArgs));
    return update(mongo, matcher, updater);
  }

  public static Future<JsonObject> updateBackendStatus(MongoClient mongo, String deployId, JsonObject status) {
    JsonObject matcher = new JsonObject();
    matcher.put(deployIdPath(), deployId);
    JsonObject updater = new JsonObject();
    updater.put("$set", new JsonObject().put(backendStatusPath(), status));
    return update(mongo, matcher, updater);
  }

  public static Future<JsonObject> updateDeployConfs(MongoClient mongo, String userId, String name, JsonObject confs) {
    JsonObject updater = new JsonObject();
    JsonObject confsWithPath = deployConfsWithPath(confs);
    updater.put("$set", confsWithPath);
    return update(mongo, userId, name, updater);
  }

  public static Future<JsonObject> updateDeployConfs(MongoClient mongo, String deployId, JsonObject confs) {
    JsonObject matcher = new JsonObject();
    matcher.put(deployIdPath(), deployId);
    JsonObject updater = new JsonObject();
    JsonObject confsWithPath = deployConfsWithPath(confs);
    updater.put("$set", confsWithPath);
    return update(mongo, matcher, updater);
  }

  public static Future<JsonObject> removeOfId(MongoClient mongo, String userId, String id) {
    JsonObject matcher = new JsonObject();
    matcher.put(userIdPath(), userId)
        .put(ID, id);
    return remove(mongo, matcher);
  }
  public static Future<JsonObject> removeOfName(MongoClient mongo, String userId, String name) {
    JsonObject matcher = new JsonObject();
    matcher.put(userIdPath(), userId)
        .put(NAME, name);
    return remove(mongo, matcher);
  }

  public static Future<JsonObject> removeAllOfUser(MongoClient mongo, String userId) {
    JsonObject matcher = new JsonObject();
    matcher.put(userIdPath(), userId);
    return remove(mongo, matcher);
  }

  public static Future<JsonObject> removeAll(MongoClient mongo) {
    JsonObject matcher = new JsonObject();
    return remove(mongo, matcher);
  }

  public static Future<JsonObject> remove(MongoClient mongo, JsonObject matcher) {
    return mongo.removeDocuments(DB, matcher).compose(result -> {
      return Future.succeededFuture(result.toJson());
    });
  }

  private static JsonObject deployConfsWithPath(JsonObject confs) {
    JsonObject confsWithPath = new JsonObject();
    for (String field: confs.fieldNames()) {
      confsWithPath.put(DEPLOY + "." + field, confs.getValue(field));
    }
    return confsWithPath;
  }


  private static String userIdPath() {
    return USER_REF + "." + USER_REF_ID;
  }

  private static String deployIdPath() {
    return DEPLOY + "." + DEPLOY_ID;
  }

  private static String platformPath() {
    return DEPLOY + "." + DEPLOY_PLATFORM;
  }

  private static String backendArgsPath() {
    return DEPLOY + "." + DEPLOY_BACKEND + "." +DEPLOY_BACKEND_ARGS;
  }

  private static String backendStatusPath() {
    return DEPLOY + "." + DEPLOY_BACKEND + "." +DEPLOY_BACKEND_STATUS;
  }

  private static String pkgsPath() {
    return DEPLOY + "." + DEPLOY_PLATFORM;
  }


}

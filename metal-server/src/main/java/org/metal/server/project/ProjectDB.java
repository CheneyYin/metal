package org.metal.server.project;

import io.vertx.core.Future;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.ReadStream;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.MongoClientDeleteResult;
import java.util.Optional;
import java.util.UUID;
import org.metal.server.exec.ExecDB;
import org.metal.server.user.UserDB;

public class ProjectDB {
  private final static Logger LOGGER = LoggerFactory.getLogger(ProjectDB.class);

  public final static String DB = "projects";
  public final static String FIELD_ID = "_id";
  public final static String FIELD_NAME = "name";
  public final static String FIELD_USER_REF = "userRef";
  public final static String FIELD_USER_REF_REF = "$ref";
  public final static String FIELD_USER_REF_ID = "$id";
  public final static String FIELD_USER_INFO = "userInfo";
  public final static String FIELD_USER_INFO_ID = "_id";
  public final static String FIELD_USER_INFO_NAME = "username";
  public final static String FIELD_CREATE_TIME = "createTime";
  public final static String FIELD_DEPLOY_ID= "deployId";
  public final static String FIELD_DEPLOY_ARGS = "deployArgs";
  public final static String FIELD_DEPLOY_ARGS_PLATFORM = "platform";
  public final static String FIELD_DEPLOY_ARGS_PLATFORM_ARGS = "platformArgs";
  public final static String FIELD_DEPLOY_ARGS_BACKEND_ARGS = "backendArgs";
  public final static String FIELD_BACKEND_STATUS = "backendStatus";
  public final static String FIELD_BACKEND_STATUS_STATUS = "status";
  public final static String FIELD_BACKEND_STATUS_CREATE_TIME = "createTime";
  public final static String FIELD_BACKEND_STATUS_UP_TIME = "upTime";
  public final static String FIELD_BACKEND_STATUS_REPORT_TIME = "reportTime";
  public final static String FIELD_BACKEND_STATUS_DOWN_TIME = "downTime";
  public final static String FIELD_SPEC = "spec";

  public static Future<Void> createCollection(MongoClient mongo) {
    return mongo.createCollection(DB)
        .compose(ret -> {
          return mongo.createIndexWithOptions(
              DB,
              new JsonObject()
                  .put(FIELD_USER_REF, new JsonObject().put(FIELD_USER_REF_ID, true))
                  .put(FIELD_NAME, true),
              new IndexOptions().unique(true)
          );
        });
  }

  public static Future<String> add(
      MongoClient mongo,
      String userId,
      String projectName,
      Platform platform, JsonObject platformArgs, JsonObject backendArgs,
      JsonObject spec
      ) {
    JsonObject project = new JsonObject();
    project.put(FIELD_USER_REF, new JsonObject()
        .put(FIELD_USER_REF_REF, UserDB.DB)
        .put(FIELD_USER_REF_ID, userId));

    JsonObject deployArgs = new JsonObject()
        .put(FIELD_DEPLOY_ARGS_PLATFORM, platform)
        .put(FIELD_DEPLOY_ARGS_PLATFORM_ARGS, platformArgs)
        .put(FIELD_DEPLOY_ARGS_BACKEND_ARGS, backendArgs);

    project.put(FIELD_CREATE_TIME, System.currentTimeMillis())
        .put(FIELD_DEPLOY_ID, UUID.randomUUID().toString())
        .put(FIELD_DEPLOY_ARGS, deployArgs)
        .put(FIELD_BACKEND_STATUS, new JsonObject()
            .put(FIELD_BACKEND_STATUS_STATUS, BackendState.CREATED.toString())
            .put(FIELD_BACKEND_STATUS_CREATE_TIME , System.currentTimeMillis())
        )
        .put(FIELD_NAME, projectName)
        .put(FIELD_SPEC, spec);

    return mongo.insert(DB, project);
  }

  public static Future<String> copyFrom(MongoClient mongo, String userId, String projectName) {
    return mongo.findOne(
        DB,
        new JsonObject()
            .put(FIELD_NAME, projectName)
            .put(FIELD_USER_REF + "." + FIELD_USER_REF_ID, userId),
        new JsonObject()
        ).compose((JsonObject project) -> {
          project.put(FIELD_DEPLOY_ID, UUID.randomUUID().toString())
              .put(FIELD_CREATE_TIME, System.currentTimeMillis())
              .put(FIELD_NAME, projectName + "_copy_" + UUID.randomUUID().toString().substring(0, 4));
          return mongo.insert(DB, project);
    });
  }

  public static Future<String> recoverFrom(MongoClient mongo, String userId, String execId) {
    return ExecDB.get(mongo, userId, execId)
        .compose((JsonObject exec) -> {
          JsonObject project = new JsonObject();
          JsonObject deployArgs = exec.getJsonObject(ExecDB.FIELD_DEPLOY_ARGS);
          return add(mongo,
              userId,
              "recover_" + execId,
              Platform.valueOf(deployArgs.getString(ExecDB.FIELD_DEPLOY_ARGS_PLATFORM)),
              deployArgs.getJsonObject(ExecDB.FIELD_DEPLOY_ARGS_PLATFORM_ARGS),
              deployArgs.getJsonObject(ExecDB.FIELD_DEPLOY_ARGS_BACKEND_ARGS),
              exec.getJsonObject(ExecDB.FIELD_SPEC)
              );
        });
  }

  private static JsonObject emptySpec() {
    return new JsonObject()
        .put("version", "1.0")
        .put("metals", new JsonArray())
        .put("edges", new JsonArray());
  }

  public static Future<String> add( MongoClient mongo, String userId, String projectName) {
    return ProjectDB.add(mongo, userId, projectName,
        Platform.SPARK, new JsonObject(), new JsonObject(),
        emptySpec());
  }

  public static Future<String> add( MongoClient mongo, String userId, String projectName, Platform platform) {
    return ProjectDB.add(mongo, userId, projectName,
        platform, new JsonObject(), new JsonObject(),
        emptySpec());
  }

  public static Future<JsonObject> update(
      MongoClient mongo,
      String userId,
      String projectName,
      Optional<Platform> platform,
      Optional<JsonObject> platformArgs,
      Optional<JsonObject> backendArgs,
      Optional<JsonObject> spec
      ) {
    JsonObject update = new JsonObject();
    platform.ifPresent(p -> {
      update.put(FIELD_DEPLOY_ARGS_PLATFORM, p.toString());
    });

    platformArgs.ifPresent(args -> {
      update.put(FIELD_DEPLOY_ARGS_PLATFORM_ARGS, args);
    });

    backendArgs.ifPresent(args -> {
      update.put(FIELD_DEPLOY_ARGS_BACKEND_ARGS, args);
    });

    if (platform.isPresent() || platformArgs.isPresent() || backendArgs.isPresent()) {
      update.put(FIELD_DEPLOY_ID, UUID.randomUUID().toString());
    }

    spec.ifPresent(s -> {
      update.put(FIELD_SPEC, s);
    });

    return mongo.findOneAndUpdate(
        DB,
        new JsonObject()
            .put(FIELD_NAME, projectName)
            .put(FIELD_USER_REF + "." + FIELD_USER_REF_ID, userId),
        update
        );
  }

  public static Future<JsonObject> updateProjectName(MongoClient mongo, String userId, String projectName, String newProjectName) {
    return mongo.findOneAndUpdate(
      DB,
      new JsonObject()
          .put(FIELD_USER_REF + "." + FIELD_USER_REF_ID, userId)
          .put(FIELD_NAME, projectName),
      new JsonObject()
            .put(FIELD_NAME, newProjectName)
    );
  }

  public static Future<JsonObject> getOfId(MongoClient mongo, String userId, String projectId) {
    return mongo.findOne(
        DB,
        new JsonObject()
            .put(FIELD_ID, projectId)
            .put(FIELD_USER_REF + "." + FIELD_USER_REF_ID, userId),
        new JsonObject()
    );
  }

  public static ReadStream<JsonObject> getOfName(MongoClient mongo, String userId, String projectName) {
    JsonObject match = new JsonObject()
        .put("$match", new JsonObject()
            .put(FIELD_NAME, projectName)
            .put(FIELD_USER_REF + "." + FIELD_USER_REF_ID, userId)
        );

    JsonObject lookup = new JsonObject()
        .put("$lookup", new JsonObject()
            .put("from", UserDB.DB)
            .put("localField", FIELD_USER_REF + "." + FIELD_USER_REF_ID)
            .put("foreignField", UserDB.FIELD_ID)
            .put("as", FIELD_USER_INFO));

    JsonObject project = new JsonObject()
        .put("$project",  new JsonObject()
            .put(FIELD_ID, true)
            .put(FIELD_NAME, true)
            .put(FIELD_CREATE_TIME, true)
            .put(FIELD_DEPLOY_ID, true)
            .put(FIELD_DEPLOY_ARGS, true)
            .put(FIELD_BACKEND_STATUS, true)
            .put(FIELD_SPEC, true)
            .put(FIELD_USER_INFO, new JsonObject()
                .put("$arrayElemAt", new JsonArray().add("$" + FIELD_USER_INFO).add(0))));

    JsonObject projectUserInfo = new JsonObject()
        .put("$project",  new JsonObject()
            .put(FIELD_ID, true)
            .put(FIELD_NAME, true)
            .put(FIELD_CREATE_TIME, true)
            .put(FIELD_DEPLOY_ID, true)
            .put(FIELD_DEPLOY_ARGS, true)
            .put(FIELD_BACKEND_STATUS, true)
            .put(FIELD_SPEC, true)
            .put(FIELD_USER_INFO, new JsonObject()
                .put(FIELD_USER_INFO_ID, true)
                .put(FIELD_USER_INFO_NAME, true)
            )
        );

    JsonArray pipeline = new JsonArray()
        .add(match)
        .add(lookup)
        .add(project)
        .add(projectUserInfo);
    LOGGER.debug(pipeline.toString());
    return mongo.aggregate(DB, pipeline);
  }

  public static ReadStream<JsonObject> getAllOfUser(MongoClient mongo, String userId) {
    JsonObject matchUserId = new JsonObject()
        .put("$match", new JsonObject()
            .put(FIELD_USER_REF + "." + FIELD_USER_REF_ID, userId));

    JsonObject lookup = new JsonObject()
        .put("$lookup", new JsonObject()
            .put("from", UserDB.DB)
            .put("localField", FIELD_USER_REF + "." + FIELD_USER_REF_ID)
            .put("foreignField", UserDB.FIELD_ID)
            .put("as", FIELD_USER_INFO));

    JsonObject project = new JsonObject()
        .put("$project",  new JsonObject()
            .put(FIELD_ID, true)
            .put(FIELD_NAME, true)
            .put(FIELD_CREATE_TIME, true)
            .put(FIELD_DEPLOY_ID, true)
            .put(FIELD_DEPLOY_ARGS, true)
            .put(FIELD_BACKEND_STATUS, true)
            .put(FIELD_SPEC, true)
            .put(FIELD_USER_INFO, new JsonObject()
                .put("$arrayElemAt", new JsonArray().add("$" + FIELD_USER_INFO).add(0))));

    JsonObject projectUserInfo = new JsonObject()
        .put("$project",  new JsonObject()
            .put(FIELD_ID, true)
            .put(FIELD_NAME, true)
            .put(FIELD_CREATE_TIME, true)
            .put(FIELD_DEPLOY_ID, true)
            .put(FIELD_DEPLOY_ARGS, true)
            .put(FIELD_BACKEND_STATUS, true)
            .put(FIELD_SPEC, true)
            .put(FIELD_USER_INFO, new JsonObject()
                .put(FIELD_USER_INFO_ID, true)
                .put(FIELD_USER_INFO_NAME, true)
            )
        );

    JsonArray pipeline = new JsonArray()
        .add(matchUserId)
        .add(lookup)
        .add(project)
        .add(projectUserInfo);
    LOGGER.debug(pipeline.toString());
    return mongo.aggregate(DB, pipeline);
  }

  public static ReadStream<JsonObject> getAll(MongoClient mongo) {
    JsonObject lookup = new JsonObject()
        .put("$lookup", new JsonObject()
            .put("from", UserDB.DB)
            .put("localField", FIELD_USER_REF + "." + FIELD_USER_REF_ID)
            .put("foreignField", UserDB.FIELD_ID)
            .put("as", FIELD_USER_INFO));

    JsonObject project = new JsonObject()
        .put("$project",  new JsonObject()
            .put(FIELD_ID, true)
            .put(FIELD_NAME, true)
            .put(FIELD_CREATE_TIME, true)
            .put(FIELD_DEPLOY_ID, true)
            .put(FIELD_DEPLOY_ARGS, true)
            .put(FIELD_BACKEND_STATUS, true)
            .put(FIELD_SPEC, true)
            .put(FIELD_USER_INFO, new JsonObject()
                .put("$arrayElemAt", new JsonArray().add("$" + FIELD_USER_INFO).add(0))));

    JsonObject projectUserInfo = new JsonObject()
        .put("$project",  new JsonObject()
            .put(FIELD_ID, true)
            .put(FIELD_NAME, true)
            .put(FIELD_CREATE_TIME, true)
            .put(FIELD_DEPLOY_ID, true)
            .put(FIELD_DEPLOY_ARGS, true)
            .put(FIELD_BACKEND_STATUS, true)
            .put(FIELD_SPEC, true)
            .put(FIELD_USER_INFO, new JsonObject()
                .put(FIELD_USER_INFO_ID, true)
                .put(FIELD_USER_INFO_NAME, true)
            )
        );

    JsonArray pipeline = new JsonArray()
        .add(lookup)
        .add(project)
        .add(projectUserInfo);
    LOGGER.debug(pipeline.toString());
    return mongo.aggregate(DB, pipeline);
  }

  public static Future<MongoClientDeleteResult> remove(MongoClient mongo, String userId, String projectName) {
    return mongo.removeDocument(
        DB,
        new JsonObject()
            .put(FIELD_NAME, projectName)
            .put(FIELD_USER_REF + "." + FIELD_USER_REF_ID, userId)
    );
  }

  public static Future<MongoClientDeleteResult> removeAllOfUser(MongoClient mongo, String userId) {
    return mongo.removeDocuments(
        DB,
        new JsonObject()
            .put(FIELD_USER_REF + "." + FIELD_USER_REF_ID, userId)
    );
  }

  public static Future<MongoClientDeleteResult> removeAll(MongoClient mongo) {
    return mongo.removeDocuments(DB, new JsonObject());
  }

}

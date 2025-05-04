package io.vertx.neo4vertx.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Neo4jGraphVerticle extends AbstractVerticle {
  private static final Logger logger = LoggerFactory.getLogger(Neo4jGraphVerticle.class);
  private ConfigRetriever retriever;

  @Override
  public void start(Promise<Void> startPromise) {
    logger.info("Starting Neo4j Graph Verticle");

    vertx.fileSystem().exists("config.yaml", res -> {
      if (res.succeeded()) {
        if (res.result()) {
          logger.info("Config file found: " + "config.yaml");
        } else {
          logger.warn("Config file NOT found: " + "config.yaml");
        }
      } else {
        logger.error("Failed to check file existence", res.cause());
      }
    });


    ConfigStoreOptions store = new ConfigStoreOptions()
      .setType("file")
      .setFormat("yaml")
      .setConfig(new JsonObject().put("path", "config.yaml"));

    retriever = ConfigRetriever.create(this.vertx, new ConfigRetrieverOptions().addStore(store));

    retriever.getConfig().onComplete(ar -> {
      if (ar.failed()) {
        logger.error("Failed to retrieve configuration", ar.cause());
        startPromise.fail(ar.cause());
        return;
      }

      JsonObject result = ar.result();
      ConcurrentHashMap<String, Object> config = new ConcurrentHashMap<>(result.getMap());
      logger.info("Configuration retrieved:" + result.encodePrettily());

      Map<String, Object> entityQueriesMap = new HashMap<>();
      Map<String, Object> retrieved = null;

      if (result.containsKey("neo4jQueries")) {
        retrieved = result.getJsonObject("neo4jQueries").getMap();
        logger.info("Found neo4jQueries: " + retrieved);
      }

      if (MapUtils.isEmpty(retrieved)) {
        logger.warn("No entities to cypher");
      } else {
        for (Map<String, Object> entityPairToCypherQuery :
          (List<Map<String, Object>>) retrieved.get("entitiesToCypherQueries")) {
          String entityPair = String.valueOf(entityPairToCypherQuery.get("entityPair"));
          String query = String.valueOf(entityPairToCypherQuery.get("query"));
          if (StringUtils.isBlank(entityPair) || StringUtils.isBlank(query)) {
            logger.warn("Invalid Cypher Query Configuration");
          } else {
            entityQueriesMap.put(entityPair, query);
          }
        }
      }

      vertx.setPeriodic(5000, id -> {
        logger.info("neo4j service proxy standby...");
        logger.info("Supported queries: " + entityQueriesMap.keySet());
      });

      // Setup HTTP server
      Router router = Router.router(vertx);
      router.route().handler(BodyHandler.create());

      final String dbUri = "neo4j://localhost";
      final String dbUser = "neo4j";
      final String dbPassword = "neo4j";
      RemoteGraphService remoteGraphService = new RemoteGraphService(vertx, dbUri, dbUser, dbPassword);

      router.route(HttpMethod.POST, "/query").handler(ctx -> {
        JsonObject json = ctx.body().asJsonObject();
        String incomingQueryName = json.getString("query");
        String cypherQuery = String.valueOf(entityQueriesMap.get(incomingQueryName));

        Map<String, Object> variablesMap = json.getJsonObject("variables").getMap();
        for (String variableName : variablesMap.keySet()) {
          cypherQuery = cypherQuery.replace(variableName, variablesMap.get(variableName).toString());
        }

        logger.info("Executing Cypher: " + cypherQuery);
        Map<String, Object> response = new ConcurrentHashMap<>();
        remoteGraphService.query(cypherQuery, resultHandler -> {
          if (resultHandler.succeeded()) {
            response.putAll(resultHandler.result());
            try {
              String jsonResponse = new ObjectMapper().writeValueAsString(response);
              ctx.response().putHeader("content-type", "application/json").end(jsonResponse);
            } catch (JsonProcessingException e) {
              logger.error("Failed to serialize response", e);
              ctx.fail(e);
            }
          } else {
            logger.error("Query failed", resultHandler.cause());
            ctx.fail(resultHandler.cause());
          }
        });
      });

      vertx.createHttpServer()
        .requestHandler(router)
        .listen(8090, http -> {
          if (http.succeeded()) {
            logger.info("HTTP server started on http://localhost:8090/");
            startPromise.complete();
          } else {
            logger.error("Failed to start HTTP server", http.cause());
            startPromise.fail(http.cause());
          }
        });
    });
  }


  public static void main(String[] args) {
    logger.info("Starting application...");
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(new Neo4jGraphVerticle());
  }
}

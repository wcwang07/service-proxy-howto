package io.vertx.neo4vertx.service;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.Shareable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import org.neo4j.driver.AccessMode;
import org.neo4j.driver.AuthToken;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Value;
import org.neo4j.driver.async.AsyncSession;
import org.neo4j.driver.summary.ResultSummary;
import org.neo4j.driver.types.Node;

public class RemoteGraphService implements GraphService {

  private Vertx vertx;

  private static final SessionConfig
    DEFAULT_READ_SESSION_CONFIG = SessionConfig.builder()
    .withDatabase("identitynetworktwodb")
    .withDefaultAccessMode(AccessMode.READ)
    .build();
  private Driver driver;

  public RemoteGraphService(Vertx vertx, String dbUri, String dbUser, String dbPassword) {
    this.vertx = vertx;
    this.driver = new Neo4jHolder(dbUri, dbUser, dbPassword).neo4jDriver();
  }

  @Override
  public void query(String query, Handler<AsyncResult<Map<String, Object>>> resultHandler) {
    Context context = vertx.getOrCreateContext();
    AsyncSession sessionAsync = driver.session(AsyncSession.class, DEFAULT_READ_SESSION_CONFIG);
    Map<String, Object> responseMap = new ConcurrentHashMap<>();
    sessionAsync.executeReadAsync(tx -> tx.runAsync(query)
      .thenCompose(resultCursor -> {
        CompletionStage<ResultSummary> results = resultCursor.forEachAsync(record -> {
          context.runOnContext(v -> {
            for (Value value : record.values()) {
              if (value instanceof Node) {
                String entityName = value.asNode().labels().iterator().next();
                Map<String, Object> entityValueMap = new ConcurrentHashMap<>(value.asMap());
                responseMap.computeIfAbsent(entityName, k -> new HashMap<String, Object>(entityValueMap));
              }
              else {
                Map<String, Object> entityValueMap = new ConcurrentHashMap<>(value.asMap());
                responseMap.putAll(entityValueMap);
              }
            }
          });
        });
        return results;
      })
      .whenComplete((resultSummary, error) -> {
        sessionAsync.closeAsync();
        context.runOnContext(v -> {
          if (error != null) {
            resultHandler.handle(Future.failedFuture(error));
          } else {
            resultHandler.handle(Future.succeededFuture(responseMap));
          }
        });
      })
      .exceptionally(ex -> {
        context.runOnContext(v -> resultHandler.handle(Future.failedFuture(ex)));
        return null;
      }));
  }

  private static class Neo4jHolder implements Shareable {

    Driver driver;
    String dbUri;
    AuthToken authToken;

    Neo4jHolder(String dbUri, String dbUser, String dbPassword) {
      this.dbUri = dbUri;
      this.authToken = AuthTokens.basic(dbUser,dbPassword);
    }

    synchronized Driver neo4jDriver() {
      if (driver == null) {
        Driver driver = GraphDatabase.driver(dbUri, this.authToken);
        driver.verifyConnectivity();
        System.out.println("neo4j cluster connection established...");
        this.driver = driver;
      }
      return this.driver;
    }
  }
}

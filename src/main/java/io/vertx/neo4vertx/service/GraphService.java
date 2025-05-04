package io.vertx.neo4vertx.service;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

import java.util.Map;

public interface GraphService {

  void query(String query, Handler<AsyncResult<Map<String, Object>>> resultHandler);
}

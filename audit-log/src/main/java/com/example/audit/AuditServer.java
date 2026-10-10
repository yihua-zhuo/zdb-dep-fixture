package com.example.audit;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Records sign-in attempts from the store terminals. */
public final class AuditServer {
  private static final Logger LOG = LogManager.getLogger(AuditServer.class);

  public static void record(String user, String terminal) {
    LOG.info("Sign-in attempt by {} at terminal {}", user, terminal);
  }

  public static void main(String[] args) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress(8082), 0);
    server.createContext("/signin", exchange -> {
      String user = exchange.getRequestHeaders().getFirst("X-User");
      String terminal = exchange.getRequestHeaders().getFirst("X-Terminal");
      record(user, terminal);
      byte[] ok = "recorded".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, ok.length);
      exchange.getResponseBody().write(ok);
      exchange.close();
    });
    server.start();
  }
}

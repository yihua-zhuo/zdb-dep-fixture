package com.example.audit;

import java.lang.reflect.Proxy;
import java.util.Hashtable;
import java.util.concurrent.atomic.AtomicInteger;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.spi.InitialContextFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.layout.PatternLayout;

/** Plain POJO test, supported by Maven Surefire without an extra test dependency. */
public class SignInLookupSecurityTest {
  private static final AtomicInteger LOOKUPS = new AtomicInteger();

  /** Replaces only the standard JNDI provider boundary; never opens a connection. */
  public static final class MarkerNamingFactory implements InitialContextFactory {
    @Override
    public Context getInitialContext(Hashtable<?, ?> environment) {
      return (Context) Proxy.newProxyInstance(
          SignInLookupSecurityTest.class.getClassLoader(), new Class<?>[] {Context.class},
          (proxy, method, args) -> {
            if (method.getName().equals("lookup")) {
              LOOKUPS.incrementAndGet();
              return "in-process-marker";
            }
            if (method.getName().equals("getEnvironment")) return new Hashtable<>();
            if (method.getName().equals("close")) return null;
            if (method.getName().equals("toString")) return "MarkerNamingContext";
            throw new UnsupportedOperationException(method.getName());
          });
    }
  }

  public void testSignInHeadersDoNotPerformNamingLookups() throws Exception {
    String previousFactory = System.getProperty(Context.INITIAL_CONTEXT_FACTORY);
    LoggerContext logging = (LoggerContext) LogManager.getContext(false);
    Configuration previousConfiguration = logging.getConfiguration();
    org.apache.logging.log4j.core.config.DefaultConfiguration configuration =
        new org.apache.logging.log4j.core.config.DefaultConfiguration();
    // Exercise actual message rendering, where older Log4j versions resolve lookups.
    PatternLayout layout = PatternLayout.newBuilder()
        .withConfiguration(configuration).withPattern("%m%n").build();
    AbstractAppender appender = new AbstractAppender("security-capture", null, layout, false) {
      @Override
      public void append(LogEvent event) {
        layout.toSerializable(event);
      }
    };
    try {
      System.setProperty(Context.INITIAL_CONTEXT_FACTORY, MarkerNamingFactory.class.getName());
      InitialContext probe = new InitialContext();
      try {
        probe.lookup("java:comp/env/securityMarker");
      } finally {
        probe.close();
      }
      if (LOOKUPS.get() != 1) throw new AssertionError("In-process naming stub was not reached");
      LOOKUPS.set(0);

      for (String name : configuration.getRootLogger().getAppenders().keySet()) {
        configuration.getRootLogger().removeAppender(name);
      }
      appender.start();
      configuration.addAppender(appender);
      configuration.getRootLogger().addAppender(appender, Level.ALL, null);
      configuration.getRootLogger().setLevel(Level.INFO);
      logging.setConfiguration(configuration);

      AuditServer.record("alice", "desk");
      if (LOOKUPS.get() != 0) throw new AssertionError("Ordinary sign-in caused a naming lookup");

      String marker = "${jndi:java:comp/env/securityMarker}";
      AuditServer.record(marker, "desk");
      AuditServer.record("alice", marker);
      if (LOOKUPS.get() != 0) {
        throw new AssertionError("Sign-in headers must not trigger JNDI lookups; observed "
            + LOOKUPS.get());
      }
    } finally {
      logging.setConfiguration(previousConfiguration);
      appender.stop();
      if (previousFactory == null) System.clearProperty(Context.INITIAL_CONTEXT_FACTORY);
      else System.setProperty(Context.INITIAL_CONTEXT_FACTORY, previousFactory);
      LOOKUPS.set(0);
    }
  }
}

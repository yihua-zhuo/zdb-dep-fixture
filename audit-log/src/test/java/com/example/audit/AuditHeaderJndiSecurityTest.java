package com.example.audit;

import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.naming.InitialContext;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.spi.NamingManager;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.junit.Test;

public class AuditHeaderJndiSecurityTest {
  @Test
  public void untrustedAuditFieldsMustNotInitiateNamingLookups() throws Exception {
    List<String> namingOperations = new CopyOnWriteArrayList<>();
    NamingManager.setInitialContextFactoryBuilder(environment -> contextEnvironment -> {
      namingOperations.add("initial context");
      return new MarkerContext(namingOperations);
    });

    AuditServer.record("ordinary-user", "ordinary-terminal");
    LoggerContext context = (LoggerContext) LogManager.getContext(false);
    LoggerConfig root = context.getConfiguration().getRootLogger();
    Level previousLevel = root.getLevel();
    RecordingAppender recorder = new RecordingAppender();
    recorder.start();
    root.addAppender(recorder, Level.ALL, null);
    root.setLevel(Level.INFO);
    context.updateLoggers();

    try {
      AuditServer.record("ordinary-user", "ordinary-terminal");
      assertTrue("Ordinary audit records must still be logged",
          recorder.messages.stream().anyMatch(message ->
              message.contains("ordinary-user") && message.contains("ordinary-terminal")));
      assertTrue("Ordinary audit fields must not initiate JNDI lookups: " + namingOperations,
          namingOperations.isEmpty());

      String marker = "${jndi:java:comp/env/auditMarker}";
      submitUntrustedFields(marker, "ordinary-terminal");
      submitUntrustedFields("ordinary-user", marker);

      assertTrue("Audit headers must not initiate JNDI lookups: " + namingOperations,
          namingOperations.isEmpty());
    } finally {
      root.removeAppender(recorder.getName());
      root.setLevel(previousLevel);
      context.updateLoggers();
      recorder.stop();
    }
  }

  private static void submitUntrustedFields(String user, String terminal) {
    try {
      AuditServer.record(user, terminal);
    } catch (IllegalArgumentException | SecurityException rejected) {
      return;
    }
  }

  private static final class MarkerContext extends InitialContext {
    private final List<String> namingOperations;

    private MarkerContext(List<String> namingOperations) throws NamingException {
      super(true);
      this.namingOperations = namingOperations;
    }

    @Override
    public Object lookup(String name) {
      namingOperations.add(name);
      return "local-audit-marker";
    }

    @Override
    public Object lookup(Name name) {
      return lookup(name.toString());
    }

    @Override
    public void close() {
    }
  }

  private static final class RecordingAppender extends AbstractAppender {
    private final List<String> messages = new CopyOnWriteArrayList<>();

    private RecordingAppender() {
      super("audit-header-security-recorder", null, null, false, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
      messages.add(event.getMessage().getFormattedMessage());
    }
  }
}

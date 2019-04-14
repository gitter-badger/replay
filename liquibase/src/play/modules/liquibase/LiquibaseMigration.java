package play.modules.liquibase;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.exception.LiquibaseException;
import liquibase.lockservice.LockServiceFactory;
import liquibase.resource.FileSystemResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.Play;

import java.io.File;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import static java.lang.Boolean.parseBoolean;
import static java.lang.System.nanoTime;
import static java.util.Comparator.comparingLong;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public final class LiquibaseMigration {

  private static final Logger logger = LoggerFactory.getLogger(LiquibaseMigration.class);

  private final String dbName;
  private final File changeLogPath;
  private final File dumpFile;
  private final String driver;
  private final String url;
  private final String username;
  private final String password;

  public LiquibaseMigration(String dbName, File changeLogPath, String driver, String url, String username, String password) {
    this.dbName = dbName;
    this.changeLogPath = changeLogPath;
    this.dumpFile = new File(changeLogPath.getAbsolutePath() + ".dump.sql");
    this.driver = driver;
    this.url = url;
    this.username = username;
    this.password = password;
  }

  public void migrate() {
    String autoUpdate = Play.configuration.getProperty("liquibase.active", "false");

    if (!parseBoolean(autoUpdate)) {
      logger.info("{} Auto update flag [{}] != true  => skipping structural update", changeLogPath, autoUpdate);
      return;
    }

    long start = nanoTime();

    try (Connection cnx = getConnection()) {
      if (isH2()) {
        restoreFromDump(cnx);
      }

      runLiquiBase(cnx);

      logger.info("{} finished in {} ms.", changeLogPath, NANOSECONDS.toMillis(nanoTime() - start));
    }
    catch (SQLException | LiquibaseException sqe) {
      throw new LiquibaseUpdateException("Failed to migrate " + changeLogPath, sqe);
    }
  }

  private void runLiquiBase(Connection cnx) throws LiquibaseException {
    if (isH2()) {
      LockServiceFactory.getInstance().register(new NonLockingLockService());
    }

    Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(cnx));
    try {
      FileSystemResourceAccessor accessor = new FileSystemResourceAccessor() {
        @Override protected void init() {
        }
      };
      Liquibase liquibase = new Liquibase(changeLogPath.getPath(), accessor, database);
      liquibase.update(Play.configuration.getProperty("liquibase.contexts", ""));
      if (isH2()) {
        storeDump(cnx);
      }
    }
    finally {
      close(database);
    }
  }

  private boolean isH2() {
    return url.contains(":h2:");
  }

  private void storeDump(Connection cnx) {
    logger.info("Store {} DB to {}", dbName, dumpFile);
    try (Statement statement = cnx.createStatement()) {
      String sql = String.format("script nopasswords to '%s'", dumpFile);
      statement.execute(sql);
    }
    catch (SQLException ex) {
      throw new LiquibaseUpdateException(String.format("Failed to store %s DB dump to %s", dbName, dumpFile), ex);
    }
  }

  private void restoreFromDump(Connection cnx) {
    if (dumpFile.exists()) {
      logger.info("Restore {} DB from {}", dbName, dumpFile);
      try (Statement statement = cnx.createStatement()) {
        String sql = String.format("runscript from '%s'", dumpFile);
        statement.execute(sql);
      }
      catch (SQLException ex) {
        throw new LiquibaseUpdateException(String.format("Failed to restore %s DB from dump %s", dbName, dumpFile), ex);
      }
    }
    else {
      logger.info("{} DB dump {} not found, creating DB from scratch", dbName, dumpFile);
    }
  }

  private void close(Database database) {
    try {
      database.close();
    }
    catch (DatabaseException | RuntimeException e) {
      logger.warn("{} problem closing connection: " + e, changeLogPath, e);
    }
  }

  @SuppressWarnings("CallToDriverManagerGetConnection")
  private Connection getConnection() throws SQLException {
    logger.info("Migrate {}: {} @ {}", changeLogPath, username, url);

    initDriver(driver);
    return DriverManager.getConnection(url, username, password);
  }

  private void initDriver(String driver) {
    try {
      Driver d = (Driver) Class.forName(driver).getConstructor().newInstance();
      DriverManager.registerDriver(d);
    } catch (Exception e) {
      throw new LiquibaseUpdateException("jdbc driver class not found: " + driver, e);
    }
  }
}

package sr.will.archiver.sql;

import org.mariadb.jdbc.MariaDbDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Consumer;

public class Database {
    private static final Logger LOGGER = LoggerFactory.getLogger("Database");

    private String host;
    private String database;
    private String user;
    private String password;
    private boolean debug = false;
    private ArrayList<Consumer<PreparedStatement>> hooks = new ArrayList<>();

    private Connection connection;
    private final ArrayList<String> disconnectReasons = new ArrayList<>(Arrays.asList(
            "The last packet successfully received from the server was",
            "Connection is closed",
            "Connection reset",
            "Connection was killed"
    ));

    public void connect() {
        LOGGER.info("Connecting to database...");

        try {
            MariaDbDataSource mariaDbDataSource = new MariaDbDataSource();
            mariaDbDataSource.setServerName(host);
            mariaDbDataSource.setDatabaseName(database);
            mariaDbDataSource.setUser(user);
            mariaDbDataSource.setPassword(password);

            connection = mariaDbDataSource.getConnection();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        LOGGER.info("Done");
    }

    public void disconnect() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void reconnect() {
        disconnect();
        connect();
    }

    public void setCredentials(String host, String database, String user, String password) {
        this.host = host;
        this.database = database;
        this.user = user;
        this.password = password;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    private void log(PreparedStatement statement) {
        if (debug) {
            System.out.println(getStatementString(statement));
        }
    }

    public void addHook(Consumer<PreparedStatement> consumer) {
        hooks.add(consumer);
    }

    private void processHooks(PreparedStatement statement) {
        for (Consumer<PreparedStatement> hook : hooks) {
            hook.accept(statement);
        }
    }

    public static String getStatementString(PreparedStatement statement) {
        return statement.toString().split(": ")[1];
    }

    public PreparedStatement replaceParams(PreparedStatement statement, Object... params) throws SQLException {
        // Get params and insert them into the query
        for (int x = 0; x < params.length; x += 1) {
            if (params[x] == null) {
                statement.setNull(x + 1, Types.NULL);
            } else if (params[x] instanceof String || params[x] instanceof UUID) {
                statement.setString(x + 1, params[x].toString());
            } else if (params[x] instanceof Integer) {
                statement.setInt(x + 1, (Integer) params[x]);
            } else if (params[x] instanceof Long) {
                statement.setLong(x + 1, (Long) params[x]);
            } else if (params[x] instanceof Boolean) {
                statement.setBoolean(x + 1, (Boolean) params[x]);
            } else {
                throw new SQLException("Unknown paramater type at position " + (x + 1) + ": " + params[x].toString());
            }
        }

        return statement;
    }

    private boolean execute(String query, boolean tried, Object... params) {
        try {
            PreparedStatement statement = replaceParams(connection.prepareStatement(query), params);
            // Log and process hooks
            log(statement);
            processHooks(statement);

            // Run the query
            return statement.execute();
        } catch (SQLException e) {
            // If the query errors with the start of this message than it has been too long since anything was communicated
            // If tried is false than this is the first attempt, try reconnecting and execute the query again
            if (!tried && shouldReconnect(e.getMessage())) {
                reconnect();
                return execute(query, true, params);
            }

            // Otherwise the query failed
            e.printStackTrace();
            return false;
        }
    }

    public boolean execute(String query, Object... params) {
        return execute(query, false, params);
    }

    private ResultSet query(String query, boolean tried, Object... params) {
        try {
            PreparedStatement statement = replaceParams(connection.prepareStatement(query), params);
            // Log and process hooks
            log(statement);
            processHooks(statement);

            // Run the query
            return statement.executeQuery();
        } catch (SQLException e) {
            // If the query errors with the start of this message than it has been too long since anything was communicated
            // If tried is false than this is the first attempt, try reconnecting and execute the query again
            if (!tried && shouldReconnect(e.getMessage())) {
                reconnect();
                return query(query, true, params);
            }

            // Otherwise the query failed
            e.printStackTrace();
            return null;
        }
    }

    public ResultSet query(String query, Object... params) {
        return query(query, false, params);
    }

    @Deprecated
    public ResultSet executeQuery(String query, Object... params) {
        return query(query, params);
    }

    private boolean shouldReconnect(String error) {
        for (String reason : disconnectReasons) {
            if (error.contains(reason)) return true;
        }

        return false;
    }
}

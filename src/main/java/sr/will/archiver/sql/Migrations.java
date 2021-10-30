package sr.will.archiver.sql;

import sr.will.archiver.Archiver;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

public class Migrations {
    private static final List<Migration> migrations = Arrays.asList(
    );

    public static void deploy() {
        createTables();

        // Get the last migration applied to the database
        int applyFrom = getLastMigrationIndex();

        // No migrations to apply, up to date
        if (applyFrom == migrations.size()) return;

        // Apply all migrations after the last applied
        Archiver.LOGGER.info("Applying {} migrations...", migrations.size() - applyFrom);
        for (int x = applyFrom; x < migrations.size(); x += 1) {
            migrations.get(x).queries.forEach(query -> Archiver.database.execute(query));
            Archiver.database.execute("INSERT INTO migrations (id) VALUES (?);", x);
        }
    }

    private static int getLastMigrationIndex() {
        try {
            ResultSet result = Archiver.database.query("SELECT (id) FROM migrations ORDER BY id DESC LIMIT 1;");
            if (result.first()) return result.getInt("id");
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return 0;
    }

    private static void createTables() {
        Archiver.database.execute("CREATE TABLE IF NOT EXISTS migrations(" +
                                          "id int NOT NULL," +
                                          "PRIMARY KEY (id));");
        Archiver.database.execute("CREATE TABLE IF NOT EXISTS vods(" +
                                          "id int NOT NULL," +
                                          "channel_id int NOT NULL," +
                                          "created_at bigint(20) NOT NULL," +
                                          "title varchar(120) NOT NULL," +
                                          "description text NOT NULL," +
                                          "youtube_id char(11) NOT NULL," +
                                          "downloaded boolean NOT NULL DEFAULT 0," +
                                          "transcoded boolean NOT NULL DEFAULT 0," +
                                          "uploaded boolean NOT NULL DEFAULT 0," +
                                          "PRIMARY KEY (id));");
        Archiver.database.execute("CREATE TABLE IF NOT EXISTS chat(" +
                                          "id char(36) NOT NULL," + // message UUID
                                          "channel int NOT NULL," + // channel ID
                                          "vod int NOT NULL," + // VOD ID
                                          "`offset` double NOT NULL," + // offset in seconds from the beginning of the vod, offset is in quotes because it's a MySQL keyword
                                          "author varchar(25) NOT NULL," + // author display name
                                          "message longtext NOT NULL," + // json blob of the message
                                          "PRIMARY KEY (id)," +
                                          "FOREIGN KEY (vod) REFERENCES vods(id));");
        Archiver.database.execute("CREATE TABLE IF NOT EXISTS credentials(" +
                                          "`key` varchar(255) NOT NULL," +
                                          "updated bigint(20) NOT NULL," +
                                          "value longtext NOT NULL," +
                                          "PRIMARY KEY (`key`));");
    }
}

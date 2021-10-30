package sr.will.archiver.youtube;

import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import sr.will.archiver.sql.Database;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class DBDataStore<V extends Serializable> implements DataStore<V> {
    private static final Gson gson = new GsonBuilder()
            .setExclusionStrategies(new StoredCredentialExclusionStrategy())
            .create();

    private final String id;
    private final Database database;
    private final DBDataStoreFactory dataStoreFactory;

    public DBDataStore(String id, Database database, DBDataStoreFactory dataStoreFactory) {
        this.id = id;
        this.database = database;
        this.dataStoreFactory = dataStoreFactory;
    }

    @Override
    public DataStoreFactory getDataStoreFactory() {
        return dataStoreFactory;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public int size() throws IOException {
        try {
            ResultSet result = database.query("SELECT COUNT(*) FROM credentials;");
            if (!result.first()) throw new IOException("No result from query, is your database connected?");

            return result.getInt(0);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IOException(e.getMessage(), e.getCause());
        }
    }

    @Override
    public boolean isEmpty() throws IOException {
        return size() == 0;
    }

    @Override
    public boolean containsKey(String key) throws IOException {
        try {
            ResultSet result = database.query("SELECT 1 FROM ? WHERE `key` = credentials;", key);
            return result.first();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IOException(e.getMessage(), e.getCause());
        }
    }

    @Override
    public boolean containsValue(V value) throws IOException {
        try {
            ResultSet result = database.query("SELECT 1 FROM credentials WHERE value = ?;", gson.toJson(value));
            return result.first();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IOException(e.getMessage(), e.getCause());
        }
    }

    @Override
    public Set<String> keySet() throws IOException {
        try {
            Set<String> keys = new HashSet<>();
            ResultSet result = database.query("SELECT `key` FROM credentials WHERE 1;");
            while (result.next()) {
                keys.add(result.getString("key"));
            }

            return keys;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IOException(e.getMessage(), e.getCause());
        }
    }

    @Override
    public Collection<V> values() throws IOException {
        try {
            Set<V> values = new HashSet<>();
            ResultSet result = database.query("SELECT value FROM credentials WHERE 1;");
            Type valueType = new TypeToken<V>() {
            }.getType();

            while (result.next()) {
                values.add(gson.fromJson(result.getString("value"), valueType));
            }

            return values;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IOException(e.getMessage(), e.getCause());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public V get(String key) throws IOException {
        try {
            ResultSet result = database.query("SELECT value FROM credentials WHERE `key` = ?;", key);
            if (!result.first()) return null;

            return (V) gson.fromJson(result.getString("value"), StoredCredential.class);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IOException(e.getMessage(), e.getCause());
        }
    }

    @Override
    public DataStore<V> set(String key, V value) throws IOException {
        database.execute("REPLACE INTO credentials (`key`, value, updated) VALUES (?, ?, ?);", key, gson.toJson(value), System.currentTimeMillis());
        return this;
    }

    @Override
    public DataStore<V> clear() throws IOException {
        database.execute("TRUNCATE credentials;");
        return this;
    }

    @Override
    public DataStore<V> delete(String key) throws IOException {
        database.execute("DELETE FROM credentials WHERE `key` = ?;", key);
        return this;
    }
}

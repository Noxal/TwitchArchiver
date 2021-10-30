package sr.will.archiver.youtube;

import com.google.api.client.util.store.AbstractDataStoreFactory;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.DataStoreFactory;
import sr.will.archiver.sql.Database;

import java.io.IOException;
import java.io.Serializable;

public class DBDataStoreFactory extends AbstractDataStoreFactory implements DataStoreFactory {
    public final Database database;

    public DBDataStoreFactory(Database database) {
        this.database = database;
    }

    @Override
    protected <V extends Serializable> DataStore<V> createDataStore(String id) throws IOException {
        return new DBDataStore<>(id, database, this);
    }
}

package sr.will.archiver.sql;

import java.util.Arrays;
import java.util.List;

public class Migration {
    public int id;
    public List<String> queries;

    public Migration(int id, String... queries) {
        this.id = id;
        this.queries = Arrays.asList(queries);
    }
}

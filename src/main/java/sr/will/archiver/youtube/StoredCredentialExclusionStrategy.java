package sr.will.archiver.youtube;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;

import java.util.concurrent.locks.Lock;

public class StoredCredentialExclusionStrategy implements ExclusionStrategy {
    @Override
    public boolean shouldSkipField(FieldAttributes f) {
        return false;
    }

    @Override
    public boolean shouldSkipClass(Class<?> clazz) {
        return clazz == Lock.class;
    }
}

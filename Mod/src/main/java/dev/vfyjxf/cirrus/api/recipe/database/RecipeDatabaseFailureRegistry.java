package dev.vfyjxf.cirrus.api.recipe.database;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class RecipeDatabaseFailureRegistry {
    private static final RecipeDatabaseFailureRegistry shared = new RecipeDatabaseFailureRegistry();

    private final ConcurrentHashMap<DatabaseRecipeRef, Failure> failuresByRef = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<DatabaseRecipeRef, Unsupported> unsupportedByRef = new ConcurrentHashMap<>();
    private final AtomicLong version = new AtomicLong();

    public static RecipeDatabaseFailureRegistry shared() {
        return shared;
    }

    public long version() {
        return version.get();
    }

    public Set<DatabaseRecipeRef> failedRecipeRefs() {
        return Set.copyOf(failuresByRef.keySet());
    }

    public Set<DatabaseRecipeRef> unsupportedRecipeRefs() {
        return Set.copyOf(unsupportedByRef.keySet());
    }

    public void recordFailures(Collection<Failure> failures) {
        if (failures == null || failures.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (Failure failure : failures) {
            if (failure == null || failure.ref() == null) {
                continue;
            }
            Failure stored = failure.withoutCause();
            Failure previous = failuresByRef.put(failure.ref(), stored);
            changed |= previous == null || !previous.sameFailure(stored);
        }
        if (changed) {
            version.incrementAndGet();
        }
    }

    public void recordUnsupported(Collection<Unsupported> unsupported) {
        if (unsupported == null || unsupported.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (Unsupported entry : unsupported) {
            if (entry == null || entry.ref() == null) {
                continue;
            }
            Unsupported previous = unsupportedByRef.put(entry.ref(), entry);
            changed |= previous == null || !previous.sameUnsupported(entry);
        }
        if (changed) {
            version.incrementAndGet();
        }
    }

    public void clearForTests() {
        if (!failuresByRef.isEmpty() || !unsupportedByRef.isEmpty()) {
            failuresByRef.clear();
            unsupportedByRef.clear();
            version.incrementAndGet();
        }
    }

    public record Failure(
        DatabaseRecipeRef ref,
        String database,
        String recipeClass,
        String exception,
        String message,
        Throwable cause
    ) {
        public Failure {
            if (ref == null) {
                throw new NullPointerException("ref");
            }
            database = database == null ? "" : database;
            recipeClass = recipeClass == null ? "" : recipeClass;
            exception = exception == null ? "" : exception;
            message = message == null ? "" : message;
        }

        public static Failure from(DatabaseRecipeRef ref, String database, String recipeClass, Throwable throwable) {
            if (throwable == null) {
                throw new NullPointerException("throwable");
            }
            return new Failure(
                ref,
                database,
                recipeClass,
                throwable.getClass().getName(),
                throwable.getMessage(),
                throwable
            );
        }

        private boolean sameFailure(Failure other) {
            return database.equals(other.database())
                && recipeClass.equals(other.recipeClass())
                && exception.equals(other.exception())
                && message.equals(other.message());
        }

        private Failure withoutCause() {
            return cause == null
                ? this
                : new Failure(ref, database, recipeClass, exception, message, null);
        }
    }

    public record Unsupported(
        DatabaseRecipeRef ref,
        String database,
        String recipeClass,
        String error
    ) {
        public Unsupported {
            if (ref == null) {
                throw new NullPointerException("ref");
            }
            database = database == null ? "" : database;
            recipeClass = recipeClass == null ? "" : recipeClass;
            error = error == null ? "" : error;
        }

        private boolean sameUnsupported(Unsupported other) {
            return database.equals(other.database())
                && recipeClass.equals(other.recipeClass())
                && error.equals(other.error());
        }
    }
}

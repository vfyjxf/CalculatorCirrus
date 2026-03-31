package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import dev.vfyjxf.cirrus.api.recipe.codec.CodecJsonIO;
import dev.vfyjxf.cloudlib.util.Checks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public record FileRuleRepository(Path root) {
    private static final String playerId = "player";

    public FileRuleRepository(Path root) {
        this.root = Checks.checkNotNull(root, "root");
    }

    public RuleRepositoryView loadView(String worldId, boolean includeOtherWorldCollections) {
        RuleCollection defaults = DefaultRuleDefinitions.defaultCollection();
        RuleCollection player = loadCollection(new RuleCollectionKey(RuleCollectionScope.player, playerId));
        RuleCollection world = loadCollection(new RuleCollectionKey(RuleCollectionScope.world, worldId));
        List<RuleCollection> readableWorlds = includeOtherWorldCollections
                ? loadOtherWorldCollections(worldId)
                : List.of();
        ActiveRuleSelection active = loadActiveSelection(worldId);
        return new RuleRepositoryView(defaults, player, world, readableWorlds, active);
    }

    public RuleCollection loadCollection(RuleCollectionKey key) {
        Path file = collectionFile(key);
        if (!Files.exists(file)) {
            return new RuleCollection(key, List.of());
        }
        try {
            RuleCollection collection = CodecJsonIO.read(file, RuleCollection.CODEC);
            if (!collection.key().equals(key)) {
                throw new IOException("Rule collection key mismatch: " + file);
            }
            return collection;
        } catch (IOException ignored) {
            return new RuleCollection(key, List.of());
        }
    }

    public void saveCollection(RuleCollection collection) throws IOException {
        if (collection.key().scope() == RuleCollectionScope.defaults) {
            throw new IOException("Default rule collection is built in and read only");
        }
        CodecJsonIO.write(collectionFile(collection.key()), RuleCollection.CODEC, collection);
    }

    public ActiveRuleSelection loadActiveSelection(String worldId) {
        Path file = activeFile(worldId);
        if (!Files.exists(file)) {
            return DefaultRuleDefinitions.defaultActiveSelection();
        }
        try {
            return CodecJsonIO.read(file, ActiveRuleSelection.CODEC);
        } catch (IOException ignored) {
            return DefaultRuleDefinitions.defaultActiveSelection();
        }
    }

    public void saveActiveSelection(String worldId, ActiveRuleSelection selection) throws IOException {
        CodecJsonIO.write(activeFile(worldId), ActiveRuleSelection.CODEC, selection);
    }

    private List<RuleCollection> loadOtherWorldCollections(String currentWorldId) {
        Path worlds = root.resolve("worlds");
        if (!Files.isDirectory(worlds)) {
            return List.of();
        }
        try (var stream = Files.list(worlds)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(id -> !id.equals(currentWorldId))
                    .map(id -> loadCollection(new RuleCollectionKey(RuleCollectionScope.world, id)))
                    .filter(collection -> !collection.rules().isEmpty())
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private Path collectionFile(RuleCollectionKey key) {
        return switch (key.scope()) {
            case defaults -> root.resolve("defaults").resolve("rules.json");
            case player -> root.resolve("player").resolve("rules.json");
            case world -> root.resolve("worlds").resolve(key.id()).resolve("rules.json");
        };
    }

    private Path activeFile(String worldId) {
        return root.resolve("worlds").resolve(worldId).resolve("active_rule.json");
    }
}

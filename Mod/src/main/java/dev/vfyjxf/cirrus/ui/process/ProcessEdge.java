package dev.vfyjxf.cirrus.ui.process;

/**
 * Directed edge representing material flow between two process nodes.
 *
 * @param fromNodeId source node id
 * @param toNodeId   target node id
 * @param materialName material being transported
 * @param amount     quantity per operation
 * @param category   registered material category (for colour coding)
 */
public record ProcessEdge(
        String fromNodeId,
        String toNodeId,
        String materialName,
        int amount,
        MaterialCategory category
) {

    public ProcessEdge(String fromNodeId, String toNodeId, String materialName, int amount) {
        this(fromNodeId, toNodeId, materialName, amount, MaterialCategories.ITEM);
    }

    public MaterialCategory type() {
        return category;
    }
}

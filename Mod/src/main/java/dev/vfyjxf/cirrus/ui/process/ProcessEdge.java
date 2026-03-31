package dev.vfyjxf.cirrus.ui.process;

/**
 * Directed edge representing material flow between two process nodes.
 *
 * @param fromNodeId source node id
 * @param toNodeId   target node id
 * @param materialName material being transported
 * @param amount     quantity per operation
 * @param type       material type (for colour coding)
 */
public record ProcessEdge(
        String fromNodeId,
        String toNodeId,
        String materialName,
        int amount,
        ProcessNode.MaterialType type
) {

    public ProcessEdge(String fromNodeId, String toNodeId, String materialName, int amount) {
        this(fromNodeId, toNodeId, materialName, amount, ProcessNode.MaterialType.ITEM);
    }
}

package dev.vfyjxf.cirrus.ui.widget.flowchart.model;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A named group of process nodes that are visually enclosed in a box.
 * Groups can nest: a group may contain child groups.
 *
 * @param id             unique group identifier
 * @param name           display label shown in the group header
 * @param parentGroupId  parent group (null for root-level groups)
 * @param childNodeIds   node IDs directly owned by this group (not in child groups)
 * @param childGroupIds  IDs of groups nested inside this group
 * @param color          ARGB header / border colour
 */
public record ProcessGroup(
        String id,
        String name,
        @Nullable String parentGroupId,
        List<String> childNodeIds,
        List<String> childGroupIds,
        int color
) {

    /** Root group with no nesting and no child groups. */
    public ProcessGroup(String id, String name, List<String> childNodeIds) {
        this(id, name, null, childNodeIds, List.of(), 0xFF334466);
    }

    /** Root group with custom colour and no child groups. */
    public ProcessGroup(String id, String name, List<String> childNodeIds, int color) {
        this(id, name, null, childNodeIds, List.of(), color);
    }

    /** Root group with child groups. */
    public ProcessGroup(String id, String name, List<String> childNodeIds,
                        List<String> childGroupIds, int color) {
        this(id, name, null, childNodeIds, childGroupIds, color);
    }
}

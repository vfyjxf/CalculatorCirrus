package dev.vfyjxf.calculatorcirrus.api.recipe.routing;

import com.google.common.graph.Graph;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;

import java.util.ArrayDeque;

public interface RecipeGraph {

    @SuppressWarnings("UnstableApiUsage")
    static <N> MutableList<N> topologicalSort(Graph<N> graph) {
        var queue = new ArrayDeque<N>();
        var inDegree = new Object2IntArrayMap<N>();

        for (N node : graph.nodes()) {
            int degree = graph.inDegree(node);

            if (degree == 0) queue.add(node);
            else inDegree.put(node, degree);
        }

        MutableList<N> result = Lists.mutable.empty();

        while (!queue.isEmpty()) {
            N node = queue.remove();
            result.add(node);
            for (N successor : graph.successors(node)) {
                int degree = inDegree.getInt(successor);
                if (degree == 1) queue.add(successor);
                else inDegree.put(successor, degree - 1);
            }
        }

        if (result.size() != graph.nodes().size()) {
            throw new IllegalStateException("Graph contains a cycle");
        }
        return result;
    }
}

package dev.vfyjxf.cirrus.ui.calc.result;

import dev.vfyjxf.cloudlib.api.util.Namespace;

import java.util.List;

public interface TagOptionsProvider {
    List<TagCandidate> candidates(Namespace tagId);
}

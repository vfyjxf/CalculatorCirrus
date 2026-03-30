package dev.vfyjxf.cirrus.ui.widget.execution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class ExecutionOverlayControlsContractTest {

    @Test
    void transportControlsFollowPlaybackFirstOrder() {
        var descriptors = ExecutionOverlayControlsWidget.workbenchTransportDescriptors();

        assertIterableEquals(
            java.util.List.of("playback", "step", "burst", "reset"),
            descriptors.collect(ExecutionOverlayControlsWidget.WorkbenchControlDescriptor::id).toList()
        );
    }
}

package dev.vfyjxf.cirrus.network;

import dev.vfyjxf.cirrus.CirrusConstants;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerDriverProbePayloadTest {

    @Test
    void probeTypeUsesExpectedId() {
        ResourceLocation expected = ResourceLocation.fromNamespaceAndPath(CirrusConstants.modId, "server_driver_probe");
        assertEquals(expected, ServerDriverProbePayload.info.type().id());
    }
}

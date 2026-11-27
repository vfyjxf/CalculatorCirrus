package dev.vfyjxf.cirrus.network;

import dev.vfyjxf.cirrus.CirrusConstants;
import dev.vfyjxf.cloudlib.api.network.payload.ServerPayloadInfo;
import dev.vfyjxf.cloudlib.api.network.payload.ServerboundPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ServerDriverProbePayload() implements ServerboundPayload {

    public static final ServerPayloadInfo<ServerDriverProbePayload> info = ServerPayloadInfo.create(
        StreamCodec.unit(new ServerDriverProbePayload()),
        ResourceLocation.fromNamespaceAndPath(CirrusConstants.modId, "server_driver_probe")
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return info.type();
    }

    @Override
    public void handle(IPayloadContext context, ServerPlayer player) {

    }
}

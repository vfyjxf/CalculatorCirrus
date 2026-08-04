package dev.vfyjxf.cirrus.network;

import dev.vfyjxf.cirrus.CirrusConstants;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CirrusPayloads {


    public static final Logger log = LoggerFactory.getLogger("CirrusPayloads");


    public static void register(RegisterPayloadHandlersEvent event) {
        //TODO:provide a meaningful version.
        PayloadRegistrar registrar = event.registrar(CirrusConstants.modId);
        //region play 2 client

        //endregion

        //region play 2 server
        ServerDriverProbePayload.info.registerPlay(registrar);
        //endregion
    }


    private CirrusPayloads() {throw new AssertionError();}

}

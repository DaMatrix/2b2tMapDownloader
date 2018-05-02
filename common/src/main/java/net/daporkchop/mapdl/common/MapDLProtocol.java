package net.daporkchop.mapdl.common;

import net.daporkchop.lib.network.protocol.PacketProtocol;
import net.daporkchop.lib.network.protocol.packet.Message;
import net.daporkchop.lib.network.session.SocketWrapper;

/**
 * @author DaPorkchop_
 */
public class MapDLProtocol extends PacketProtocol<Message, MapSession> {
    public static final int PROTOCOL = 1;

    public MapDLProtocol() {
        super("MapDL", PROTOCOL);
    }

    @Override
    public MapSession newSession(SocketWrapper base, boolean server) {
        return new MapSession(base);
    }
}

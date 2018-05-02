package net.daporkchop.mapdl.common;

import net.daporkchop.lib.network.server.NetServer;
import net.daporkchop.lib.network.session.BaseSession;
import net.daporkchop.lib.network.session.SocketWrapper;

/**
 * @author DaPorkchop_
 */
public class MapSession extends BaseSession {
    public MapSession(SocketWrapper socket) {
        super(socket);
    }

    @Override
    public void onConnect(NetServer server) {

    }
}

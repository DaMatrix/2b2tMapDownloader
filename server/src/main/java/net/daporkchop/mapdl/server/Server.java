package net.daporkchop.mapdl.server;

import net.daporkchop.mapdl.common.INetHandlerServer;
import net.daporkchop.mapdl.common.MapDLProtocol;

/**
 * @author DaPorkchop_
 */
public class Server implements INetHandlerServer {
    public static void main(String... args) {
        System.out.println("Hello world!");
        System.out.println(MapDLProtocol.PROTOCOL);
    }
}

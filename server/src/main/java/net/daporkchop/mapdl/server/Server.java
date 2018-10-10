/*
 * Adapted from the Wizardry License
 *
 * Copyright (c) 2018-2018 DaPorkchop_ and contributors
 *
 * Permission is hereby granted to any persons and/or organizations using this software to copy, modify, merge, publish, and distribute it. Said persons and/or organizations are not allowed to use the software or any derivatives of the work for commercial use or any other means to generate income, nor are they allowed to claim this software as their own.
 *
 * The persons and/or organizations are also disallowed from sub-licensing and/or trademarking this software without explicit permission from DaPorkchop_.
 *
 * Any persons and/or organizations using this software must disclose their source code and have it publicly available, include this license, provide sufficient credit to the original authors of the project (IE: DaPorkchop_), as well as provide a link to the original project.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package net.daporkchop.mapdl.server;

import lombok.NonNull;
import net.daporkchop.lib.crypto.CryptographySettings;
import net.daporkchop.lib.crypto.cipher.symmetric.BlockCipherMode;
import net.daporkchop.lib.crypto.cipher.symmetric.BlockCipherType;
import net.daporkchop.lib.crypto.cipher.symmetric.padding.BlockCipherPadding;
import net.daporkchop.lib.crypto.sig.ec.CurveType;
import net.daporkchop.lib.encoding.compression.EnumCompression;
import net.daporkchop.lib.network.endpoint.builder.ServerBuilder;
import net.daporkchop.lib.network.endpoint.server.PorkServer;
import net.daporkchop.mapdl.common.net.MapDLProtocol;
import net.daporkchop.mapdl.common.net.MapSession;
import net.daporkchop.mapdl.server.net.MapSessionServer;
import net.daporkchop.mapdl.server.util.ServerConstants;

import java.net.InetSocketAddress;
import java.util.Scanner;

/**
 * @author DaPorkchop_
 */
public class Server implements ServerConstants {
    @NonNull
    public final PorkServer<MapSession> netServer;

    @SuppressWarnings("unchecked")
    public Server() {
        this.netServer = (PorkServer<MapSession>) new ServerBuilder()
                .setCompression(EnumCompression.GZIP)
                .setCryptographySettings(new CryptographySettings(
                        CurveType.brainpoolp192r1,
                        BlockCipherType.AES,
                        BlockCipherMode.CBC,
                        BlockCipherPadding.PKCS7
                ))
                .setAddress(new InetSocketAddress(NETWORK_PORT))
                .setProtocol(new MapDLProtocol(MapSessionServer::new))
                .build();
    }

    public static void main(String... args) {
        Server server = new Server();

        {
            Scanner scanner = new Scanner(System.in);
            while (!scanner.nextLine().trim().isEmpty())    {
            }
            scanner.close();
        }

        server.shutdown();
    }

    public void shutdown()  {
        if (this.netServer.isRunning()) {
            this.netServer.close("shutting down");
        }
    }
}

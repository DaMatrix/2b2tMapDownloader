/*
 * Adapted from the Wizardry License
 *
 * Copyright (c) 2018-2019 DaPorkchop_ and contributors
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

import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import net.daporkchop.lib.common.misc.file.PFiles;
import net.daporkchop.lib.hash.util.Digest;
import net.daporkchop.lib.logging.LogAmount;
import net.daporkchop.lib.network.endpoint.PServer;
import net.daporkchop.lib.network.endpoint.builder.ServerBuilder;
import net.daporkchop.lib.network.tcp.TCPEngine;
import net.daporkchop.mapdl.server.net.game.FullHTTPFramer;
import net.daporkchop.mapdl.server.net.game.ServerSession;
import net.daporkchop.mapdl.server.net.web.HTTPSession;
import net.daporkchop.mapdl.server.net.web.LightHTTPFramer;
import net.daporkchop.mapdl.server.storage.user.UserStorage;
import net.daporkchop.mapdl.server.util.ServerConstants;
import net.daporkchop.mapdl.server.util.process.ProcessLauncher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * @author DaPorkchop_
 */
@Getter
@Accessors(fluent = true)
public class Server implements ServerConstants, AutoCloseable {
    public static void main(String... args) {
        { //init logging
            File logDir = new File("logs/");
            PFiles.ensureDirectoryExists(logDir);
            File logFile = new File(logDir, "latest.log");
            /*if (logFile.exists() && !logFile.renameTo(new File(logDir, String.format(
                        "%s.log",
                        new SimpleDateFormat("yy.MM.dd HH.mm.ss").format(Instant.ofEpochMilli(logFile.lastModified()).)
                )))) {
                throw new IllegalStateException("Unable to rename old log file!");
            }*/
            logger.enableANSI().redirectStdOut().addFile(logFile, LogAmount.DEBUG).setLogAmount(LogAmount.DEBUG);
        }

        try (Scanner scanner = new Scanner(System.in);
             Server server = new Server(new File("."), scanner)) {
            scanner.nextLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected final ProcessLauncher processLauncher = new ProcessLauncher(10L);
    protected final File                   root;
    protected final PServer<HTTPSession>   httpServer;
    protected final PServer<ServerSession> gameServer;

    protected final byte[]      salt;
    protected final UserStorage users;

    private Server(@NonNull File root, @NonNull Scanner scanner) throws IOException {
        this.root = PFiles.ensureDirectoryExists(root);

        {
            File saltFile = new File(root, "salt");
            if (!saltFile.exists()) {
                logger.info("Please enter a salt for user passwords: ");
                try (OutputStream out = new FileOutputStream(saltFile)) {
                    out.write(scanner.nextLine().getBytes(StandardCharsets.UTF_8));
                }
            }
            this.salt = Digest.WHIRLPOOL.hash(saltFile).getHash();
        }

        this.users = new UserStorage(this);

        logger.info("Starting web server...");
        this.httpServer = ServerBuilder.of(() -> new HTTPSession(this))
                                       .engine(TCPEngine.builder().framerFactory(LightHTTPFramer::new).build())
                                       .bind("0.0.0.0", 8080)
                                       .build();
        logger.success("Web server started.");
        logger.info("Starting game server...");
        this.gameServer = ServerBuilder.of(() -> new ServerSession(this))
                                       .engine(TCPEngine.builder().framerFactory(FullHTTPFramer::new).build())
                                       .bind("0.0.0.0", 8081)
                                       .build();
        logger.success("Game server started.");
    }

    @Override
    public void close() throws IOException {
        this.gameServer.closeAsync().addListener(() -> logger.success("Game server closed."));
        this.httpServer.closeAsync().addListener(() -> logger.success("HTTP server closed."));
        this.processLauncher.shutdown();

        this.users.close();
    }
}

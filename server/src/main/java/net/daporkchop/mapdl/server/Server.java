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
import lombok.experimental.Accessors;
import net.daporkchop.lib.common.misc.file.PFiles;
import net.daporkchop.lib.logging.LogAmount;
import net.daporkchop.lib.network.endpoint.PServer;
import net.daporkchop.lib.network.endpoint.builder.ServerBuilder;
import net.daporkchop.lib.network.tcp.TCPEngine;
import net.daporkchop.mapdl.server.http.HTTPSession;
import net.daporkchop.mapdl.server.http.LightHTTPFramer;
import net.daporkchop.mapdl.server.util.ServerConstants;
import net.daporkchop.mapdl.server.util.process.ProcessLauncher;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * @author DaPorkchop_
 */
@Getter
@Accessors(fluent = true)
public class Server implements ServerConstants {
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
            logger.enableANSI().addFile(logFile, LogAmount.DEBUG).setLogAmount(LogAmount.DEBUG);
        }

        Server server;
        try {
            server = new Server();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (Scanner s = new Scanner(System.in))    {
            s.nextLine();
        }

        server.shutdown();
    }

    protected final ProcessLauncher processLauncher = new ProcessLauncher(10L);
    protected final File                 repoDir;
    protected final PServer<HTTPSession> httpServer;
    protected final PServer<?> gameServer = null; //TODO

    private Server() throws IOException {
        this.repoDir = new File("repo/");
        if (!this.repoDir.exists()) {
            logger.info("Creating repo...");
            this.processLauncher.submit(
                    (stdout, stderr, exitCode) -> logger.success(stdout.toString(StandardCharsets.UTF_8)),
                    PFiles.ensureDirectoryExists(this.repoDir),
                    "git", "init"
            );
        }
        logger.info("Starting web server...");
        this.httpServer = ServerBuilder.of(HTTPSession::new)
                                       .engine(TCPEngine.builder().framerFactory(LightHTTPFramer::new).build())
                                       .bind("0.0.0.0", 8080)
                                       .build();
        logger.success("Web server started.");
    }

    public void shutdown() {
        this.processLauncher.shutdown();
        this.httpServer.closeAsync().addListener(() -> logger.success("HTTP server closed."));
    }
}

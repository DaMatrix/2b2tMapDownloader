/*
 * Adapted from The MIT License (MIT)
 *
 * Copyright (c) 2018-2020 DaPorkchop_
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software
 * is furnished to do so, subject to the following conditions:
 *
 * Any persons and/or organizations using this software must include the above copyright notice and this permission notice,
 * provide sufficient credit to the original authors of the project (IE: DaPorkchop_), as well as provide a link to the original project.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.daporkchop.mapdl.server;

import com.google.gson.reflect.TypeToken;
import io.netty.util.concurrent.Future;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import net.daporkchop.lib.binary.oio.reader.UTF8FileReader;
import net.daporkchop.lib.binary.oio.writer.UTF8FileWriter;
import net.daporkchop.lib.common.function.io.IOConsumer;
import net.daporkchop.lib.common.misc.file.PFiles;
import net.daporkchop.lib.common.system.PlatformInfo;
import net.daporkchop.lib.http.impl.netty.server.NettyHttpServer;
import net.daporkchop.lib.http.server.HttpServer;
import net.daporkchop.lib.logging.LogAmount;
import net.daporkchop.mapdl.common.User;
import net.daporkchop.mapdl.server.util.process.ProcessLauncher;
import net.daporkchop.mapdl.server.web.ServerRequestHandler;
import net.daporkchop.mapdl.server.world.World;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import static net.daporkchop.lib.logging.Logging.*;
import static net.daporkchop.mapdl.server.util.ServerConstants.*;

/**
 * @author DaPorkchop_
 */
@Getter
@Accessors(fluent = true)
public class Server implements AutoCloseable {
    public static void main(String... args) {
        { //init logging
            File logFile = new File(PFiles.ensureDirectoryExists(new File("logs/")), "latest.log");
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
    protected final File root;

    protected final File              usersFile;
    protected final Map<String, User> users;

    //this is okay performance-wise since Integer caches all values -128 to 127 internally, and we only need -1 to 1
    protected final Map<Integer, World> worlds;

    protected final HttpServer server;

    private Server(@NonNull File root, @NonNull Scanner scanner) throws IOException {
        try {
            logger.info("Starting 2b2tMapDownloader server...");

            Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
                logger.alert("Uncaught exception in thread \"%s\":", e, thread);
                try {
                    this.close();
                } catch (IOException ioe) {
                    logger.alert("IO exception while aborting server:", ioe);
                } finally {
                    System.exit(1);
                }
            });

            this.root = PFiles.ensureDirectoryExists(root);

            //load users
            this.users = new ConcurrentHashMap<>();
            this.usersFile = new File(root, "users.json");
            if (PFiles.checkFileExists(this.usersFile)) {
                logger.info("Loading users...");
                try (Reader src = new UTF8FileReader(this.usersFile)) {
                    this.users.putAll(GSON_ALL.fromJson(src, new TypeToken<Map<String, User>>() {}.getType()));
                }
            }

            this.server = new NettyHttpServer(logger.channel("HTTP"))
                    .handler(new ServerRequestHandler(this));

            Future<?> bindFuture = this.server.bind(new InetSocketAddress(8080)).addListener(f -> {
                if (!f.isSuccess()) {
                    logger.alert("Failed to bind to port 8080!", f.cause());
                    System.exit(1);
                }
            });

            logger.info("Loading worlds...");
            Map<Integer, World> worlds = new HashMap<>();
            for (int i = -1; i <= 1; i++) {
                worlds.put(i, new World(this, i));
            }
            this.worlds = Collections.unmodifiableMap(worlds);

            bindFuture.syncUninterruptibly();
        } catch (Exception e) {
            logger.alert("Encountered exception while starting server:", e);

            try {
                this.close();
            } catch (IOException ioe) {
                logger.alert("IO exception while aborting server:", ioe);
            }
            System.exit(1);
            throw new RuntimeException(e);
        }
        logger.success("Server started, we are ready to go!");
    }

    @Override
    public void close() throws IOException {
        this.server.close();
        this.processLauncher.shutdown();

        this.saveUsers();

        this.worlds.values().forEach((IOConsumer<World>) World::close);
    }

    public void saveUsers() throws IOException {
        synchronized (this.usersFile) {
            try (Writer dst = new UTF8FileWriter(PFiles.ensureFileExists(this.usersFile))) {
                GSON_ALL.toJson(this.users, dst);
                dst.append(PlatformInfo.OPERATING_SYSTEM.lineEnding());
            }
        }
    }
}

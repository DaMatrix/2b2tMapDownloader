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

package net.daporkchop.mapdl.server.repo;

import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import net.daporkchop.lib.binary.stream.DataIn;
import net.daporkchop.lib.common.misc.file.PFiles;
import net.daporkchop.lib.encoding.basen.Base58;
import net.daporkchop.mapdl.server.Server;
import net.daporkchop.mapdl.server.util.ServerConstants;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Contains the commit history of the repo.
 *
 * @author DaPorkchop_
 */
@Getter
@Accessors(fluent = true)
public class History implements ServerConstants {
    protected static final Pattern COMMIT_PARSE_PATTERN = Pattern.compile("([0-9a-f]{40}) (\\S+) '([^']+)' '([^']+)' ([^\"]+)");

    protected final Server server;
    protected final File   root;
    protected final ReadWriteLock lock = new ReentrantReadWriteLock();
    protected final Map<String, Commit> lookup = new HashMap<>();
    protected final List<Commit> commits = new ArrayList<>();

    public History(@NonNull Server server) {
        this(server, new File(server.root(), "repo/"));
    }

    public History(@NonNull Server server, @NonNull File root) {
        this.server = server;
        this.root = root;

        if (!this.root.exists()) {
            logger.info("Creating repo...");
            this.server.processLauncher().submit(
                    (stdout, stderr, exitCode) -> logger.success(stdout.toString(StandardCharsets.UTF_8)),
                    PFiles.ensureDirectoryExists(this.root),
                    "git", "init"
            );
        }

        this.refresh();
    }

    public void refresh() {
        this.server.processLauncher().submit(
                (stdout, stderr, exitCode) -> {
                    if (exitCode != 0)  {
                        throw new IllegalStateException(String.format("Log command exited with status: %d", exitCode));
                    }
                        this.doRefresh(stdout.toString(StandardCharsets.UTF_8));
                },
                this.root,
                "git", "log", "--pretty=format:\"%H %an '%ad' '%ar' %s\"", "--date=iso"
        );
    }

    protected void doRefresh(@NonNull String data)  {
        this.lock.writeLock().lock();
        try {
            this.lookup.clear();
            this.commits.clear();

            List<Commit> tempList = new LinkedList<>(); //this makes inserting to the front of the list much faster than with arraylist
            Matcher matcher = COMMIT_PARSE_PATTERN.matcher(data);
            while (matcher.find())  {
                Commit commit = new Commit(
                        matcher.group(1),
                        UUID.fromString(matcher.group(2)),
                        matcher.group(3),
                        matcher.group(4)
                );
                try (DataIn in = DataIn.wrap(ByteBuffer.wrap(Base58.decodeBase58(matcher.group(5))))) {
                    commit.read(in);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                tempList.add(0, commit);
            }

            this.commits.addAll(tempList);
            tempList.forEach(commit -> this.lookup.put(commit.hash, commit));
        } finally {
            this.lock.writeLock().unlock();
        }
    }
}

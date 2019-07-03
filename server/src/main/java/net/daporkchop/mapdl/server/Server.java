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

import net.daporkchop.lib.common.misc.file.PFiles;
import net.daporkchop.lib.logging.LogAmount;
import net.daporkchop.mapdl.server.util.ServerConstants;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

/**
 * @author DaPorkchop_
 */
public class Server implements ServerConstants {
    public static void main(String... args) {
        { //init logging
            File logDir = new File("./logs/");
            PFiles.ensureDirectoryExists(logDir);
            File logFile = new File(logDir, "latest.log");
            if (logFile.exists() && !logFile.renameTo(new File(logDir, String.format(
                        "%s.log",
                        new SimpleDateFormat("yy.MM.dd HH.mm.ss").format(Instant.ofEpochMilli(logFile.lastModified()))
                )))) {
                throw new IllegalStateException("Unable to rename old log file!");
            }
            logger.enableANSI().addFile(logFile, LogAmount.DEBUG);
        }

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
    }
}

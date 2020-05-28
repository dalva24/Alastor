/* 
 *  ALASTOR Massively Concurrent File Transfer System
 *  Copyright (C) 2020 Dariel Valdano
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.dalva.alastor.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import picocli.CommandLine;
import picocli.CommandLine.Option;

/**
 * Landing for CLI "serve" command
 * @author Dalva
 */
@CommandLine.Command(name = "serve",
        description = "Become server and serve files from working directory")
public class EntryServer implements Callable<Integer> {

  @Option(names = {"-p", "--port"}, description = "Port to listen to (default 41457)")
  private int port = 41457;

  @Option(names = {"-d", "--dir"}, description = "Serve directory (default {workdir}/files/)")
  private String serveDir = "./files/";

  @CommandLine.Parameters(index = "0", defaultValue = "./clients.keylist", description = {"Trusted Keys (default {workdir}/clients.keylist)", "newline-separated list of trusted client keys"})
  private String trustedKeysFile;

  private Server server;

  @Override
  public Integer call() throws Exception {
    System.out.println("ALASTOR SERVER"); //todo version info
    System.out.println("Serving directory: " + serveDir);
    System.out.println("Configuration looks good, igniting Alastor...");
    
    Auth.setup(trustedKeysFile);
    ServerFileHandler.setPrefix(serveDir);
    
    start();
    
    System.out.println("");
    System.out.println(CommandLine.Help.Ansi.AUTO.string(
            "@|fg(208) Welcome to our world; The World of Pandemonium, The Garden of Battle, my Flame Haze.|@"
            + "@|fg(243)  - Alastor (1E15-22:44)|@"));
    System.out.println("");

    blockUntilShutdown();

    return 0;
  }

  public void start() throws IOException {
    server = ServerBuilder.forPort(port)
            .addService(new net.dalva.alastor.server.AlastorImpl())
            .build()
            .start();
    System.out.println("Server started. Listening at port: " + port);
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        System.out.println("Shutting down Alastor server....");
        try {
          EntryServer.this.stop();
        } catch (InterruptedException e) {
          e.printStackTrace(System.err);
        }
        System.out.println("Server shut down successfully.");
      }
    });
  }

  public void stop() throws InterruptedException {
    if (server != null) {
      server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
    }
  }

  /**
   * Await termination on the main thread since the grpc library uses daemon threads.
   * @throws java.lang.InterruptedException
   */
  public void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }

}

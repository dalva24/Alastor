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
package net.dalva.alastor.client;

import java.util.concurrent.Callable;
import lombok.Data;
import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Landing for CLI "get" command
 * @author Dalva
 */
@Data
@CommandLine.Command(name = "get",
        description = "Download a file from Alastor server")
public class EntryClient implements Callable<Integer> {

  @Option(names = {"-n", "--notls"}, description = "Don't use TLS (TLS used by default)")
  private boolean notls = false;

  @Option(names = {"-c", "--connections"}, description = "Number of connections used (default 50)")
  private int conns = 50;

  @Option(names = {"-k", "--chunksize"}, description = "Chunk Size in kB (default 50kB)")
  private int chunkSizeInKB = 50;

  @Parameters(index = "0", description = {"example.com, 10.8.0.1:5555, 127.0.0.1:41457, ...", "Port 443 by default, or 80 when -n is set"})
  private String address;

  @Parameters(index = "1", description = {"arbitrary length string", "Must be in the server's trusted keylist"})
  private String clientKey;

  @Parameters(index = "2", description = "file_to_download.tar.gz, ...")
  private String filename;

  @Override
  public Integer call() throws Exception {
    System.out.println("ALASTOR CLIENT"); //todo version info
    System.out.println("Will be using " + conns + " parallel connections with " + chunkSizeInKB + "kB chunk size");
    System.out.print("Connecting to " + address);
    if (notls) {
      System.out.println(" without TLS");
      System.out.println(Ansi.AUTO.string("@|bold,red,underline WARNING: TLS DISABLED|@"));
    } else {
      System.out.println(" using TLS");
    }
    
    FlameWeaver.weave(this);

    return 0;
  }

  

}

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
package net.dalva.alastor;

import net.dalva.alastor.client.EntryClient;
import net.dalva.alastor.server.EntryServer;
import java.util.concurrent.Callable;
import org.fusesource.jansi.AnsiConsole;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 *
 * @author Dalva
 */
@Command(name = "alastor", mixinStandardHelpOptions = true, version = Main.VERSION_FULL,
        description = {"Massively Concurrent File Transfer System", "Please choose a command, either to become server or download files."},
        subcommands = {
          EntryServer.class,
          EntryClient.class})
public class Main implements Callable<Integer> {
  
  public static final String VERSION = "v0.2";
  public static final String VERSION_FULL = "ALASTOR v0.2";

  @Spec
  CommandSpec spec;

  public static void main(String... args) {
    
    AnsiConsole.systemInstall();
    
    int exitCode = new CommandLine(new Main()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public Integer call() throws Exception { //called without subcommand
    spec.commandLine().printVersionHelp(System.err);
    spec.commandLine().usage(System.err);
    return 0;
  }

}

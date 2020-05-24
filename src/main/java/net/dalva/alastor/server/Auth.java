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

import java.io.IOException;
import java.util.ArrayList;
import net.dalva.alastor.FileHandler;

/**
 * Handles Client Authentication-related tasks
 * @author Dalva
 */
public class Auth {
  
  private static ArrayList<String> KEY_LIST = new ArrayList();
  
  /**
   * Reads a keyfile that contains newline-separated list of trusted client keys
   * @param keyfile input keyfile
   * @throws IOException 
   */
  public static void setup(String keyfile) throws IOException {
    
    FileHandler file = new FileHandler(keyfile, true);
    
    while (true) {
      String line = file.readLine();
      if (line == null) {
        break;
      }
      if (!line.startsWith("#")) {
        KEY_LIST.add(line);
      }
    }
    
  }
  
  /**
   * Check whether this key is trusted
   * @param clientKey The key to be checked
   * @return true if is in the trusted key list
   */
  public static boolean checkTrusted(String clientKey) {
    return KEY_LIST.stream().anyMatch((key) -> (key.equals(clientKey)));
  }
  
}

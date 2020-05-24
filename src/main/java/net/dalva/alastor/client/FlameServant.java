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

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.dalva.alastor.grpc.AlastorGrpc;

/**
 * This class is responsible for individual connection. To be used in a thread pool.
 *
 * @author Dalva
 */
public class FlameServant extends Thread {

  private final ManagedChannel channel;
  private final AlastorGrpc.AlastorBlockingStub blockingStub;

  @Override
  public void run() {
    
    System.out.println("Servant " + Thread.currentThread().getName() + " running");
    
    // The main loop
    while (true) {
      int nextChunk = FlameWeaver.getNextChunk();
      if (nextChunk == 0) { // no more chunks to download, kill servant.
        break;
      }
      
      FlameWeaver.submitChunk(FlameWeaver.dataQuery(blockingStub, nextChunk), nextChunk);
      
    }
    
    System.out.println("Servant " + Thread.currentThread().getName() + " has completed their services");
    
    try {
      channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      Logger.getLogger(FlameServant.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  /**
   * Construct the worker, initialize its own channel and stub.
   *
   * @param isNotTls
   * @param address
   */
  public FlameServant(boolean isNotTls, String address) {

    if (isNotTls) {
      channel = ManagedChannelBuilder.forTarget(address)
              .usePlaintext()
              .build();
    } else {
      channel = ManagedChannelBuilder.forTarget(address)
              .build();

    }
    blockingStub = AlastorGrpc.newBlockingStub(channel);
  }
}

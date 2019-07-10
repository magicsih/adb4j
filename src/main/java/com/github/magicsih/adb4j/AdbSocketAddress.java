/**
 * 
 */
package com.github.magicsih.adb4j;

import java.net.InetSocketAddress;

/**
 * @author sih
 *
 */
public class AdbSocketAddress extends InetSocketAddress {

  private static final long serialVersionUID = -8688399406012201183L;

  public AdbSocketAddress(String hostname, int port) {
    super(hostname, port);
  }
  
  public AdbSocketAddress() {
    super("localhost", 5037);
  }
  
  public AdbSocketAddress(int port) {
    super("localhost", port);
  }
}

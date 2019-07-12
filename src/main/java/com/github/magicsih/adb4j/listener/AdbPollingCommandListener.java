/**
 * 
 */
package com.github.magicsih.adb4j.listener;

import com.github.magicsih.adb4j.AdbDevice;

/**
 * @author sih
 *
 */
public interface AdbPollingCommandListener {

  void poll(AdbDevice device, String name, String command, String data);
  
  void disconnect(AdbDevice device);
  
}

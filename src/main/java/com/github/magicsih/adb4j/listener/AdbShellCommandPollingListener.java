/**
 * 
 */
package com.github.magicsih.adb4j.listener;

import com.github.magicsih.adb4j.AdbDevice;

/**
 * @author sih
 *
 */
public interface AdbShellCommandPollingListener {

  void poll(AdbDevice device, String name, String command, String data);
  
  void disconnect(AdbDevice device);
  
}

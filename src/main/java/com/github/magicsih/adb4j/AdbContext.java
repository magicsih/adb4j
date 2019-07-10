package com.github.magicsih.adb4j;

import java.util.List;
import com.github.magicsih.adb4j.exception.AdbNotFoundException;

public interface AdbContext {

  void init() throws AdbNotFoundException;
  
  void init(AdbSocketAddress addr) throws AdbNotFoundException;
  
  AdbDevice getAdpcDeviceById(String deviceId);
  
  List<String> getOnlineDeviceIds();
  
  List<AdbDevice> getAdpcDevices();

  void shutdown();
}

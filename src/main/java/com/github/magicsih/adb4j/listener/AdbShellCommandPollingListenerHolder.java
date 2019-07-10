package com.github.magicsih.adb4j.listener;
/**
 * 
 */

/**
 * @author sih
 *
 */
public class AdbShellCommandPollingListenerHolder {

  private final String name;
  private final String command;
  private AdbShellCommandPollingListener listener;
  private final boolean keepAlive;
  
  public AdbShellCommandPollingListenerHolder(String name, String command,
      AdbShellCommandPollingListener listener, boolean keepAlive) {
    super();
    this.name = name;
    this.command = command;
    this.listener = listener;
    this.keepAlive = keepAlive;
  }

  public String getName() {
    return name;
  }

  public String getCommand() {
    return command;
  }

  public AdbShellCommandPollingListener getListener() {
    return listener;
  }
  
  public void clearListener() {
    if(this.listener != null) this.listener = null;
  }
  
  public boolean isKeepAlive() {
    return keepAlive;
  }

  @Override
  public String toString() {
    return "AdbShellCommandPollingListenerHolder [name=" + name + ", command=" + command
        + ", listener=" + listener + ", keepAlive=" + keepAlive + "]";
  }
}

package com.github.magicsih.adb4j.listener;
/**
 * 
 */

/**
 * @author sih
 *
 */
public class AdbPollingCommand {

  private final String name;
  private final String command;
  private AdbPollingCommandListener listener;
  private final boolean keepAlive;

  public AdbPollingCommand(String name, String command,
      AdbPollingCommandListener listener, boolean keepAlive) {
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

  public AdbPollingCommandListener getListener() {
    return listener;
  }

  public void clearListener() {
    if(this.listener != null) this.listener = null;
  }

  public boolean isKeepAlive() {    
    return keepAlive;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((command == null) ? 0 : command.hashCode());
    result = prime * result + (keepAlive ? 1231 : 1237);
    result = prime * result + ((name == null) ? 0 : name.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    AdbPollingCommand other = (AdbPollingCommand) obj;
    if (command == null) {
      if (other.command != null)
        return false;
    } else if (!command.equals(other.command))
      return false;
    if (keepAlive != other.keepAlive)
      return false;
    if (name == null) {
      if (other.name != null)
        return false;
    } else if (!name.equals(other.name))
      return false;
    return true;
  }

  @Override
  public String toString() {
    return "AdbPollingCommand [name=" + name + ", command=" + command + ", listener=" + listener
        + ", keepAlive=" + keepAlive + "]";
  }
}

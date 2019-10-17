# adb4j

## Quick Start
### Maven Dependency
<pre>
<code>
&lt;dependency&gt;
    &lt;groupId&gt;com.github.magicsih&lt;/groupId&gt;
    &lt;artifactId&gt;adb4j&lt;/artifactId&gt;
    &lt;version&gt;0.0.1-SNAPSHOT&lt;/version&gt;      
&lt;/dependency&gt;
</code>
</pre>

<pre>
<code>
AdbContext adbContext = new DefaultAdbContext(
    Executors.newFixedThreadPool(10, new CustomizableThreadFactory("ADBNetwork-")),
    Executors.newFixedThreadPool(10, new CustomizableThreadFactory("AdpcCallback-"))
    );
adbContext.init();

String packageName = "com.sec.android.app.clockpackage";

AdbDevice s8 = adbContext.getAdpcDeviceById("{your-android-device-id}");
s8.registerPollingListener(STAT_INFO_TOP, "top -p `pidof " + packageName + "` -o ARGS,%CPU -n 1\n", this, false);
s8.registerPollingListener("meminfo", "dumpsys meminfo " + packageName + "\n", this, false);
s8.start();

Thread.currentThread().join();   
adbContext.shutdown();
</code>
</pre>

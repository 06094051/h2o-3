package water.api;

import water.*;
import water.util.LinuxProcFileReader;
import water.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class LogsHandler extends Handler {
  private static class GetLogTask extends DTask<GetLogTask> {
    public String name;
    public String log;

    public boolean success = false;

    public GetLogTask() {
      log = null;
    }

    @Override public void compute2() {
      String logPathFilename = "/undefined";        // Satisfy IDEA inspection.
      try {
        if (name == null || name.equals("default")) {
          name = "debug";
        }

        if (name.equals("stdout") || name.equals("stderr")) {
          LinuxProcFileReader lpfr = new LinuxProcFileReader();
          lpfr.read();
          if (! lpfr.valid()) {
            log = "This option only works for Linux hosts";
          }
          else {
            String pid = lpfr.getProcessID();
            String fdFileName = "/proc/" + pid + "/fd/" + (name.equals("stdout") ? "1" : "2");
            File f = new File(fdFileName);
            logPathFilename = f.getCanonicalPath();
            if (logPathFilename.startsWith("/dev")) {
              log = "Unsupported when writing to console";
            }
            if (logPathFilename.startsWith("socket")) {
              log = "Unsupported when writing to a socket";
            }
            if (logPathFilename.startsWith("pipe")) {
              log = "Unsupported when writing to a pipe";
            }
            if (logPathFilename.equals(fdFileName)) {
              log = "Unsupported when writing to a pipe";
            }
            Log.trace("LogPathFilename calculation: " + logPathFilename);
          }
        }
        else if (  name.equals("trace")
                || name.equals("debug")
                || name.equals("info")
                || name.equals("warn")
                || name.equals("error")
                || name.equals("fatal")) {
          name = water.util.Log.getLogFileName(name);
          logPathFilename = Log.getLogDir() + File.separator + name;
        }
        else {
          throw new IllegalArgumentException("Illegal log file name requested (try 'default')");
        }

        if (log == null) {
          File f = new File(logPathFilename);
          if (!f.exists()) {
            throw new IllegalArgumentException("File " + f + " does not exist");
          }
          if (!f.canRead()) {
            throw new IllegalArgumentException("File " + f + " is not readable");
          }

          BufferedReader reader = new BufferedReader(new FileReader(f));
          String line;
          StringBuilder sb = new StringBuilder();

          line = reader.readLine();
          while (line != null) {
            sb.append(line);
            sb.append("\n");
            line = reader.readLine();
          }
          reader.close();

          log = sb.toString();
        }

        success = true;
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }

      tryComplete();
    }

    @Override public byte priority() {
      return H2O.MIN_HI_PRIORITY;
    }
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public LogsV3 fetch(int version, LogsV3 s) {
    int nodeidx = s.nodeidx;
    if (nodeidx == -1) {
      nodeidx = H2O.SELF.index();
    }
    if ((nodeidx < 0) || (nodeidx >= H2O.CLOUD.size())) {
      throw new IllegalArgumentException("node does not exist");
    }

    String filename = s.name;
    if (filename != null) {
      if (filename.contains(File.separator)) {
        throw new IllegalArgumentException("filename may not contain File.separator character");
      }
    }

    H2ONode node = H2O.CLOUD._memary[nodeidx];
    GetLogTask t = new GetLogTask();
    t.name = filename;
    Log.trace("GetLogTask starting to node " + nodeidx + "...");
    new RPC<>(node, t).call().get();
    Log.trace("GetLogTask completed to node " + nodeidx);

    if (! t.success) {
      throw new RuntimeException("GetLogTask failed");
    }

    s.log = t.log;

    return s;
  }
}

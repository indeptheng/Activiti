package org.activiti.document;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author Jonathan Mulieri
 */
public class ShellCommandRunner {
  public static class Result implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ShellCommandRunner.Result.class);

    public BufferedReader    inputReader;
    public BufferedReader    errorReader;
    public int               exitValue;
    private Process          process;
    public Result(Process p) {
      this.process = p;
      this.exitValue  = p.exitValue();
      this.inputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      this.errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
    }

    @Override
    public void close() throws Exception {
      if (process != null) {
        process.getInputStream().close();
        process.getOutputStream().close();
        process.getErrorStream().close();
      }
    }

    public String getOutput() {
      StringBuilder builder = new StringBuilder();
      readFromStream(builder, "STDOUT", inputReader);
      readFromStream(builder, "STDERR", errorReader);
      return builder.toString();
    }

    private void readFromStream(StringBuilder builder, String streamName, BufferedReader reader) {
      String line;
      boolean first = true;
      try {
        while ((line = reader.readLine()) != null) {
          if (first) {
            first = false;
            builder.append(streamName + "\n");
          }
          builder.append(line + "\n");
        }
      } catch (IOException e) {
        LOG.error("Error reading from stream {}", e);
      }
    }
  }

  public static Result shellOut(String command) throws Exception {
    Process p = Runtime.getRuntime().exec(new String[]{"bash", "-c", command});
    p.waitFor();
    return new Result(p);
  }
}
package saros.net.stream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// TODO JavaDoc
public interface ByteStream {

  public InputStream getInputStream() throws IOException;

  public OutputStream getOutputStream() throws IOException;

  public void close() throws IOException;

  public int getReadTimeout() throws IOException;

  public void setReadTimeout(int timeout) throws IOException;

  public Object getLocalAddress();

  public Object getRemoteAddress();

  public StreamMode getMode();

  public String getId();
}

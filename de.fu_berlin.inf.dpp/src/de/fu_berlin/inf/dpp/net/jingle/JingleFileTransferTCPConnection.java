package de.fu_berlin.inf.dpp.net.jingle;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import org.apache.log4j.Logger;

import de.fu_berlin.inf.dpp.net.internal.JingleFileTransferData;

public abstract class JingleFileTransferTCPConnection {

	private static Logger logger = Logger
			.getLogger(JingleFileTransferTCPConnection.class);

	/* transfer information */
	protected JingleFileTransferData receiveTransferData;
	
	protected IJingleFileTransferListener listener;

	protected void sendFileListData(OutputStream output,
			String file_list_content) throws IOException {
		ObjectOutputStream oo = new ObjectOutputStream(output);

		oo.writeObject(file_list_content);
		oo.flush();
	}

	protected void sendMetaData(OutputStream output, JingleFileTransferData data)
			throws IOException {
		ObjectOutputStream oo = new ObjectOutputStream(output);
		// ObjectInputStream ii = new ObjectInputStream(socket
		// .getInputStream());

		oo.writeObject(data);
		oo.flush();
	}

	protected void receiveFileListData(InputStream input) throws IOException,
			ClassNotFoundException {
		logger.debug("receive file List");
		ObjectInputStream ii = new ObjectInputStream(input);

		String fileListData = (String) ii.readObject();

		/* inform listener. */
		listener.incommingFileList(fileListData, receiveTransferData.sender);

		// System.out.println("File List Data : " + fileListData.toString());
	}

	protected void receiveMetaData(InputStream input) throws IOException,
			ClassNotFoundException {
		// ObjectOutputStream oo = new ObjectOutputStream(
		// socket.getOutputStream());
		ObjectInputStream ii = new ObjectInputStream(input);

		JingleFileTransferData meta = (JingleFileTransferData) ii.readObject();
		this.receiveTransferData = meta;

		// ii.close();

	}
}

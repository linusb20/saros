package de.fu_berlin.inf.dpp.net.jingle.transmitter;

import java.awt.Robot;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import org.apache.log4j.Logger;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smackx.filetransfer.FileTransferManager;
import org.jivesoftware.smackx.filetransfer.IBBTransferNegotiator;
import org.jivesoftware.smackx.filetransfer.OutgoingFileTransfer;
import org.jivesoftware.smackx.filetransfer.Socks5TransferNegotiatorManager;

import de.fu_berlin.inf.dpp.net.internal.JingleFileTransferData;
import de.fu_berlin.inf.dpp.net.internal.JingleFileTransferData.FileTransferType;
import de.fu_berlin.inf.dpp.net.internal.XMPPChatTransmitter.FileTransferData;
import de.fu_berlin.inf.dpp.net.jingle.IFileTransferTransmitter;
import de.fu_berlin.inf.dpp.net.jingle.IJingleFileTransferListener;
import de.fu_berlin.inf.dpp.net.jingle.JingleFileTransferProcessMonitor;
import de.fu_berlin.inf.dpp.net.jingle.JingleSessionException;

public class FileTransferTCPTransmitter implements IFileTransferTransmitter,
		Runnable {

	private static Logger logger = Logger
			.getLogger(FileTransferTCPTransmitter.class);

	private InetAddress localHost;
	private InetAddress remoteHost;
	private int localPort;
	private int remotePort;
	public static final int tileWidth = 25;
	private boolean on = true;
	private boolean transmit = false;
	private boolean receive = false;

	/* transfer information */
	private JingleFileTransferData[] transferData;
	private JingleFileTransferProcessMonitor monitor;
	private IJingleFileTransferListener listener;

	/* transfer information */
	private JingleFileTransferData receiveTransferData;

	// private FileTransferTCPTransmitter(int localPort,
	// InetAddress remoteHost, int remotePort) {
	//
	// this.localPort = localPort;
	// this.remoteHost = remoteHost;
	// this.remotePort = remotePort;
	//
	// transmit = true;
	//
	// }

	public FileTransferTCPTransmitter(int localPort, InetAddress remoteHost,
			int remotePort, JingleFileTransferData[] transferData,
			JingleFileTransferProcessMonitor monitor) {

		this.localPort = localPort;
		this.remoteHost = remoteHost;
		this.remotePort = remotePort;

		transmit = true;
		this.transferData = transferData;
		this.monitor = monitor;
	}

	public void run() {
		start();
	}

	public void start() {

		Socket socket = null;
		try {

			try {
				// TODO: Socket create methode mit time out einfügen

				/* Übertragung zwischen zwei Partnern. */
				socket = new Socket(remoteHost, remotePort);
			} catch (SocketException se) {
				Thread.sleep(500);
				socket = new Socket(remoteHost, remotePort);
			}

			InputStream input = socket.getInputStream();
			OutputStream output = socket.getOutputStream();

			while (on) {
				
				/**
				 * Time out für offen verbindung ohne daten einbauen. 
				 */
				if (transmit) {

					/* send file number. */
					// OutputStream os = socket.getOutputStream();
					if (transferData.length > 0) {
						logger.debug("send transfer number : "
								+ transferData.length);
						output.write(transferData.length);
					}
					for (int i = 0; i < transferData.length; i++) {

						/* testing. only */
						// sendFile(socket, "/home/troll/Saros_DPP_1.0.2.jar");
						/* send file meta data */
						logger.debug("send meta data for : "
								+ transferData[i].file_project_path);
						sendMetaData(output, transferData[i]);

						if (transferData[i].type == FileTransferType.FILELIST_TRANSFER) {
							sendFileListData(output,
									transferData[i].file_list_content);
							/*
							 * if file list send, we expect remote file list to
							 * receive.
							 */
							receive = true;
							transmit = false;
						}
						if (transferData[i].type == FileTransferType.RESOURCE_TRANSFER) {
							logger.debug("send file : "
									+ transferData[i].file_project_path);
							sendFile(output, transferData[i]);

						}

					}
					transferData = new JingleFileTransferData[0];
					/* set monitor status complete :) */
					monitor.setComplete(true);

					// Thread.sleep(2000);

				}
				if (receive) {
					/* get number of file to be transfer. */
					// InputStream input = socket.getInputStream();
					int fileNumber = input.read();

					System.out.println("incomming file numbers: " + fileNumber);

					for (int i = 0; i < fileNumber; i++) {
						/* receive file meta data */
						receiveMetaData(input);

						if (receiveTransferData.type == FileTransferType.FILELIST_TRANSFER) {
							receiveFileListData(input);
						}
						// if (receiveTransferData.type ==
						// FileTransferType.RESOURCE_TRANSFER) {
						// /* receive file. */
						// receiveFile(socket);
						// }

					}

					receive = false;
				}
			}
			output.close();
			input.close();
			socket.close();

		} catch (Exception e1) {
			e1.printStackTrace();
			if (listener != null) {
				listener.exceptionOccured(new JingleSessionException(e1
						.getMessage()));
			}
			return;
		}
	}

	private void sendFileListData(OutputStream output, String file_list_content)
			throws IOException {
		ObjectOutputStream oo = new ObjectOutputStream(output);

		oo.writeObject(file_list_content);
		oo.flush();
	}

	private void sendMetaData(OutputStream output, JingleFileTransferData data)
			throws IOException {
		ObjectOutputStream oo = new ObjectOutputStream(output);
		// ObjectInputStream ii = new ObjectInputStream(socket
		// .getInputStream());

		oo.writeObject(data);
		oo.flush();
	}

	private void receiveFileListData(InputStream input) throws IOException,
			ClassNotFoundException {
		logger.debug("receive file List");
		ObjectInputStream ii = new ObjectInputStream(input);

		String fileListData = (String) ii.readObject();

		/* inform listener. */
		listener.incommingFileList(fileListData, receiveTransferData.sender);

		// System.out.println("File List Data : " + fileListData.toString());
	}

	private void receiveMetaData(InputStream input) throws IOException,
			ClassNotFoundException {
		// ObjectOutputStream oo = new ObjectOutputStream(
		// socket.getOutputStream());
		ObjectInputStream ii = new ObjectInputStream(input);

		JingleFileTransferData meta = (JingleFileTransferData) ii.readObject();
		this.receiveTransferData = meta;

		// ii.close();

	}

	private void readByteArray(InputStream input, OutputStream output)
			throws IOException {
		ObjectOutputStream oos = new ObjectOutputStream(output);
		ObjectInputStream iis = new ObjectInputStream(input);

		/* Einlesen der gepufferten Daten. */

	}

	private void sendFile(OutputStream output, File file) throws IOException {

		// OutputStream output = socket.getOutputStream();
		// File file = new File(fileName);
		// int length = (int) file.length();

		FileInputStream fis = new FileInputStream(file);
		BufferedInputStream bis = new BufferedInputStream(fis);

		int readSize = 1024;
		byte[] buffer = new byte[readSize];
		int length = 0;
		long filesize = file.length();
		long currentSize = 0;

		/* zuvor informationen schicken, wie groß die Datei ist. */
		while (currentSize < filesize) {

			/* check end of file */
			if ((currentSize + readSize) >= filesize) {
				readSize = (int) (filesize - currentSize);
			}

			if ((length = bis.read(buffer, 0, readSize)) != 0) {
				output.write(buffer, 0, readSize);
				currentSize += readSize;
			}
		}

	}

	@Deprecated
	private void sendFileOld(OutputStream output, String fileName)
			throws IOException {

		// OutputStream output = socket.getOutputStream();
		File file = new File(fileName);
		int length = (int) file.length();

		System.out.println("File length: " + length);
		FileInputStream fis = new FileInputStream(file);
		BufferedInputStream bis = new BufferedInputStream(fis);
		byte[] buffer = new byte[1024];
		int bytesRead;
		while ((bytesRead = bis.read(buffer, 0, 1024)) != -1) {
			output.write(buffer, 0, 1024);
			output.flush();
		}
		System.out.println("File has send");

		fis.close();
		// output.close();
	}

	private void sendFile(OutputStream output, JingleFileTransferData fileData) {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();

		IProject project = root.getProject(fileData.project_name);
		// System.out.println("send single file");
		// logger.info("send file: "+fileData.file_project_path);
		if (project.exists()) {
			IFile file = project.getFile(new Path(fileData.file_project_path));
			try {
				sendFile(output, file.getLocation().toFile());
			} catch (IOException e) {
				logger.error("Error during file transfer: ", e);
				// e.printStackTrace();
			}
		} else {
			// TODO: exception weiterreichen.
			logger.error("Project not found.");
		}
	}

	// private void sendFile(OutputStream output, File file) throws IOException
	// {
	// // OutputStream output = socket.getOutputStream();
	// // int length = (int) file.length();
	// // System.out.println("File length: " + length);
	//	
	// FileInputStream fis = new FileInputStream(file.getAbsolutePath());
	// BufferedInputStream bis = new BufferedInputStream(fis);
	// byte[] buffer = new byte[1024];
	// int bytesRead;
	// while ((bytesRead = bis.read(buffer, 0, 1024)) != -1) {
	// output.write(buffer, 0, 1024);
	// output.flush();
	// }
	//
	// fis.close();
	// // output.close();
	// }

	@Deprecated
	private void sendString(Socket socket) throws IOException,
			ClassNotFoundException {
		ObjectOutputStream oo = new ObjectOutputStream(socket.getOutputStream());
		ObjectInputStream ii = new ObjectInputStream(socket.getInputStream());

		oo.writeObject("Test-String");
		oo.flush();
		System.out.println((String) ii.readObject());

		// socket.close();
		// ii.close();
		// oo.close();
	}

	@Deprecated
	private void startByteFileTransfer() {

		// System.out.println("Angemeldet: ");
		//		
		// // input = (ByteArrayInputStream) socket.getInputStream();
		// output = (ByteArrayOutputStream)socket.getOutputStream();
		//		
		// File file = new File("/home/troll/test.txt");
		// FileInputStream fileInputStream = new FileInputStream(file);
		// byte[] buffer = new byte[256];
		// for (int len = fileInputStream.read(buffer); len > 0; len =
		// fileInputStream
		// .read(buffer)) {
		// output.write(buffer, 0, len);
		// }
		// fileInputStream.close();

	}

	@Deprecated
	private void testNumberTransfer() {
		Socket socket = null;
		// ByteArrayInputStream input = null;
		// ByteArrayOutputStream output = null;
		while (on) {
			if (transmit) {
				try {
					/* Übertragung zwischen zwei Partnern. */
					socket = new Socket(remoteHost, remotePort);

					InputStream input = socket.getInputStream();
					OutputStream output = socket.getOutputStream();

					output.write(5);
					output.write(23);
					output.flush();

					System.out.println("Server antwort: " + input.read());
					socket.close();
					input.close();
					output.close();

					// Thread.sleep(2000);
					transmit = false;
					// socket.close();
					on = false;
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		}
	}

	// private static void method0(File file) throws Exception, IOException,
	// UnsupportedEncodingException {
	//
	// FileInputStream fileInputStream = new FileInputStream(file);
	// byte[] data = new byte[(int) file.length()];
	// fileInputStream.read(data);
	// fileInputStream.close();
	// System.out.println(new String(data, "UTF-8"));
	// }

	/**
	 * Return given file as byte array representation.
	 */
	private static byte[] readFile(File file) throws Exception {

		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		FileInputStream fileInputStream = new FileInputStream(file);
		byte[] buffer = new byte[256];
		for (int len = fileInputStream.read(buffer); len > 0; len = fileInputStream
				.read(buffer)) {
			byteArrayOutputStream.write(buffer, 0, len);
		}
		fileInputStream.close();
		// System.out.println(new String(byteArrayOutputStream.toByteArray(),
		// "UTF-8"));
		return byteArrayOutputStream.toByteArray();
	}

	/**
	 * Set Transmit Enabled/Disabled
	 * 
	 * @param transmit
	 *            boolean Enabled/Disabled
	 */
	public void setTransmit(boolean transmit) {
		this.transmit = transmit;
	}

	/**
	 * Stops Transmitter
	 */
	public void stop() {
		this.transmit = false;
		this.on = false;
	}

	public void sendFileData(JingleFileTransferData[] transferData) {

		this.transferData = transferData;
		transmit = true;
	}

	@Override
	public void addJingleFileTransferListener(
			IJingleFileTransferListener listener) {
		this.listener = listener;
	}

	@Override
	public void removeJingleFileTransferListener(
			IJingleFileTransferListener listener) {
		this.listener = null;

	}
}

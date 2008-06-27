/*
 * DPP - Serious Distributed Pair Programming
 * (c) Freie Universitaet Berlin - Fachbereich Mathematik und Informatik - 2006
 * (c) Riad Djemili - 2006
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 1, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package de.fu_berlin.inf.dpp.net.internal;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.log4j.Logger;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.preference.IPreferenceStore;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.DefaultPacketExtension;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smackx.filetransfer.FileTransferListener;
import org.jivesoftware.smackx.filetransfer.FileTransferManager;
import org.jivesoftware.smackx.filetransfer.FileTransferNegotiator;
import org.jivesoftware.smackx.filetransfer.FileTransferRequest;
import org.jivesoftware.smackx.filetransfer.IBBTransferNegotiator;
import org.jivesoftware.smackx.filetransfer.IncomingFileTransfer;
import org.jivesoftware.smackx.filetransfer.OutgoingFileTransfer;
import org.jivesoftware.smackx.filetransfer.Socks5TransferNegotiator;
import org.jivesoftware.smackx.filetransfer.Socks5TransferNegotiatorManager;
import org.xmlpull.v1.XmlPullParserException;

import de.fu_berlin.inf.dpp.FileList;
import de.fu_berlin.inf.dpp.PreferenceConstants;
import de.fu_berlin.inf.dpp.Saros;
import de.fu_berlin.inf.dpp.User;
import de.fu_berlin.inf.dpp.activities.FileActivity;
import de.fu_berlin.inf.dpp.activities.IActivity;
import de.fu_berlin.inf.dpp.activities.TextEditActivity;
import de.fu_berlin.inf.dpp.activities.TextSelectionActivity;
import de.fu_berlin.inf.dpp.concurrent.jupiter.Request;
import de.fu_berlin.inf.dpp.invitation.IInvitationProcess;
import de.fu_berlin.inf.dpp.invitation.IInvitationProcess.TransferMode;
import de.fu_berlin.inf.dpp.net.IFileTransferCallback;
import de.fu_berlin.inf.dpp.net.ITransmitter;
import de.fu_berlin.inf.dpp.net.JID;
import de.fu_berlin.inf.dpp.net.TimedActivity;
import de.fu_berlin.inf.dpp.net.internal.JingleFileTransferData.FileTransferType;
import de.fu_berlin.inf.dpp.net.jingle.IJingleFileTransferListener;
import de.fu_berlin.inf.dpp.net.jingle.JingleFileTransferManager;
import de.fu_berlin.inf.dpp.net.jingle.JingleFileTransferProcessMonitor;
import de.fu_berlin.inf.dpp.net.jingle.JingleSessionException;
import de.fu_berlin.inf.dpp.net.jingle.JingleFileTransferManager.JingleConnectionState;
import de.fu_berlin.inf.dpp.project.ISessionManager;
import de.fu_berlin.inf.dpp.project.ISharedProject;
import de.fu_berlin.inf.dpp.ui.ErrorMessageDialog;

/**
 * An ITransmitter implementation which uses Smack Chat objects.
 * 
 * @author rdjemili
 */
public class XMPPChatTransmitter implements ITransmitter,
		de.fu_berlin.inf.dpp.net.IReceiver, MessageListener,
		FileTransferListener, IJingleFileTransferListener {
	private static Logger log = Logger.getLogger(XMPPChatTransmitter.class
			.getName());

	private static final int MAX_PARALLEL_SENDS = 10;
	private static final int MAX_TRANSFER_RETRIES = 5;
	private static final int FORCEDPART_OFFLINEUSER_AFTERSECS = 60;

	private static boolean jingle = true;
	private boolean JingleError = false;
	private JingleFileTransferManager jingleManager;
	private JingleFileTransferProcessMonitor monitor;

	/*
	 * the following string descriptions are used to differentiate between
	 * transfers that are for invitations and transfers that are an activity for
	 * the current project.
	 */
	private static final String RESOURCE_TRANSFER_DESCRIPTION = "resourceAddActivity";

	private static final String FILELIST_TRANSFER_DESCRIPTION = "filelist";
	
	private static final String PROJECT_ARCHIVE_DESCRIPTION = "projectArchiveFile";

	private XMPPConnection connection;

	/*
	 * old version of chatmanager. TODO: exchange this with private manager.
	 */
	private ChatManager chatmanager;

	private MultiUserChatManager mucmanager;

	private PrivateChatManager privatechatmanager;

	private Map<JID, Chat> chats = new HashMap<JID, Chat>();

	private FileTransferManager fileTransferManager;

	// TODO use ListenerList instead
	private List<IInvitationProcess> processes = new CopyOnWriteArrayList<IInvitationProcess>();

	private List<FileTransferData> fileTransferQueue = new LinkedList<FileTransferData>();
	private List<MessageTransfer> messageTransferQueue = new LinkedList<MessageTransfer>();
	private Map<String, IncomingFile> incomingFiles = new HashMap<String, IncomingFile>();
	private int runningFileTransfers = 0;

	private boolean m_bFileTransferByChat = false; // to switch to

	// chat-filetransfer as
	// fallback

	/**
	 * A simple struct that is used to queue file transfers.
	 */
	public class FileTransferData {

		public FileTransferType type;
		public JID recipient;
		public IPath path;
		public int timestamp;
		public IFileTransferCallback callback;
		public int retries = 0;
		public byte[] content;
		public long filesize;
		public IProject project;

		// /* for testing only */
		// public File file;
		// public String filePath;
	}

	/**
	 * A simple struct that is used to manage incoming chunked files via
	 * chat-file transfer
	 */
	private class IncomingFile {
		String name;
		int receivedChunks;
		int chunkCount;
		List<String> messageBuffer;

		IncomingFile() {
			messageBuffer = new LinkedList<String>();
		}

		boolean isComplete() {
			return (receivedChunks == chunkCount);
		}
	}

	/**
	 * A simple struct that is used to queue message transfers.
	 */
	private class MessageTransfer {
		public JID receipient;
		public PacketExtension packetextension;
	}

	public XMPPChatTransmitter(XMPPConnection connection) {
		setXMPPConnection(connection);
	}

	public void setXMPPConnection(XMPPConnection connection) {
		this.connection = connection;
		this.chatmanager = connection.getChatManager();
		fileTransferManager = new FileTransferManager(connection);
		// TODO: aktuell noch nicht angesprochen
		fileTransferManager.addFileTransferListener(this);

		chats.clear();

		setProxyPort(connection);

		// TODO always preserve threads
		// this.connection.addPacketListener(this, new
		// MessageTypeFilter(Message.Type.chat)); // HACK
		// this.connection.addPacketListener(this, new
		// MessageTypeFilter(Message.Type.groupchat));

		/*
		 * an dieser Stelle wird der MUC Transmitter initialisiert, um die
		 * Kapselung des Systems mit dem Fassadenmuster nicht zu verletzten.
		 * TODO: Später überlegen, wie wir es trennen. Alle Methoden rufen
		 * momentan ITransmitter Methoden des MUC Transmitter auf, soweit diese
		 * implementiert sind.
		 */
		this.mucmanager = new MultiUserChatManager();
		mucmanager.setConnection(connection, this);

		this.privatechatmanager = new PrivateChatManager();
		privatechatmanager.setConnection(connection, this);

		if (jingle) {
			/* try to connect with jingle */
			jingleManager = new JingleFileTransferManager(connection, this);
		}
	}

	public void addInvitationProcess(IInvitationProcess process) {
		processes.add(process);
	}

	public void removeInvitationProcess(IInvitationProcess process) {
		processes.remove(process);
		/* terminate jingle session for states: done, cancel and joined.*/
		jingleManager.terminateJingleSession(process.getPeer());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.fu_berlin.inf.dpp.net.ITransmitter
	 */
	public void sendCancelInvitationMessage(JID user, String errorMsg) {
		sendMessage(user, PacketExtensions
				.createCancelInviteExtension(errorMsg));
		/* terminate jingle session. */
		jingleManager.terminateJingleSession(user);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.fu_berlin.inf.dpp.ITransmitter
	 */
	public void sendRequestForFileListMessage(JID user) {

		sendMessage(user, PacketExtensions.createRequestForFileListExtension());

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.fu_berlin.inf.dpp.ITransmitter
	 */
	public void sendRequestForActivity(ISharedProject sharedProject,
			int timestamp, boolean andup) {

		log.info("Requesting old activity (timestamp=" + timestamp + ", "
				+ andup + ") from all...");

		sendMessageToAll(sharedProject, PacketExtensions
				.createRequestForActivityExtension(timestamp, andup));

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.fu_berlin.inf.dpp.ITransmitter
	 */
	public void sendInviteMessage(ISharedProject sharedProject, JID guest,
			String description) {
		sendMessage(guest, PacketExtensions.createInviteExtension(sharedProject
				.getProject().getName(), description));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.fu_berlin.inf.dpp.ITransmitter
	 */
	public void sendJoinMessage(ISharedProject sharedProject) {
		try {
			/* sleep process for 500 millis to ensure invitation state process.*/
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			log.error(e);
		}
		sendMessageToAll(sharedProject, PacketExtensions.createJoinExtension());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.fu_berlin.inf.dpp.ITransmitter
	 */
	public void sendLeaveMessage(ISharedProject sharedProject) {
		sendMessageToAll(sharedProject, PacketExtensions.createLeaveExtension());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.fu_berlin.inf.dpp.ITransmitter
	 */
	public void sendActivities(ISharedProject sharedProject,
			List<TimedActivity> timedActivities) {

		// timer that calls sendActivities is called before setting chat

		for (TimedActivity timedActivity : timedActivities) {
			IActivity activity = timedActivity.getActivity();

			if (activity instanceof FileActivity) {
				FileActivity fileAdd = (FileActivity) activity;

				/* send file checksum error message to exclusive recipient*/
				if(fileAdd.getType().equals(FileActivity.Type.Error)){
					sendFileChecksumError(fileAdd.getRecipient(), fileAdd.getPath());
				}
				/* send file to solve checksum error to single recipient */
				if (fileAdd.getType().equals(FileActivity.Type.Created) || fileAdd.getRecipient() != null){
					int time = timedActivity.getTimestamp();
					sendFile(fileAdd.getRecipient(), sharedProject.getProject(), fileAdd
							.getPath(), time, null);
					return;
				}
				
				if (fileAdd.getType().equals(FileActivity.Type.Created)) {
					JID myJID = Saros.getDefault().getMyJID();

					for (User participant : sharedProject.getParticipants()) {
						JID jid = participant.getJid();
						if (jid.equals(myJID))
							continue;

						// TODO use callback
						int time = timedActivity.getTimestamp();
						/* send file with other send method. */
						sendFile(jid, sharedProject.getProject(), fileAdd
								.getPath(), time, null);
					}

					// TODO remove activity and let this be handled by
					// ActivitiesProvider instead

					// don't remove file activity so that it still bumps the
					// time stamp when being received
				}
			} else {
				sharedProject.getSequencer().getActivityHistory().add(
						timedActivity);

				// TODO: removed very old entries
			}
		}

		log.info("Sent activities: " + timedActivities);

		/* send activities with muc chat messages. */
		if (mucmanager != null && mucmanager.isConnected()) {
			mucmanager.sendActivities(sharedProject, timedActivities);
		}
		else{
			/* if no connection with muc room, send activities with 
			 * private message */
			 if (timedActivities != null ) {
			 sendMessageToAll(sharedProject, new
			 ActivitiesPacketExtension(timedActivities));
			 }	
		}

		

	}


	
	private void sendFileListWithJingle(JID recipient, String fileListContent) {
		JingleFileTransferProcessMonitor monitor = new JingleFileTransferProcessMonitor();
		/* create file transfer. */
		JingleFileTransferData data = new JingleFileTransferData();

		/* only for testing. */
		data.file_list_content = fileListContent;
		data.type = FileTransferType.FILELIST_TRANSFER;
		data.recipient = recipient;
		data.sender = new JID(connection.getUser());
		data.file_project_path = FileTransferType.FILELIST_TRANSFER.toString();

		jingleManager.createOutgoingJingleFileTransfer(recipient,
				new JingleFileTransferData[] { data }, monitor);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.fu_berlin.inf.dpp.ITransmitter
	 */
	public void sendFileList(JID recipient, FileList fileList)
			throws XMPPException {

		String xml = fileList.toXML();
		String to = recipient.toString();

		if (getFileTransferModeViaChat()) {

			if (sendChatTransfer(FILELIST_TRANSFER_DESCRIPTION, "", xml
					.getBytes(), recipient))
				log.debug("Sent file list via ChatTransfer");
			else
				log.warn("Error sending file list via ChatTransfer");
		}

		/* send with jingle file transfer */
		if (jingle
				&& (jingleManager.getState(recipient) != JingleConnectionState.ERROR) && !JingleError) {

			sendFileListWithJingle(recipient, xml);

		} else {			
			/* send file with IBB File Transfer */

			log.debug("Establishing file list transfer");

			int attempts = MAX_TRANSFER_RETRIES;

			/* Write xml datas to temp file for transfering. */
			try {
				File newfile = new File(FILELIST_TRANSFER_DESCRIPTION + "."
						+ new JID(connection.getUser()).getName());
				if (newfile.exists()) {
					newfile.delete();
				}
				log.debug("file : " + newfile.getAbsolutePath());

				FileWriter writer = new FileWriter(
						FILELIST_TRANSFER_DESCRIPTION + "."
								+ new JID(connection.getUser()).getName());
				writer.append(xml);
				writer.close();

				// } catch (IOException e1) {
				// // TODO Auto-generated catch block
				// e1.printStackTrace();
				// }

				// while (true) {

				// try {
				OutgoingFileTransfer
						.setResponseTimeout(MAX_TRANSFER_RETRIES * 1000);
				OutgoingFileTransfer transfer = fileTransferManager
						.createOutgoingFileTransfer(to);

				// OutputStream out = transfer.sendFile(
				// FILELIST_TRANSFER_DESCRIPTION, xml.getBytes().length,
				// FILELIST_TRANSFER_DESCRIPTION);
				//
				// if (out != null) {
				// if (attempts-- > 0)
				// continue;
				// throw new XMPPException(transfer.getException());
				// }
				//
				// BufferedWriter writer = new BufferedWriter(new
				// PrintWriter(
				// out));
				// writer.write(xml);
				// writer.flush();
				// writer.close();
				// }

				log.info("Sending file list");
				FileTransferProcessMonitor monitor = new FileTransferProcessMonitor(
						transfer);
				transfer.sendFile(newfile, FILELIST_TRANSFER_DESCRIPTION);

				/* wait for complete transfer. */
				while (monitor.isAlive() && monitor.isRunning()) {
					Thread.sleep(500);
				}
				monitor.closeMonitor(true);

				// int time = 0;
				// while (!transfer.isDone()) {
				//
				// if (transfer
				// .getStatus()
				// .equals(
				// org.jivesoftware.smackx.filetransfer.FileTransfer.Status.error))
				// {
				// log.error("ERROR!!! " + transfer.getError());
				// } else {
				// log.debug("Status : " + transfer.getStatus()+" Progress :
				// " +
				// transfer.getProgress());
				// }
				// try {
				// /* check response time out. */
				// if (time < OutgoingFileTransfer.getResponseTimeout()) {
				// Thread.sleep(100);
				// time += 100;
				// }
				// else{
				// log.error("File transfer response error.");
				// throw new XMPPException("File transfer response error.");
				// }
				// } catch (InterruptedException e) {
				// // TODO Auto-generated catch block
				// e.printStackTrace();
				// }
				// }

				/*
				 * TODO: es kommt momentan zu einer file not found exception,
				 * obwohl die Datei übertragen wurde.
				 */
				// if (!transfer
				// .getStatus()
				// .equals(
				// org.jivesoftware.smackx.filetransfer.FileTransfer.Status.complete))
				// {
				// log.warn("file list transfer incomplete!");
				// throw new XMPPException("file list transfer incomplete");
				// }
				/* delete temp file. */
				// File list = new File(FILELIST_TRANSFER_DESCRIPTION);
				if (newfile.exists()) {
					newfile.delete();
				}
				log.info("File list sent");

				// break;

			} catch (IOException e) {
				// if (attempts-- > 0)
				// continue;

				m_bFileTransferByChat = true;
				sendChatTransfer(FILELIST_TRANSFER_DESCRIPTION, "", xml
						.getBytes(), recipient);
				// TODO errorhandling

				log
						.info("File list sent via ChatTransfer. File transfer mode is set to ChatTransfer.");
			}
			// }
			catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// end of jingle transfer
		}

	}

	/**
	 * Sends a data buffer to a recipient using chat messages. The buffer is
	 * transmitted Base64 encoded and split into blocks of size MAX_MSG_LENGTH.
	 * 
	 * @param name
	 *            name of the data buffer. e.g. the filename of the transmitted
	 *            file
	 * @param desc
	 *            description String of the transfer. e.g. encoded timestamp for
	 *            file activity
	 * @param data
	 *            a byte buffer to be transmitted. it will be base64 encoded
	 * @param recipient
	 *            JID of the user to send this data to
	 * 
	 * @return <code>true</code> if the message was send successfully
	 */
	boolean sendChatTransfer(String name, String desc, byte[] data,
			JID recipient) {

		final int maxMsgLen = Saros.getDefault().getPreferenceStore().getInt(
				PreferenceConstants.CHATFILETRANSFER_CHUNKSIZE);

		// Convert byte array to base64 string
		String data64 = new sun.misc.BASE64Encoder().encode(data);

		// send large data sets in several messages
		int tosend = data64.length();
		int pcount = (tosend / maxMsgLen) + ((tosend % maxMsgLen == 0) ? 0 : 1);
		int start = 0;
		try {
			for (int i = 1; i <= pcount; i++) {
				int psize = Math.min(tosend, maxMsgLen);
				int end = start + psize;

				PacketExtension extension = PacketExtensions
						.createDataTransferExtension(name, desc, i, pcount,
								data64.substring(start, end));

				sendMessage(recipient, extension);

				start = end;
				tosend -= psize;

			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	/**
	 * Receives a data buffer sent by a chat message. The data will be decoded
	 * from base64 encoding. Splitted transfer will be buffered until all chunks
	 * are received. Then the file will be reconstructed and processed as a
	 * whole.
	 * 
	 * @param message
	 *            Message containing the data as extension.
	 * 
	 * @return <code>true</code> if the message was handled successfully.
	 */
	boolean receiveChatTransfer(Message message) {
		DefaultPacketExtension dt = PacketExtensions
				.getDataTransferExtension(message);
		String sName = dt.getValue(PacketExtensions.DT_NAME);
		String sData = dt.getValue(PacketExtensions.DT_DATA);

		String sSplit = dt.getValue(PacketExtensions.DT_SPLIT);
		try {
			// is this a multipart transfer?
			if (sSplit != null && sSplit.equals("1/1") == false) {
				// parse split information (index and chunk count)
				int i = sSplit.indexOf('/');
				int cur = Integer.parseInt(sSplit.substring(0, i));
				int max = Integer.parseInt(sSplit.substring(i + 1));

				log.debug("Received chunk " + cur + " of " + max + " of file "
						+ sName);

				// check for previous chunks
				IncomingFile ifile = incomingFiles.get(sName);
				if (ifile == null) {
					// this is the first received chunk->create incoming file
					// object
					ifile = new IncomingFile();
					ifile.receivedChunks++;
					ifile.chunkCount = max;
					ifile.name = sName;
					for (i = 0; i < max; i++)
						ifile.messageBuffer.add(null);
					ifile.messageBuffer.set(cur - 1, sData);
					incomingFiles.put(sName, ifile);
					return true;
				} else {
					// this is a following chunk
					ifile.receivedChunks++;
					ifile.messageBuffer.set(cur - 1, sData);

					if (ifile.isComplete() == false)
						return true;

					// join the buffers to restore the file from chunks
					sData = "";
					for (i = 0; i < max; i++)
						sData += ifile.messageBuffer.get(i);
					incomingFiles.remove(ifile);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;

		}

		byte[] dataOrg = null;
		try {
			dataOrg = new sun.misc.BASE64Decoder().decodeBuffer(sData);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		// File list received
		if (sName.equals(FILELIST_TRANSFER_DESCRIPTION)) {
			FileList fileList = null;
			IInvitationProcess myProcess = null;
			try {
				JID fromJID = new JID(message.getFrom());
				for (IInvitationProcess process : processes) {
					if (process.getPeer().equals(fromJID)) {
						myProcess = process;
						fileList = new FileList(new String(dataOrg));
						process.fileListReceived(fromJID, fileList);
					}
				}
				log.info("Received file list via ChatTransfer");
			} catch (Exception e) {
				if (myProcess != null)
					myProcess.cancel("Error receiving file list", false);
			}

		} else {
			// receiving file (resource)

			try {

				JID from = new JID(message.getFrom());
				Path path = new Path(sName);

				ByteArrayInputStream in = new ByteArrayInputStream(dataOrg);

				log.debug("Receiving resource from " + from.toString() + ": "
						+ sName + " (ChatTransfer)");

				boolean handledByInvitation = false;
				for (IInvitationProcess process : processes) {
					if (process.getPeer().equals(from)) {
						process.resourceReceived(from, path, in);
						handledByInvitation = true;
					}
				}

				if (!handledByInvitation) {

					if (Saros.getDefault().getSessionManager()
							.getSharedProject() == null) {
						// receiving resource without a running session? not
						// accepted
						return false;
					}

					FileActivity activity = new FileActivity(
							FileActivity.Type.Created, path, in);

					int time;
					String description = dt.getValue(PacketExtensions.DT_DESC);
					try {
						time = Integer.parseInt(description
								.substring(RESOURCE_TRANSFER_DESCRIPTION
										.length() + 1));
					} catch (Exception e) {
						Saros.log("Could not parse time from description: "
								+ description, e);
						time = 0; // HACK
					}

					TimedActivity timedActivity = new TimedActivity(activity,
							time);

					ISessionManager sm = Saros.getDefault().getSessionManager();
					sm.getSharedProject().getSequencer().exec(timedActivity);
				}

				log.info("Received resource " + sName);

			} catch (Exception e) {
				log.warn("Failed to receive " + sName, e);
			}
		}

		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.fu_berlin.inf.dpp.net.ITransmitter
	 */
	public void sendFile(JID to, IProject project, IPath path,
			IFileTransferCallback callback) {
		sendFile(to, project, path, -1, callback);
	}

	/**
	 * Reads a files content into a buffer.
	 * 
	 * @param transfer
	 *            Object containing file path and a buffer (among other) to read
	 *            from and to.
	 * 
	 * @return <code>true</code> if the file was read successfully
	 */
	boolean readFile(FileTransferData transfer) {
		// SessionManager sm = Saros.getDefault().getSessionManager();
		// IProject project = sm.getSharedProject().getProject();

		File f = new File(transfer.project.getFile(transfer.path).getLocation()
				.toOSString());
		transfer.filesize = f.length();
		transfer.content = new byte[(int) transfer.filesize];

		try {
			InputStream in = transfer.project.getFile(transfer.path)
					.getContents();
			in.read(transfer.content, 0, (int) transfer.filesize);
		} catch (Exception e) {
			e.printStackTrace();
			transfer.content = null;
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.fu_berlin.inf.dpp.net.ITransmitter
	 */
	public void sendFile(JID to, IProject project, IPath path, int timestamp,
			IFileTransferCallback callback) {

		FileTransferData transfer = new FileTransferData();
		transfer.recipient = to;
		transfer.path = path;
		transfer.timestamp = timestamp;
		transfer.callback = callback;
		transfer.project = project;
		transfer.filesize = project.getFile(path).getLocation().toFile()
				.length();

		// if transfer will be delayed, we need to buffer the file
		// to not send modified versions later
		if (!connection.isConnected())
			readFile(transfer);
		else
			transfer.content = null;

		fileTransferQueue.add(transfer);
		sendNextFile();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.fu_berlin.inf.dpp.net.ITransmitter#sendProjectArchive(de.fu_berlin.inf.dpp.net.JID,
	 *      org.eclipse.core.resources.IProject, java.io.File,
	 *      de.fu_berlin.inf.dpp.net.IFileTransferCallback)
	 */
	public void sendProjectArchive(JID recipient, IProject project,
			File archive, IFileTransferCallback callback) {
		OutgoingFileTransfer.setResponseTimeout(MAX_TRANSFER_RETRIES * 1000);
		OutgoingFileTransfer transfer = fileTransferManager
				.createOutgoingFileTransfer(recipient.toString());
		
		
		try {
			transfer.sendFile(archive, PROJECT_ARCHIVE_DESCRIPTION);
			
			FileTransferProcessMonitor monitor = new FileTransferProcessMonitor(
					transfer,callback);
			/* wait for complete transfer. */
			while (monitor.isAlive() && monitor.isRunning()) {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			monitor.closeMonitor(true);

			if (transfer
					.getStatus()
					.equals(
							org.jivesoftware.smackx.filetransfer.FileTransfer.Status.complete)) {
				log.debug("transfer complete");
				callback.fileSent(new Path(archive.getName()));
			}
			
			/* delete temp archive file. */
			archive.delete();

		} catch (Exception e) {
			
				log.warn("Failed to send archive file", e);
				if (callback != null)
					callback.fileTransferFailed(null,e);
		}
	}

	private void sendNextFile() {
		if (fileTransferQueue.size() == 0
				|| runningFileTransfers > MAX_PARALLEL_SENDS
		// || Saros.getDefault().getConnectionState() !=
		// Saros.ConnectionState.CONNECTED
		) {
			log.debug("non file to send in queue.");
			return;
		}

		final FileTransferData transfer = fileTransferQueue.remove(0);

		new Thread(new Runnable() {

			/*
			 * (non-Javadoc)
			 * 
			 * @see java.lang.Runnable#run()
			 */
			public void run() {
				try {
					runningFileTransfers++;
					log.debug("try to send file " + transfer.path);
					transferFile(transfer);

				} catch (Exception e) {
					if (transfer.retries >= MAX_TRANSFER_RETRIES) {
						log.warn("Failed to send " + transfer.path, e);
						if (transfer.callback != null)
							transfer.callback.fileTransferFailed(transfer.path,
									e);

					} else {
						transfer.retries++;
						fileTransferQueue.add(transfer);
					}

				} finally {
					runningFileTransfers--;
					sendNextFile();
				}
			}

		}).start();
	}

	public void sendUserListTo(JID to, List<User> participants) {
		log.debug("Sending user list to " + to.toString());

		sendMessage(to, PacketExtensions.createUserListExtension(participants));
	}

	/*
	 * (non-Javadoc)
	 * @see de.fu_berlin.inf.dpp.net.ITransmitter#sendFileChecksumError(de.fu_berlin.inf.dpp.net.JID, org.eclipse.core.runtime.IPath)
	 */
	public void sendFileChecksumError(JID to, IPath path){
		log.debug("Sending checksum error message to "+to+" of file "+path.lastSegment());
		sendMessage(to,PacketExtensions.createChecksumErrorExtension(path));
	}
	
	/*
	 * (non-Javadoc)
	 * @see de.fu_berlin.inf.dpp.net.ITransmitter#sendJupiterTransformationError(de.fu_berlin.inf.dpp.net.JID, org.eclipse.core.runtime.IPath)
	 */
	public void sendJupiterTransformationError(JID to, IPath path){
		log.debug("Sending jupiter transformation error message to "+to+" of file "+path.lastSegment());
		sendMessage(to,PacketExtensions.createJupiterErrorExtension(path));
	}
	
	public void sendRemainingFiles() {

		if (fileTransferQueue.size() > 0)
			sendNextFile();
	}

	public void sendRemainingMessages() {

		try {
			while (messageTransferQueue.size() > 0) {
				final MessageTransfer pex = messageTransferQueue.remove(0);

				Chat chat = getChat(pex.receipient);
				Message message;
				// TODO: Änderung für Smack 3
				message = new Message();
				// message = chat.createMessage();
				message.addExtension(pex.packetextension);
				chat.sendMessage(message);
				log.info("Resending message");
			}
		} catch (Exception e) {
			Saros.getDefault().getLog().log(
					new Status(IStatus.ERROR, Saros.SAROS, IStatus.ERROR,
							"Could not send message", e));
		}
	}

	public boolean resendActivity(JID jid, int timestamp, boolean andup) {

		boolean sent = false;

		ISharedProject project = Saros.getDefault().getSessionManager()
				.getSharedProject();

		try {
			List<TimedActivity> tempActivities = new LinkedList<TimedActivity>();
			for (TimedActivity tact : project.getSequencer()
					.getActivityHistory()) {

				if ((andup == false && tact.getTimestamp() != timestamp)
						|| (andup == true && tact.getTimestamp() < timestamp))
					continue;

				tempActivities.add(tact);
				sent = true;

				if (andup == false)
					break;
			}

			if (sent) {
				PacketExtension extension = new ActivitiesPacketExtension(
						tempActivities);
				sendMessage(jid, extension);
			}

		} catch (Exception e) {
			Saros.getDefault().getLog().log(
					new Status(IStatus.ERROR, Saros.SAROS, IStatus.ERROR,
							"Could not resend message", e));
		}

		return sent;
	}

	public void processMessage(Chat chat, Message message) {
		// TODO: new method für smack 3
//		log.debug("incomming message : " + message.getBody());
		// processPacket(message);

	}

	public void processPacket(Chat chat, Packet packet) {

	}

	/*
	 * (non-Javadoc)
	 * @see de.fu_berlin.inf.dpp.net.ITransmitter#sendActivitiyTo(de.fu_berlin.inf.dpp.project.ISharedProject, java.util.List, de.fu_berlin.inf.dpp.net.JID)
	 */
	public void sendJupiterRequest(ISharedProject sharedProject,
			Request request, JID jid) {
		log.info("send request to : "+jid+ " request: "+request);
		sendMessage(jid, new RequestPacketExtension(request));
	}
	
	public void processRequest(Packet packet) {
		Message message = (Message) packet;
		
		RequestPacketExtension packetExtension = PacketExtensions.getJupiterRequestExtension(message);
		
		if(packetExtension != null){
			ISharedProject project = Saros.getDefault().getSessionManager()
			.getSharedProject();
			log.info("Received request : "+packetExtension.getRequest().toString());
			project.getSequencer().receiveRequest(packetExtension.getRequest());
			
		}else{
			log.error("Failure in request packet extension.");
		}
		
	}
	
	// TODO replace dependencies by more generic listener interfaces
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.jivesoftware.smack.PacketListener
	 */
	public void processPacket(Packet packet) {
		Message message = (Message) packet;

		JID fromJID = new JID(message.getFrom());
		// Change the input method to get the right chats
		putIncomingChat(fromJID, message.getThread());
		ISharedProject project = Saros.getDefault().getSessionManager()
				.getSharedProject();

		ActivitiesPacketExtension activitiesPacket = PacketExtensions
				.getActvitiesExtension(message);

		boolean isProjectParticipant = false;
		if (project != null)
			isProjectParticipant = (project.getParticipant(fromJID) != null);

		if (activitiesPacket != null) {
			List<TimedActivity> timedActivities = activitiesPacket
					.getActivities();

			log.debug("Received activities from " + fromJID.toString() + ": "
					+ timedActivities);

			if (!isProjectParticipant) {
				log.info("user not member!");
				return;
			}

			for (TimedActivity timedActivity : timedActivities) {

				IActivity activity = timedActivity.getActivity();
				activity.setSource(fromJID.toString());
				
//				if (activity instanceof TextSelectionActivity) {
//					((TextSelectionActivity) activity).setSource(fromJID
//							.toString());
//				}
//				if (activity instanceof TextEditActivity) {
//					((TextEditActivity) activity).setSource(fromJID.toString());
//				}

				/*
				 * incoming fileActivities that add files are only used as
				 * placeholder to bump the timestamp. the real fileActivity will
				 * be processed by using a file transfer.
				 */
				if (!(activity instanceof FileActivity)
						|| !((FileActivity) activity).getType().equals(
								FileActivity.Type.Created)) {

					//TODO: Empfang der Aktivitäten und weiterleitung an den sequencer
					project.getSequencer().exec(timedActivity);

				}
			}
		}

		if (PacketExtensions.getJoinExtension(message) != null) {

			boolean iAmInviter = false;

			for (IInvitationProcess process : processes) {
				if (process.getPeer().equals(fromJID)) {
					process.joinReceived(fromJID);
					iAmInviter = true;
				}
			}
			if (!iAmInviter && project != null)
				project.addUser(new User(fromJID)); // a new user joined this
			// session

		}

		else if (PacketExtensions.getLeaveExtension(message) != null) {
			if (project != null)
				project.removeUser(new User(fromJID)); // HACK
		}

		else if (PacketExtensions.getRequestActivityExtension(message) != null
				&& isProjectParticipant) {
			DefaultPacketExtension rae = PacketExtensions
					.getRequestActivityExtension(message);
			String sID = rae.getValue("ID");
			String sIDandup = rae.getValue("ANDUP");

			int ts = -1;
			if (sID != null) {
				ts = (new Integer(sID)).intValue();
				// get that activity from history (if it was mine) and send it
				boolean sent = resendActivity(fromJID, ts, (sIDandup != null));

				String info = "Received Activity request for timestamp=" + ts
						+ ".";
				if (sIDandup != null)
					info += " (andup) ";
				if (sent)
					info += " I sent response.";
				else
					info += " (not for me)";

				log.info(info);
			}
		}

		else if (PacketExtensions.getDataTransferExtension(message) != null) {
			receiveChatTransfer(message);
		}

		/* invitee request for project file list (state.INVITATION_SEND */
		else if (PacketExtensions.getRequestExtension(message) != null) {
			for (IInvitationProcess process : processes) {
				if (process.getPeer().equals(fromJID))
					process.invitationAccepted(fromJID);
			}
		}

		else if (PacketExtensions.getUserlistExtension(message) != null) {
			DefaultPacketExtension userlistExtension = PacketExtensions
					.getUserlistExtension(message);

			// My inviter sent a list of all session participants
			// I need to adapt the order for later case of driver leaving the
			// session
			log.debug("Received user list from " + fromJID);

			int count = 0;
			while (true) {
				String jidS = userlistExtension.getValue("User"
						+ count);
				if (jidS == null)
					break;
				log.debug("   *:" + jidS);

				JID jid = new JID(jidS);
				User user = new User(jid);
				
				String userRole = userlistExtension.getValue("UserRole"+count);
				user.setUserRole(de.fu_berlin.inf.dpp.User.UserRole.valueOf(userRole));
				
				String color = userlistExtension.getValue("UserColor"+count);
				try{
					user.setColorID(Integer.parseInt(color));
				}catch(NumberFormatException nfe){
					log.warn("Exception during convert user color form userlist for user : "+user.getJid());
				}

				if (project.getParticipant(jid) == null)
					sendMessage(jid, PacketExtensions.createJoinExtension());

				project.addUser(user, count - 1); // add user to internal user
				// list, maintaining the
				// received order
				count++;
			}
		}

		else if (PacketExtensions.getInviteExtension(message) != null) {
			DefaultPacketExtension inviteExtension = PacketExtensions
					.getInviteExtension(message);
			String desc = inviteExtension
					.getValue(PacketExtensions.DESCRIPTION);
			String pName = inviteExtension
					.getValue(PacketExtensions.PROJECTNAME);

			ISessionManager sm = Saros.getDefault().getSessionManager();
			sm.invitationReceived(fromJID, pName, desc);
		}

		else if (PacketExtensions.getCancelInviteExtension(message) != null) {
			DefaultPacketExtension cancelInviteExtension = PacketExtensions
					.getCancelInviteExtension(message);

			String errorMsg = cancelInviteExtension
					.getValue(PacketExtensions.ERROR);

			for (IInvitationProcess process : processes) {
				if (process.getPeer().equals(fromJID))
					process.cancel(errorMsg, true);
			}
		}
		
		else if(PacketExtensions.getJingleErrorExtension(message) != null) {
			log.debug("Receive jingle error messages from "+fromJID);
			/* set error state for this peer in jingle connection states. */
			jingleManager.setJingleErrorState(fromJID);
			/*TODO: callback for other connections possible? */
		}
		else if(PacketExtensions.getJupiterErrorExtension(message) != null) {
			log.debug("Receive jingle error messages from "+fromJID);
			
		}
		else if(PacketExtensions.getChecksumErrorExtension(message) != null) {
//			log.debug("Receive jingle error messages from "+fromJID);
			DefaultPacketExtension checksumErrorExtension = PacketExtensions
			.getChecksumErrorExtension(message);
			String path = checksumErrorExtension.getValue(PacketExtensions.FILE_PATH);
			System.out.println("Checksum Error for "+path);
		}
		
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.jivesoftware.smackx.filetransfer.FileTransferListener
	 */
	public void fileTransferRequest(FileTransferRequest incommingRequest) {

		/* for testing file transfer. */

		// File newfile = null;
		// try {
		//
		// String fileDescription = request.getDescription();
		// log.debug("incomming file transfer " + request.getFileName());
		// if (fileDescription.equals(FILELIST_TRANSFER_DESCRIPTION)) {
		//
		// /*
		// * Create file list file
		// */
		// IncomingFileTransfer transfer = request.accept();
		//
		// log.debug(request.getFileName() + " with filepath "
		// + transfer.getFilePath());
		//
		// FileTransferProcessMonitor monitor = new FileTransferProcessMonitor(
		// transfer);
		// /* receive file. */
		// newfile = new File(request.getFileName());
		// transfer.recieveFile(newfile);
		//
		// /* wait for complete transfer. */
		// while (monitor.isAlive() && monitor.isRunning()) {
		// Thread.sleep(500);
		// }
		// monitor.closeMonitor(true);
		//
		// /* change file list receiver */
		// FileList fileList = receiveFileList(newfile);
		//
		// // FileList fileList = receiveFileList(request);
		//
		// JID fromJID = new JID(request.getRequestor());
		//
		// for (IInvitationProcess process : processes) {
		// if (process.getPeer().equals(fromJID))
		// process.fileListReceived(fromJID, fileList);
		// }
		// log.debug("received filelist with file transfer. ");
		// } else {
		// if (fileDescription
		// .startsWith(RESOURCE_TRANSFER_DESCRIPTION, 0)) {
		// // /* receive file. */
		// // newfile = new File(request.getFileName());
		// // transfer.recieveFile(newfile);
		//
		// receiveResource(request);
		// log.debug("receive ressource file transfer.");
		// }
		// }
		//
		// } catch (Exception e) {
		// log.error(e);
		// }
		/* end for testing file transfer. */

		final FileTransferRequest request = incommingRequest;

		new Thread(new Runnable() {

			public void run() {
				try {
					String fileDescription = request.getDescription();
					log.debug("1. incomming file transfer "
							+ request.getFileName());
					if(fileDescription.equals(PROJECT_ARCHIVE_DESCRIPTION)){
						log.debug(" incoming project archive file.");
						receiveArchiveFile(request);
					}
					if (fileDescription.equals(FILELIST_TRANSFER_DESCRIPTION)) {
						FileList fileList = receiveFileListBufferByteArray(request);
						JID fromJID = new JID(request.getRequestor());

						log.debug("2. inform invitation process...");
						for (IInvitationProcess process : processes) {
							if (process.getPeer().equals(fromJID)){
								process.fileListReceived(fromJID, fileList);
								/* incoming IBB transfer. cancel jingle transfer mode. */
								process.setTransferMode(TransferMode.IBB);
							}
						}

					} else if (fileDescription.startsWith(
							RESOURCE_TRANSFER_DESCRIPTION, 0)) {
						receiveResource(request);
					}
				} catch (Exception e) {
					//TODO Exception weiter reichen.
					log.error("Incomming File Transfer Thread: ", e);
					for (IInvitationProcess process : processes) {
						if (process.getPeer().equals(new JID(request.getRequestor())))
							process.cancel(e.getMessage(), false);
					}
				}

			}

		}).start();

	}

	/**
	 * read incoming file and open inputstream to IInvitationProcess.
	 * @param request
	 * @throws Exception
	 */
	private void receiveArchiveFile(FileTransferRequest request) throws Exception{
//		try{
		File archive = receiveFile(request);
		
		ZipFile zip = new ZipFile(archive);
		Enumeration<ZipEntry> entries = (Enumeration<ZipEntry>) zip.entries();
		while(entries.hasMoreElements()){
			ZipEntry entry = entries.nextElement();
			System.out.println(entry.getName());
			JID fromJID = new JID(request.getRequestor());

			log.debug("2. inform invitation process...");
			for (IInvitationProcess process : processes) {
				if (process.getPeer().equals(fromJID))
//					new Path(entry.getName()).removeLastSegments(0);
					process.resourceReceived(fromJID, new Path(entry.getName()), zip.getInputStream(entry));
			}
		}
//		} catch(Exception e){
//			e.printStackTrace();
//			System.out.println("Exception");
//		}
		archive.delete();
	}
	
	private void sendMessageToAll(ISharedProject sharedProject,
			PacketExtension extension) { // HACK

		JID myJID = Saros.getDefault().getMyJID();

		for (User participant : sharedProject.getParticipants()) {
			if (participant.getJid().equals(myJID))
				continue;

			// if user is known to be offline, dont send but queue
			if (sharedProject != null) {

				User user = sharedProject.getParticipant(participant.getJid());
				if (user != null
						&& user.getPresence() == User.UserConnectionState.OFFLINE) {

					// offline for too long
					if (user.getOfflineSecs() > FORCEDPART_OFFLINEUSER_AFTERSECS) {
						log.info("Removing offline user from session...");
						sharedProject.removeUser(user);
					} else {
						queueMessage(participant.getJid(), extension);
						log.info("User known as offline - Message queued!");
					}

					continue;
				}
			}

			sendMessage(participant.getJid(), extension);
		}
	}

	private void queueMessage(JID jid, PacketExtension extension) {
		MessageTransfer msg = new MessageTransfer();
		msg.receipient = jid;
		msg.packetextension = extension;
		messageTransferQueue.add(msg);
	}

	private void sendMessage(JID jid, PacketExtension extension) {

		if (!connection.isConnected()) {
			queueMessage(jid, extension);
			return;
		}

		try {

			Chat chat = getChat(jid);
			Message message;
			// Änderung für Smack 3
			message = new Message();
			// message = chat.createMessage();
			message.addExtension(extension);

			chat.sendMessage(message);

		} catch (Exception e) {
			queueMessage(jid, extension);

			Saros.getDefault().getLog().log(
					new Status(IStatus.ERROR, Saros.SAROS, IStatus.ERROR,
							"Could not send message, message queued", e));
		}
	}

	private void receiveResource(JingleFileTransferData data, InputStream input) {
		try {
			JID from = data.sender;
			Path path = new Path(data.file_project_path);

			for (IInvitationProcess process : processes) {
				if (process.getPeer().equals(from)) {
					process.resourceReceived(from, path, input);

				}
			}

		} catch (Exception e) {
			log.error("Error during receive file", e);
		}
	}

	/**
	 * receive resource with file transfer.
	 * 
	 * @param request
	 */
	private void receiveResource(FileTransferRequest request) {
		try {

			JID from = new JID(request.getRequestor());
			/* file path exists in description. */
			Path path = new Path(request.getDescription().substring(
					RESOURCE_TRANSFER_DESCRIPTION.length() + 1));

			log.debug("Receiving resource from" + from.toString() + ": "
					+ request.getFileName());

			// InputStream in = request.accept().recieveFile();

			IncomingFileTransfer transfer = request.accept();

			// FileTransferProcessMonitor monitor = new
			// FileTransferProcessMonitor(
			// transfer);

			InputStream in = transfer.recieveFile();
			/* 1. Wenn es innerhalb des Invitation processes stattfindet. */
			boolean handledByInvitation = false;
			for (IInvitationProcess process : processes) {
				if (process.getPeer().equals(from)) {
					process.resourceReceived(from, path, in);
					handledByInvitation = true;
				}
			}

			/*
			 * 2. wenn es nicht innerhalb des invitation process stattfindet,
			 * sondern innerhalb der session.
			 */
			if (!handledByInvitation) {
				FileActivity activity = new FileActivity(
						FileActivity.Type.Created, path, in);

				int time;
				String description = request.getDescription();
				try {
					time = Integer
							.parseInt(description
									.substring(RESOURCE_TRANSFER_DESCRIPTION
											.length() + 1));
				} catch (NumberFormatException e) {
					Saros.log("Could not parse time from description: "
							+ description, e);
					time = 0; // HACK
				}

				TimedActivity timedActivity = new TimedActivity(activity, time);

				ISessionManager sm = Saros.getDefault().getSessionManager();
				sm.getSharedProject().getSequencer().exec(timedActivity);
			}

			// /* wait for complete transfer. */
			// while (monitor.isAlive() && monitor.isRunning()) {
			// Thread.sleep(500);
			// }
			// monitor.closeMonitor(true);

			log.info("Received resource " + request.getFileName());

		} catch (Exception e) {
			log.warn("Failed to receive " + request.getFileName(), e);
		}
	}

	@Deprecated
	private void receiveResourceOld(FileTransferRequest request) {
		try {

			JID from = new JID(request.getRequestor());
			Path path = new Path(request.getFileName());

			log.debug("Receiving resource from" + from.toString() + ": "
					+ request.getFileName());

			InputStream in = request.accept().recieveFile();

			boolean handledByInvitation = false;
			for (IInvitationProcess process : processes) {
				if (process.getPeer().equals(from)) {
					process.resourceReceived(from, path, in);
					handledByInvitation = true;
				}
			}

			if (!handledByInvitation) {
				FileActivity activity = new FileActivity(
						FileActivity.Type.Created, path, in);

				int time;
				String description = request.getDescription();
				try {
					time = Integer
							.parseInt(description
									.substring(RESOURCE_TRANSFER_DESCRIPTION
											.length() + 1));
				} catch (NumberFormatException e) {
					Saros.log("Could not parse time from description: "
							+ description, e);
					time = 0; // HACK
				}

				TimedActivity timedActivity = new TimedActivity(activity, time);

				ISessionManager sm = Saros.getDefault().getSessionManager();
				sm.getSharedProject().getSequencer().exec(timedActivity);
			}

			log.info("Received resource " + request.getFileName());

		} catch (Exception e) {
			log.warn("Failed to receive " + request.getFileName(), e);
		}
	}

	private void transferFile(FileTransferData transferData)
			throws CoreException, XMPPException, IOException {

		log.info("Sending file " + transferData.path);

		JID recipient = transferData.recipient;

		// SessionManager sm = Saros.getDefault().getSessionManager();
		// IProject project = sm.getSharedProject().getProject();

		String description = RESOURCE_TRANSFER_DESCRIPTION;
		if (transferData.timestamp >= 0) {
			description = description + ':' + transferData.timestamp;
		}

		if (getFileTransferModeViaChat() || transferData.callback == null) {

			if (transferData.content == null)
				readFile(transferData);

			File sendFile = new File(transferData.path.toString());
			if (!sendFile.exists()) {
				log.error("File not exist");
			}

			sendChatTransfer(transferData.path.toString(), description,
					transferData.content, recipient);

			log.info("Sent file " + transferData.path + " (by ChatTransfer)");
			return;
		}

		// try {

		
	
		if (jingle
				&& (jingleManager.getState(recipient) != JingleConnectionState.ERROR)) {
			log.info("Sent file " + transferData.path + " (with Jingle)");

			JingleFileTransferProcessMonitor monitor = new JingleFileTransferProcessMonitor();
			/* create file transfer. */
			JingleFileTransferData data = new JingleFileTransferData();

			/* only for testing. */
			// transferData.path.toString();
			data.file_project_path = transferData.path.toString();
			data.project_name = transferData.project.getName();
			data.type = FileTransferType.RESOURCE_TRANSFER;
			data.recipient = recipient;
			data.sender = new JID(connection.getUser());
			// data.filesize = transferData.filesize;

			/* read content data. */
			File f = new File(transferData.project.getFile(transferData.path)
					.getLocation().toOSString());
			data.filesize = f.length();
			data.content = new byte[(int) data.filesize];

			try {
				InputStream in = transferData.project
						.getFile(transferData.path).getContents();
				in.read(data.content, 0, (int) data.filesize);
			} catch (Exception e) {
				e.printStackTrace();
				data.content = null;
				log.error("Error during read file content for transfer!");
			}

			jingleManager.createOutgoingJingleFileTransfer(recipient,
					new JingleFileTransferData[] { data }, monitor);
			
			/* inform callback. */
			if (transferData.callback != null)
				transferData.callback.fileSent(transferData.path);

		} else {

			/* Fallback to transfer all data into one archive file. */
			transferData.callback.jingleFallback();
			
			/* old version. new files transfer with archive file*/
//			sendSingleFileWithIBB(transferData);

		}
		// }
		// catch (Exception e) {
		//
		// }

//		if (transferData.callback != null)
//			transferData.callback.fileSent(transferData.path);
	}

	/**
	 * send single file from queue with xmpp message.
	 * @param transferData
	 * @throws CoreException
	 * @throws XMPPException
	 * @throws IOException
	 */
	private void sendSingleFileWithIBB(FileTransferData transferData)
			throws CoreException, XMPPException, IOException{

		
		JID recipient = transferData.recipient;

		// SessionManager sm = Saros.getDefault().getSessionManager();
		// IProject project = sm.getSharedProject().getProject();

		String description = RESOURCE_TRANSFER_DESCRIPTION;
		
		OutgoingFileTransfer
				.setResponseTimeout(MAX_TRANSFER_RETRIES * 1000);
		OutgoingFileTransfer transfer = fileTransferManager
				.createOutgoingFileTransfer(recipient.toString());

		/* get file from project. */
		// File f = new
		// File(transferData.project.getFile(transferData.path)
		// .getLocation().toOSString());
		// File f =
		// transferData.project.getFile(transferData.path).getProjectRelativePath().toFile();
		IFile f = transferData.project.getFile(transferData.path);

		if (f.exists()) {
			log.debug("file exists and will be send :" + f.getName() + " "
					+ f.getLocation());
			/* set path in description */
			description = description + ":" + transferData.path;
			/* send file */
			transfer.sendFile(new File(f.getLocation().toString()),
					description);
		} else {
			log.warn("file NOT exists. " + f.getLocation());
			// TODO: bessere exception auslösen. nur zum aktuellen
			// test
			throw new IOException("File not exists.");
		}

		FileTransferProcessMonitor monitor = new FileTransferProcessMonitor(
				transfer);
		/* wait for complete transfer. */
		while (monitor.isAlive() && monitor.isRunning()) {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		monitor.closeMonitor(true);

		if (transfer
				.getStatus()
				.equals(
						org.jivesoftware.smackx.filetransfer.FileTransfer.Status.complete)) {
			log.debug("transfer complete");
		}

		// // HACK file size
		// OutputStream out = transfer.sendFile(transferData.path
		// .toString(), 1, description);
		//
		// if (out == null || transfer.getException() != null)
		// throw new XMPPException(transfer.getException());
		//
		// if (transferData.content == null) {
		// byte[] buffer = new byte[1000];
		// int length = -1;
		// InputStream in = project.getFile(transferData.path)
		// .getContents();
		// while ((length = in.read(buffer)) >= 0) {
		// out.write(buffer, 0, length);
		// }
		// in.close();
		// } else {
		// out.write(transferData.content, 0,
		// (int) transferData.filesize);
		// }
		//
		// out.close();

		// log.info("Sent file " + transferData.path);
		
		if (transferData.callback != null)
			transferData.callback.fileSent(transferData.path);
	}
	
	private FileList receiveFileList(File file) {
		log.info("Receiving " + file.getName() + " path "
				+ file.getAbsolutePath());

		FileList fileList = null;
		try {

			LineNumberReader reader = new LineNumberReader(new FileReader(file));
			String sb = "";
			String line = null;
			while ((line = reader.readLine()) != null) {
				sb += line;
			}
			fileList = new FileList(sb.toString());

			log.info("Received " + file.getName());

			/* delete transfered file. */
			file.delete();

		} catch (Exception e) {
			log.error(e);
			// Saros.log("Exception while receiving file list", e);
			// TODO retry? but we dont catch any exception here,
			// smack might not throw them up
		}

		return fileList;
	}

	/**
	 * Receive file and save temporary.
	 * @param request transfer request of incoming file.
	 * @return File object of received file
	 */
	private File receiveFile(FileTransferRequest request){
		File archiveFile = new File("./incoming_archive.zip");
		log.debug("Archive file: "+archiveFile.getAbsolutePath());
		try {
			final IncomingFileTransfer transfer = request.accept();
			
			IFileTransferCallback callback = null;
			
			/* get IInvitationprocess for monitoring. */
			JID fromJID = new JID(request.getRequestor());
			for (IInvitationProcess process : processes) {
				if (process.getPeer().equals(fromJID)){
					/* set callback. */
					callback = process;
				}
			}
			
			/*monitoring of transfer process*/
			FileTransferProcessMonitor monitor = new FileTransferProcessMonitor(
					transfer,callback);
			
			/* receive file. */
			transfer.recieveFile(archiveFile);
			
			/* wait for complete transfer. */
			while (monitor.isAlive() && monitor.isRunning()) {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
			monitor.closeMonitor(true);
			/* read file.  */
			
//			FileOutputStream out = new FileOutputStream(archiveFile.getName());
//				
//			InputStream in = transfer.recieveFile();
//
//			byte[] buffer = new byte[1024];
//			int bytesRead = 0;
//			
//			while ((bytesRead = in.read(buffer, 0, 1024)) != -1) {
//				out.write(buffer, 0, bytesRead);
//			}
//			in.close();
//			out.close();
//			log.debug("Close input stream");

			
			
		} catch (Exception e) {
			log.error("Error in Incoming File: ", e);
			return null;
			// e.printStackTrace();
			// Saros.log("Exception while receiving file list", e);
			// TODO retry? but we dont catch any exception here,
			// smack might not throw them up
		}

		return archiveFile;
	}
	
	private FileList receiveFileListBufferByteArray(FileTransferRequest request) {
		FileList fileList = null;
		try {
			final IncomingFileTransfer transfer = request.accept();

			InputStream in = transfer.recieveFile();

			byte[] buffer = new byte[1024];
			int bytesRead;
			String sb = new String();
			while ((bytesRead = in.read(buffer, 0, 1024)) != -1) {
				sb += new String(buffer, 0, bytesRead).toString();
//				System.out.println("incomming: " + sb);
			}
			in.close();
			log.debug("Close input stream");
			fileList = new FileList(sb.toString());

		} catch (Exception e) {
			log.error("Error in Incoming File List: ", e);
			return null;
			// e.printStackTrace();
			// Saros.log("Exception while receiving file list", e);
			// TODO retry? but we dont catch any exception here,
			// smack might not throw them up
		}

		return fileList;
	}

	@Deprecated
	private FileList receiveFileList(FileTransferRequest request) {
		log.info("Receiving file list");

		FileList fileList = null;
		try {
			final IncomingFileTransfer transfer = request.accept();

			InputStream in = transfer.recieveFile();
			BufferedReader reader = new BufferedReader(
					new InputStreamReader(in));
			StringBuffer sb = new StringBuffer();

			try {
				String line = null;
				/* TODO: an dieser Stelle kommt es zu einem DeadLock. */
				while ((line = reader.readLine()) != null) {
					System.out.println(line);
					sb.append(line + "\n");
				}
			} catch (Exception e) {
				log.error(e.getMessage());
				Saros.log("Error while receiving file list", e);
			} finally {
				reader.close();
			}

			fileList = new FileList(sb.toString());

			log.info("Received file list");

		} catch (Exception e) {
			log.error(e.getMessage());
			Saros.log("Exception while receiving file list", e);
			// TODO retry? but we dont catch any exception here,
			// smack might not throw them up
		}

		return fileList;
	}

	private void putIncomingChat(JID jid, String thread) {
		if (!chats.containsKey(jid)) {

			// TODO: Änderung für Smack 3
			// Chat chat = this.chatmanager.createChat(jid.toString(), thread,
			// this);
			Chat chat = this.chatmanager.getThreadChat(thread);
			chats.put(jid, chat);
		}

	}

	private Chat getChat(JID jid) {
		if (connection == null)
			throw new NullPointerException("Connection can't be null.");

		Chat chat = chats.get(jid);

		if (chat == null) {

			// ChatManager chatmanager = connection.getChatManager();
			// chat = new Chat(connection, jid.toString());

			// TODO: Änderung für Smack 3 : Listener angeben
			chat = this.chatmanager.createChat(jid.toString(), this);
			chats.put(jid, chat);
		}

		return chat;
	}

	private void setProxyPort(XMPPConnection connection) {

		IPreferenceStore preferenceStore = Saros.getDefault()
				.getPreferenceStore();
		// TODO: Änderung für smack 3 : filetransfer have to be implements new
		fileTransferManager.getProperties().setProperty(FileTransferNegotiator.AVOID_SOCKS5, "true");
		fileTransferManager.getProperties().setProperty(IBBTransferNegotiator.PROPERTIES_BLOCK_SIZE, preferenceStore.getString(PreferenceConstants.CHATFILETRANSFER_CHUNKSIZE));
		// fileTransferManager.getProperties().setProperty(Socks5TransferNegotiator.PROPERTIES_PORT,
		// preferenceStore.getString(PreferenceConstants.FILE_TRANSFER_PORT));

	}

	private boolean getFileTransferModeViaChat() {
		return m_bFileTransferByChat
				|| Saros.getDefault().getPreferenceStore().getBoolean(
						PreferenceConstants.FORCE_FILETRANSFER_BY_CHAT);

	}

	@Deprecated
	public void incommingFileTransfer(JingleFileTransferProcessMonitor monitor) {
		log.info("Incomming Jingle File Transfer");
		this.monitor = monitor;
		while (!monitor.isDone()) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		// jingleManager.terminateJingleSession();
	}

	public void incommingFileList(String fileList_content, JID recipient) {
		FileList fileList = null;
		log.info("incoming file list");
		try {
			fileList = new FileList(fileList_content);
		} catch (XmlPullParserException e) {

			e.printStackTrace();
		} catch (IOException e) {

			e.printStackTrace();
		}
		for (IInvitationProcess process : processes) {
			if (process.getPeer().equals(recipient))
				process.fileListReceived(recipient, fileList);
		}

	}

	public void exceptionOccured(JingleSessionException exception) {
		// TODO: exception weiter geben oder für Fallback verwenden!
		log.error("Jingle Session Exception");

		// this.JingleError = true;
		/* jingle exception with given jid */
		if (exception.getJID() != null) {
			/* inform invitation process. */
			for (IInvitationProcess process : processes) {
				if (process.getPeer().equals(exception.getJID())){
					/* set jingle connection error. */
					jingleManager.setJingleErrorState(exception.getJID());
					/* fallback in invitation process*/
					process.jingleFallback();
					/* send error state to recipient.*/
					sendMessage(exception.getJID(), PacketExtensions
							.createJingleErrorExtension());
					log.debug("jingle fallback. send error message to "+exception.getJID());
				}
			}
//			ErrorMessageDialog.showErrorMessage("Error during Jingle Transfer. Fallback to IBB.");
			
		} else {
			ErrorMessageDialog.showErrorMessage(exception);
		}
	}

	public void incomingResourceFile(JingleFileTransferData data,
			InputStream input) {
		log.debug("incoming file");
		receiveResource(data, input);
	}





}

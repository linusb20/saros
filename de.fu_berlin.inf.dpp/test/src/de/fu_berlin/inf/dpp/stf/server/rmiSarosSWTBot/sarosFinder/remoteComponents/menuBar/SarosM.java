package de.fu_berlin.inf.dpp.stf.server.rmiSarosSWTBot.sarosFinder.remoteComponents.menuBar;

import java.rmi.Remote;
import java.rmi.RemoteException;

import de.fu_berlin.inf.dpp.net.JID;

public interface SarosM extends Remote {

    public SarosPreferences preferences() throws RemoteException;

    /**
     * Creates an account with GUI, which should be done with the following
     * steps:
     * <ol>
     * <li>Click menu "Saros" -> "Create Account"</li>
     * <li>confirm the popup window "Create New User Account" with the given
     * parameters</li>
     * </ol>
     * 
     * @param jid
     *            a JID which is used to identify the users of the Jabber
     *            network, more about it please see {@link JID}.
     * @param password
     *            the password of the new account.
     * 
     *            TODO not implement yet.
     * @throws RemoteException
     */
    public void creatAccount(JID jid, String password) throws RemoteException;

}

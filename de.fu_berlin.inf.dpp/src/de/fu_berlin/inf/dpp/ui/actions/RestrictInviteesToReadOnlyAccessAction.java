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
package de.fu_berlin.inf.dpp.ui.actions;

import org.apache.log4j.Logger;
import org.eclipse.jface.action.Action;
import org.picocontainer.annotations.Inject;

import de.fu_berlin.inf.dpp.User;
import de.fu_berlin.inf.dpp.User.Permission;
import de.fu_berlin.inf.dpp.annotations.Component;
import de.fu_berlin.inf.dpp.project.AbstractSarosSessionListener;
import de.fu_berlin.inf.dpp.project.AbstractSharedProjectListener;
import de.fu_berlin.inf.dpp.project.ISarosSession;
import de.fu_berlin.inf.dpp.project.ISarosSessionListener;
import de.fu_berlin.inf.dpp.project.ISharedProjectListener;
import de.fu_berlin.inf.dpp.project.SarosSessionManager;
import de.fu_berlin.inf.dpp.ui.ImageManager;
import de.fu_berlin.inf.dpp.ui.SarosUI;
import de.fu_berlin.inf.dpp.util.Utils;

/**
 * this action remove all remote users with {@link User.Permission#WRITE_ACCESS}
 * from project. Only the project host has {@link User.Permission#WRITE_ACCESS}
 * after this action is executed.
 */
@Component(module = "action")
public class RestrictInviteesToReadOnlyAccessAction extends Action {

    public static final String ACTION_ID = RestrictInviteesToReadOnlyAccessAction.class
        .getName();

    private static final Logger log = Logger
        .getLogger(RestrictInviteesToReadOnlyAccessAction.class.getName());

    @Inject
    protected SarosUI sarosUI;

    protected ISharedProjectListener sharedProjectListener = new AbstractSharedProjectListener() {
        @Override
        public void permissionChanged(User user) {
            updateEnablement();
        }
    };

    protected ISarosSessionListener sessionListener = new AbstractSarosSessionListener() {

        @Override
        public void sessionStarted(ISarosSession newSarosSession) {
            newSarosSession.addListener(sharedProjectListener);
            updateEnablement();
        }

        @Override
        public void sessionEnded(ISarosSession oldSarosSession) {
            oldSarosSession.removeListener(sharedProjectListener);
            updateEnablement();
        }
    };

    protected SarosSessionManager sessionManager;

    public RestrictInviteesToReadOnlyAccessAction(
        SarosSessionManager sessionManager) {
        super("Restrict invitees to read-only access");
        this.sessionManager = sessionManager;

        setImageDescriptor(ImageManager
            .getImageDescriptor("icons/elcl16/restrictinviteestoreadonlyaccess.png"));
        setToolTipText("Restrict Invitees To Read-Only Access");
        setId(ACTION_ID);

        /*
         * if SessionView is not "visible" on session start up this constructor
         * will be called after session started (and the user uses this view)
         * That's why the method sessionListener.sessionStarted has to be called
         * manually. If not the sharedProjectListener is not added to the
         * session and the action enablement cannot be updated.
         */
        if (sessionManager.getSarosSession() != null) {
            sessionListener.sessionStarted(sessionManager.getSarosSession());
        }

        sessionManager.addSarosSessionListener(sessionListener);
        updateEnablement();
    }

    /**
     * @review runSafe OK
     */
    @Override
    public void run() {
        Utils.runSafeSync(log, new Runnable() {
            public void run() {
                runRestriction();
            }
        });
    }

    public void runRestriction() {

        ISarosSession sarosSession = sessionManager.getSarosSession();
        for (User user : sarosSession.getParticipants()) {
            if (user.hasWriteAccess() && !user.isHost()) {
                sarosUI.performPermissionChange(user,
                    Permission.READONLY_ACCESS);
            } else if (user.isHost() && !user.hasWriteAccess()) {
                sarosUI.performPermissionChange(user, Permission.WRITE_ACCESS);
            }
        }
        updateEnablement();
    }

    protected void updateEnablement() {
        ISarosSession sarosSession = sessionManager.getSarosSession();
        setEnabled((sarosSession != null && sarosSession.isHost() && (sarosSession
            .getUsersWithWriteAccess().size() > 0)));
    }
}

package de.fu_berlin.inf.dpp.stf.client.testProject.testsuits;

import static org.junit.Assert.assertTrue;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.jivesoftware.smack.XMPPException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

import de.fu_berlin.inf.dpp.Saros;
import de.fu_berlin.inf.dpp.net.JID;
import de.fu_berlin.inf.dpp.stf.STF;
import de.fu_berlin.inf.dpp.stf.client.ConfigTester;
import de.fu_berlin.inf.dpp.stf.client.Tester;
import de.fu_berlin.inf.dpp.stf.client.testProject.helpers.MakeOperationConcurrently;

public class STFTest extends STF {

    @Rule
    public TestName name = new TestName();

    public enum TypeOfCreateProject {
        NEW_PROJECT, EXIST_PROJECT, EXIST_PROJECT_WITH_COPY, EXIST_PROJECT_WITH_COPY_AFTER_CANCEL_LOCAL_CHANGE
    }

    public enum TypeOfShareProject {
        SHARE_PROJECT, SHARE_PROJECT_PARTICALLY, ADD_SESSION
    }

    /**********************************************
     * 
     * Tester
     * 
     **********************************************/
    public static Tester alice;
    public static Tester bob;
    public static Tester carl;
    public static Tester dave;
    public static Tester edna;

    public static enum TypeOfTester {
        ALICE, BOB, CARL, DAVE, EDNA
    }

    public static List<Tester> activeTesters = new ArrayList<Tester>();

    public static List<Tester> initTesters(TypeOfTester... testers)
        throws RemoteException {
        List<Tester> result = new ArrayList<Tester>();
        for (TypeOfTester t : testers) {
            switch (t) {
            case ALICE:
                alice = ConfigTester.newAlice();
                result.add(alice);
                break;
            case BOB:
                bob = ConfigTester.newBob();
                result.add(bob);
                break;
            case CARL:
                carl = ConfigTester.newCarl();
                result.add(carl);
                break;
            case DAVE:
                dave = ConfigTester.newDave();
                result.add(dave);
                break;
            case EDNA:
                edna = ConfigTester.newEdna();
                result.add(edna);
                break;
            default:
                break;
            }
        }
        activeTesters = result;
        return result;
    }

    /**********************************************
     * 
     * test data
     * 
     **********************************************/
    public final static JID TEST_JID = new JID(
        "edna_stf@saros-con.imp.fu-berlin.de/" + Saros.RESOURCE);
    public final static JID TEST_JID2 = new JID(
        "dave_stf@saros-con.imp.fu-berlin.de/" + Saros.RESOURCE);

    /* test data for modifying account */
    public final static String SERVER = "saros-con.imp.fu-berlin.de";
    public final static String NEW_USER_NAME = "new_alice_stf";

    public final static String REGISTERED_USER_NAME = "bob_stf";

    public static final String INVALID_SERVER_NAME = "saros-con";

    // need to change, if you want to test creatAccount
    public static JID JID_TO_CREATE = new JID(
        ("test3@saros-con.imp.fu-berlin.de/" + Saros.RESOURCE));

    public final static JID JID_TO_ADD = new JID(
        ("bob_stf@" + SERVER + "/" + Saros.RESOURCE));

    public final static JID JID_TO_CHANGE = new JID((NEW_USER_NAME + "@"
        + SERVER + "/" + Saros.RESOURCE));

    public static String PASSWORD = "dddfffggg";
    public static String NO_MATCHED_REPEAT_PASSWORD = "dd";

    /* Project name */
    public static final String PROJECT1 = "Foo_Saros1";
    protected static final String PROJECT1_COPY = "copy_of_FOO_Saros1";
    /** Name chosen by Saros if {@link STFTest#PROJECT1} already exists. */
    public static final String PROJECT1_NEXT = "Foo_Saros 1";
    public static final String PROJECT2 = "Foo_Saros2";
    public static final String PROJECT3 = "Foo_Saros3";

    /* Folder name */
    public static final String FOLDER1 = "MyFolder";
    public static final String FOLDER2 = "MyFolder2";

    /* File */
    public static final String FILE1 = "MyFile.xml";
    public static final String FILE2 = "MyFile2.xml";
    public static final String FILE3 = "file.txt";
    public static final String[] path = { PROJECT1, FILE3 };

    /* Package name */
    public static final String PKG1 = "my.pkg";
    public static final String PKG2 = "my.pkg2";
    public static final String PKG3 = "my.pkg3";

    /* class name */
    public static final String CLS1 = "MyClass";
    public static final String CLS2 = "MyClass2";
    public static final String CLS3 = "MyClass3";

    /* class name with suffix */
    public static final String CLS1_SUFFIX = "MyClass" + SUFFIX_JAVA;
    public static final String CLS2_SUFFIX = "MyClass2" + SUFFIX_JAVA;
    public static final String CLS3_SUFFIX = "MyClass3" + SUFFIX_JAVA;

    /* content path */
    public static final String CP1 = "test/STF/" + CLS1 + SUFFIX_JAVA;
    public static final String CP2 = "test/STF/" + CLS2 + SUFFIX_JAVA;
    public static final String CP3 = "test/STF/" + CLS3 + SUFFIX_JAVA;
    public static final String CP1_CHANGE = "test/STF/" + CLS1 + "Change"
        + SUFFIX_JAVA;
    public static final String CP2_CHANGE = "test/STF/" + CLS2 + "Change"
        + SUFFIX_JAVA;

    /* SVN infos */
    protected static final String SVN_REPOSITORY_URL = "http://saros-build.imp.fu-berlin.de/svn/saros";
    protected static final String SVN_PROJECT = "stf_test_project";
    protected static final String SVN_PROJECT_COPY = "copy_of_stf_test_project";
    protected static String SVN_PROJECT_PATH = getOS() == TypeOfOS.MAC ? "stf_tests/stf_test_project"
        : "/stf_tests/stf_test_project";

    protected static final String SVN_PROJECT_URL_SWITCHED = SVN_REPOSITORY_URL
        + "/stf_tests/stf_test_project_copy";
    protected static final String SVN_PKG = "pkg";
    protected static final String SVN_CLS1 = "Test";
    protected static final String SVN_CLS1_SUFFIX = SVN_CLS1 + SUFFIX_JAVA;
    protected static final String SVN_CLS1_FULL_PATH = "/stf_test_project/src/pkg/Test.java";
    protected static final String SVN_CLS1_SWITCHED_URL = "http://saros-build.imp.fu-berlin.de/svn/saros/stf_tests/stf_test_project_copy/src/pkg/Test.java";
    /** Initial commit in stf_test_project. */
    protected static final String SVN_CLS1_REV1 = "2735";
    /** copy from stf_test_project to stf_test_project_copy */
    protected static final String SVN_CLS1_REV2 = "2736";
    /** modified in stf_test_project_copy */
    protected static final String SVN_CLS1_REV3 = "2737";
    /** modified in stf_test_project_copy */
    protected static final String SVN_CLS1_REV4 = "2767";

    /**********************************************
     * 
     * Before/After conditions
     * 
     **********************************************/

    @Before
    public void before() throws Exception {
        closeAllShells();
    }

    @After
    public void after() throws Exception {
        resetWorkbenches();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        resetSaros();
    }

    /**********************************************
     * 
     * often used to define preconditions
     * 
     **********************************************/

    /**
     * bring workbench to a original state before beginning your tests
     * <ul>
     * <li>activate saros-instance workbench</li>
     * <li>close all opened popUp windows</li>
     * <li>close all opened editors</li>
     * <li>delete all existed projects</li>
     * <li>close welcome view, if it is open</li>
     * <li>open java perspective</li>
     * <li>close all unnecessary views</li>
     * </ul>
     * 
     * @throws RemoteException
     */
    public static void setUpWorkbench() throws RemoteException {
        for (Tester tester : activeTesters) {
            tester.bot().activateWorkbench();
            if (tester.bot().isViewOpen("Welcome"))
                tester.bot().view("Welcome").close();
            tester.sarosBot().window().openPerspective();
            closeUnnecessaryViews(tester);
            tester.bot().resetWorkbench();
            tester.noBot().deleteAllProjectsNoGUI();
            tester.sarosBot().views().packageExplorerView().tree()
                .deleteAllProjects();
        }
    }

    public static void closeUnnecessaryViews(Tester tester)
        throws RemoteException {
        if (tester.bot().isViewOpen("Problems"))
            tester.bot().view("Problems").close();

        if (tester.bot().isViewOpen("Javadoc"))
            tester.bot().view("Javadoc").close();

        if (tester.bot().isViewOpen("Declaration"))
            tester.bot().view("Declaration").close();

        if (tester.bot().isViewOpen("Task List"))
            tester.bot().view("Task List").close();

        if (tester.bot().isViewOpen("Outline"))
            tester.bot().view("Outline").close();
    }

    /**
     * bring Saros to a original state before beginning your tests
     * <ul>
     * <li>make automaticReminder disable</li>
     * <li>open sarosViews</li>
     * <li>connect</li>
     * <li>check buddy lists, if all active testers are in contact</li>
     * </ul>
     * 
     * @throws RemoteException
     */
    public static void setUpSaros() throws RemoteException {
        for (Tester tester : activeTesters) {
            tester.noBot().disableAutomaticReminderNoGUI();
            openSarosViews(tester);
            tester.sarosBot().views().buddiesView()
                .connectWith(tester.jid, tester.password);
        }
        resetBuddies();
    }

    public static void openSarosViews(Tester tester) throws RemoteException {
        if (!tester.bot().isViewOpen(VIEW_SAROS_BUDDIES)) {
            tester.sarosBot().window()
                .showViewWithName(NODE_SAROS, VIEW_SAROS_BUDDIES);
        }
        if (!tester.bot().isViewOpen(VIEW_SAROS_SESSION))
            tester.sarosBot().window()
                .showViewWithName(NODE_SAROS, VIEW_SAROS_SESSION);

        if (!tester.bot().isViewOpen(VIEW_REMOTE_SCREEN))
            tester.sarosBot().window()
                .showViewWithName(NODE_SAROS, VIEW_REMOTE_SCREEN);

        if (!tester.bot().isViewOpen(VIEW_SAROS_CHAT))
            tester.sarosBot().window()
                .showViewWithName(NODE_SAROS, VIEW_SAROS_CHAT);
    }

    /**
     * A convenient function to quickly build a session which share a java
     * project with a class.
     * 
     * @param inviter
     * @param invitees
     * @throws RemoteException
     * @throws InterruptedException
     */
    public static void setUpSessionWithAJavaProjectAndAClass(Tester inviter,
        Tester... invitees) throws RemoteException, InterruptedException {
        inviter.sarosBot().views().packageExplorerView().tree().newC()
            .javaProjectWithClasses(PROJECT1, PKG1, CLS1);
        buildSessionConcurrently(PROJECT1, CM_SHARE_PROJECT,
            TypeOfCreateProject.NEW_PROJECT, inviter, invitees);
    }

    public static void setUpSessionWithJavaProjects(
        Map<String, List<String>> projectsPkgsClasses, Tester inviter,
        Tester... invitees) throws RemoteException {
        List<String> createdProjects = new ArrayList<String>();
        for (Iterator<String> i = projectsPkgsClasses.keySet().iterator(); i
            .hasNext();) {
            String key = i.next();
            if (!createdProjects.contains(key)) {
                createdProjects.add(key);
                inviter.sarosBot().views().packageExplorerView().tree().newC()
                    .javaProject(key);
                List<String> pkgAndclass = projectsPkgsClasses.get(key);
                inviter.sarosBot().views().packageExplorerView()
                    .selectPkg(key, pkgAndclass.get(0)).newC()
                    .cls(key, pkgAndclass.get(0), pkgAndclass.get(1));
            }
        }
    }

    /**********************************************
     * 
     * often used to define afterConditions
     * 
     **********************************************/
    /**
     * For all active testers, reset buddy names, disconnect, delete all
     * projects.
     * 
     * @throws RemoteException
     */
    public static void resetSaros() throws RemoteException {
        for (Tester tester : activeTesters) {
            if (tester != null) {
                // tester.sarosBot().views().buddiesView().resetAllBuddyNameNoGUI();
                tester.sarosBot().views().buddiesView().disconnect();
                tester.noBot().deleteAllProjectsNoGUI();
            }
        }
        resetAllBots();
    }

    public static void resetSaros(Tester... testers) throws RemoteException {
        for (Tester tester : testers) {
            if (tester != null) {
                tester.sarosBot().views().buddiesView()
                    .resetAllBuddyNameNoGUI();
                tester.sarosBot().views().buddiesView().disconnect();
                tester.noBot().deleteAllProjectsNoGUI();
            }
        }
        resetAllBots();
    }

    public static void resetWorkbenches() throws RemoteException {
        for (Tester tester : activeTesters) {
            tester.bot().resetWorkbench();
        }
    }

    public static void closeAllShells() throws RemoteException {
        for (Tester tester : activeTesters) {
            tester.bot().closeAllShells();
        }
    }

    public static void resetDefaultAccount() throws RemoteException {
        for (Tester tester : activeTesters) {
            if (tester != null) {
                if (!tester.sarosBot().saros().preferences()
                    .existsAccount(tester.jid))
                    tester.sarosBot().saros().preferences()
                        .addAccount(tester.jid, tester.password);
                if (!tester.sarosBot().saros().preferences()
                    .isAccountActive(tester.jid))
                    tester.sarosBot().saros().preferences()
                        .activateAccount(tester.jid);
                tester.sarosBot().saros().preferences()
                    .deleteAllNoActiveAccounts();

            }
        }
    }

    public static void resetBuddiesName() throws RemoteException {
        for (int i = 0; i < activeTesters.size(); i++) {
            for (int j = i + 1; j < activeTesters.size(); j++) {
                activeTesters
                    .get(i)
                    .sarosBot()
                    .views()
                    .buddiesView()
                    .renameBuddy(activeTesters.get(j).jid,
                        activeTesters.get(j).getBaseJid());
            }
        }
    }

    public static void resetBuddies() throws RemoteException {
        // check buddy lists.
        for (int i = 0; i < activeTesters.size(); i++) {
            for (int j = i + 1; j < activeTesters.size(); j++) {
                addBuddies(activeTesters.get(i), activeTesters.get(j));
            }
        }
    }

    public static void resetWriteAccess(Tester host, Tester... invitees)
        throws RemoteException {
        for (Tester tester : invitees) {
            if (tester.sarosBot().state().isInSession()
                && tester.sarosBot().state().hasReadOnlyAccess()) {
                host.sarosBot().views().sessionView().selectBuddy(tester.jid)
                    .grantWriteAccess();
            }
        }

        if (host.sarosBot().state().isInSessionNoGUI()
            && !host.sarosBot().state().hasWriteAccessNoGUI()) {
            host.sarosBot().views().sessionView().selectBuddy(host.jid)
                .grantWriteAccess();
        }
    }

    public static void resetFollowModeSequentially(Tester... buddiesFollowing)
        throws RemoteException {
        for (Tester tester : buddiesFollowing) {
            if (tester.sarosBot().state().isInSessionNoGUI()
                && tester.sarosBot().state().isFollowing()) {
                JID followedBuddyJID = tester.sarosBot().state()
                    .getFollowedBuddyJIDNoGUI();
                tester.sarosBot().views().sessionView()
                    .selectBuddy(followedBuddyJID).stopFollowingThisBuddy();
            }
        }
    }

    /**
     * stop the follow-mode of the buddies who are following the local user.
     * 
     * @param buddiesFollowing
     *            the list of the buddies who are following the local user.
     * @throws RemoteException
     * @throws InterruptedException
     */
    public static void resetFollowModeConcurrently(Tester... buddiesFollowing)
        throws RemoteException, InterruptedException {
        List<Callable<Void>> stopFollowTasks = new ArrayList<Callable<Void>>();
        for (int i = 0; i < buddiesFollowing.length; i++) {
            final Tester tester = buddiesFollowing[i];
            stopFollowTasks.add(new Callable<Void>() {
                public Void call() throws Exception {
                    JID followedBuddyJID = tester.sarosBot().state()
                        .getFollowedBuddyJIDNoGUI();
                    tester.sarosBot().views().sessionView()
                        .selectBuddy(followedBuddyJID).stopFollowingThisBuddy();
                    return null;
                }
            });
        }
        MakeOperationConcurrently.workAll(stopFollowTasks);
    }

    // public static void resetSharedProject(Tester host) throws RemoteException
    // {
    // host.sarosBot().views().packageExplorerView().selectSrc(PROJECT1)
    // .deleteAllItems();
    // host.sarosBot().views().packageExplorerView().tree().newC()
    // .cls(PROJECT1, PKG1, CLS1);
    // // host.bot().sleep(1000);
    // }

    /**********************************************
     * 
     * often used convenient functions
     * 
     **********************************************/

    public static void reBuildSession(Tester host, Tester... invitees)
        throws RemoteException {
        if (!host.sarosBot().state().isInSessionNoGUI()) {
            for (Tester tester : invitees) {
                buildSessionSequentially(PROJECT1, CM_SHARE_PROJECT,
                    TypeOfCreateProject.EXIST_PROJECT, host, tester);
            }
        }
    }

    public static void createSameJavaProjectByActiveTesters()
        throws RemoteException {
        for (Tester tester : activeTesters) {
            tester.sarosBot().views().packageExplorerView().tree().newC()
                .javaProjectWithClasses(PROJECT1, PKG1, CLS1);
        }
    }

    public static void createProjectWithFileBy(Tester... testers)
        throws RemoteException {
        for (Tester tester : testers) {
            tester.sarosBot().views().packageExplorerView().tree().newC()
                .project(PROJECT1);
            tester.sarosBot().views().packageExplorerView()
                .selectFolder(PROJECT1).newC().file(FILE3);
            tester.bot().waitUntilEditorOpen(FILE3);
        }
    }

    public static void deleteAllProjectsByActiveTesters()
        throws RemoteException {
        for (Tester tester : activeTesters) {
            tester.noBot().deleteAllProjectsNoGUI();
        }
    }

    public static void disConnectByActiveTesters() throws RemoteException {
        for (Tester tester : activeTesters) {
            if (tester != null) {
                tester.sarosBot().views().buddiesView().disconnect();
            }
        }
    }

    public static void deleteFoldersByActiveTesters(String... folders)
        throws RemoteException {
        for (Tester tester : activeTesters) {
            for (String folder : folders) {
                if (tester.sarosBot().views().packageExplorerView()
                    .selectProject(PROJECT1).existsWithRegex(folder))
                    tester.noBot().deleteFolderNoGUI(PROJECT1, folder);
            }
        }
    }

    public static void buildSessionSequentially(String projectName,
        String howToShareProject, TypeOfCreateProject usingWhichProject,
        Tester inviter, Tester... invitees) throws RemoteException {
        String[] baseJIDOfInvitees = getPeersBaseJID(invitees);

        inviter.sarosBot().views().packageExplorerView()
            .selectProject(projectName).saros()
            .shareProjectWith(howToShareProject, baseJIDOfInvitees);
        for (Tester invitee : invitees) {
            invitee.bot().shell(SHELL_SESSION_INVITATION).confirm(FINISH);
            invitee.sarosBot().confirmShellAddProjectUsingWhichProject(
                projectName, usingWhichProject);
        }
    }

    // ********** Component, which consist of other simple functions ***********

    public static void buildSessionConcurrently(final String projectName,
        String howToShareProject, final TypeOfCreateProject usingWhichProject,
        Tester inviter, Tester... invitees) throws RemoteException,
        InterruptedException {

        log.trace("alice.shareProjectParallel");
        inviter.sarosBot().views().packageExplorerView()
            .selectProject(projectName).saros()
            .shareProjectWith(howToShareProject, getPeersBaseJID(invitees));

        List<Callable<Void>> joinSessionTasks = new ArrayList<Callable<Void>>();
        for (final Tester invitee : invitees) {
            joinSessionTasks.add(new Callable<Void>() {
                public Void call() throws Exception {
                    invitee.bot().shell(SHELL_SESSION_INVITATION)
                        .confirm(FINISH);
                    invitee.sarosBot().confirmShellAddProjectUsingWhichProject(
                        projectName, usingWhichProject);
                    invitee.sarosBot().condition().waitUntilIsInSession();
                    return null;
                }
            });
        }
        log.trace("workAll(joinSessionTasks)");
        MakeOperationConcurrently.workAll(joinSessionTasks);
    }

    /**
     * Define the leave session with the following steps.
     * <ol>
     * <li>The host(alice) leave session first.</li>
     * <li>Then other invitees confirm the windonws "Closing the Session"
     * concurrently</li>
     * </ol>
     * 
     * @throws RemoteException
     * @throws InterruptedException
     */
    public static void leaveSessionHostFirst(Tester host)
        throws RemoteException, InterruptedException {
        List<Callable<Void>> closeSessionTasks = new ArrayList<Callable<Void>>();
        for (final Tester tester : activeTesters) {
            if (tester != host) {
                if (tester.sarosBot().state().isInSessionNoGUI()) {
                    closeSessionTasks.add(new Callable<Void>() {
                        public Void call() throws Exception {
                            // Need to check for isDriver before leaving.
                            tester.sarosBot().confirmShellClosingTheSession();
                            return null;
                        }
                    });
                }
            }
        }
        host.sarosBot().views().sessionView().leaveTheSession();
        MakeOperationConcurrently.workAll(closeSessionTasks);
    }

    /**
     * Define the leave session with the following steps.
     * <ol>
     * <li>The musicians bob and carl leave the session first.(concurrently)</li>
     * <li>wait until bob and carl are really not in the session using
     * "waitUntilAllPeersLeaveSession", then leave the host alice.</li>
     * </ol>
     * make sure,
     * 
     * 
     * @throws RemoteException
     * @throws InterruptedException
     */
    public static void leaveSessionPeersFirst() throws RemoteException,
        InterruptedException {
        Tester host = null;
        List<JID> peerJIDs = new ArrayList<JID>();
        List<Callable<Void>> leaveTasks = new ArrayList<Callable<Void>>();
        for (final Tester tester : activeTesters) {

            if (tester.sarosBot().state().isHostNoGUI()) {
                host = tester;
            } else {
                peerJIDs.add(tester.jid);
                leaveTasks.add(new Callable<Void>() {
                    public Void call() throws Exception {
                        tester.sarosBot().views().sessionView()
                            .leaveTheSession();
                        return null;
                    }
                });
            }
        }

        MakeOperationConcurrently.workAll(leaveTasks);
        if (host != null) {
            host.sarosBot().condition().waitUntilAllPeersLeaveSession(peerJIDs);
            host.bot().view(VIEW_SAROS_SESSION)
                .toolbarButton(TB_LEAVE_THE_SESSION).click();
            host.sarosBot().condition().waitUntilIsNotInSession();
        }

    }

    /**
     * the local user can be concurrently followed by many other users.
     * 
     * @param buddiesTofollow
     *            the list of the buddies who want to follow the local user.
     * @throws RemoteException
     * @throws InterruptedException
     */
    public static void setFollowMode(final Tester followedBuddy,
        Tester... buddiesTofollow) throws RemoteException, InterruptedException {
        List<Callable<Void>> followTasks = new ArrayList<Callable<Void>>();
        for (int i = 0; i < buddiesTofollow.length; i++) {
            final Tester tester = buddiesTofollow[i];
            followTasks.add(new Callable<Void>() {
                public Void call() throws Exception {
                    tester.sarosBot().views().sessionView()
                        .selectBuddy(followedBuddy.jid).followThisBuddy();
                    return null;
                }
            });
        }
        MakeOperationConcurrently.workAll(followTasks);
    }

    /**
     * add buddy with GUI, which should be performed by both the users.
     * 
     * @param peers
     *            the user, which should be added in your contact list
     * @throws RemoteException
     * 
     * 
     */
    public static void addBuddies(Tester host, Tester... peers)
        throws RemoteException {
        for (Tester peer : peers) {
            if (!host.sarosBot().views().buddiesView().hasBuddyNoGUI(peer.jid)) {
                host.sarosBot().views().buddiesView().addANewBuddy(peer.jid);
                peer.bot().waitUntilShellIsOpen(
                    SHELL_REQUEST_OF_SUBSCRIPTION_RECEIVED);
                peer.bot().shell(SHELL_REQUEST_OF_SUBSCRIPTION_RECEIVED)
                    .activate();
                peer.bot().shell(SHELL_REQUEST_OF_SUBSCRIPTION_RECEIVED).bot()
                    .button(OK).click();
                peer.bot().sleep(500);
            }
        }
    }

    /**
     * Remove given contact from Roster without GUI, if contact was added before
     * 
     * @throws XMPPException
     */
    public static void deleteBuddiesNoGUI(Tester buddy,
        Tester... deletedBuddies) throws RemoteException, XMPPException {
        for (Tester deletedBuddy : deletedBuddies) {
            if (!buddy.sarosBot().views().buddiesView()
                .hasBuddyNoGUI(deletedBuddy.jid))
                return;
            buddy.sarosBot().views().buddiesView()
                .deleteBuddyNoGUI(deletedBuddy.jid);
            deletedBuddy.sarosBot().views().buddiesView()
                .confirmShellRemovelOfSubscription();
        }
    }

    /**
     * Remove given contact from Roster with GUI, if contact was added before.
     */
    public static void deleteBuddies(Tester buddy, Tester... deletedBuddies)
        throws RemoteException {
        for (Tester deletedBuddy : deletedBuddies) {
            if (!buddy.sarosBot().views().buddiesView()
                .hasBuddyNoGUI(deletedBuddy.jid))
                return;
            buddy.sarosBot().views().buddiesView()
                .deleteBuddy(deletedBuddy.jid);
            deletedBuddy.sarosBot().views().buddiesView()
                .confirmShellRemovelOfSubscription();
        }

    }

    public static void shareYourScreen(Tester buddy, Tester selectedBuddy)
        throws RemoteException {
        buddy.sarosBot().views().sessionView()
            .shareYourScreenWithSelectedBuddy(selectedBuddy.jid);
        selectedBuddy.bot().waitUntilShellIsOpen(
            SHELL_INCOMING_SCREENSHARING_SESSION);
        selectedBuddy.bot().shell(SHELL_INCOMING_SCREENSHARING_SESSION).bot()
            .button(YES).click();
    }

    /**
     * This method is same as
     * 
     * . The difference to buildSessionConcurrently is that the invitation
     * process is activated by clicking the toolbarbutton
     * "open invitation interface" in the roster view.
     * 
     * @param projectName
     *            the name of the project which is in a session now.
     * @param invitees
     *            the user whom you want to invite to your session.
     * @throws RemoteException
     * @throws InterruptedException
     */
    public static void inviteBuddies(final String projectName,
        final TypeOfCreateProject usingWhichProject, Tester inviter,
        Tester... invitees) throws RemoteException, InterruptedException {
        inviter.sarosBot().views().sessionView()
            .openInvitationInterface(getPeersBaseJID(invitees));
        List<Callable<Void>> joinSessionTasks = new ArrayList<Callable<Void>>();
        for (final Tester tester : invitees) {
            joinSessionTasks.add(new Callable<Void>() {
                public Void call() throws Exception {
                    tester.bot().shell(SHELL_SESSION_INVITATION)
                        .confirm(FINISH);
                    tester.sarosBot().confirmShellAddProjectUsingWhichProject(
                        projectName, usingWhichProject);
                    return null;
                }
            });
        }
        log.trace("workAll(joinSessionTasks)");
        MakeOperationConcurrently.workAll(joinSessionTasks);
    }

    public void waitsUntilTransferedDataIsArrived(Tester buddy)
        throws RemoteException {
        buddy.bot().sleep(500);
    }

    /**********************************************
     * 
     * inner functions
     * 
     **********************************************/
    private static void resetAllBots() {
        alice = bob = carl = dave = edna = null;
        activeTesters.clear();
        assertTrue(activeTesters.isEmpty());
    }

    private static String[] getPeersBaseJID(Tester... peers) {
        String[] peerBaseJIDs = new String[peers.length];
        for (int i = 0; i < peers.length; i++) {
            peerBaseJIDs[i] = peers[i].getBaseJid();
        }
        return peerBaseJIDs;
    }
}

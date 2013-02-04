package de.fu_berlin.inf.dpp.stf.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.MethodRule;
import org.junit.rules.TestWatchman;
import org.junit.runners.model.FrameworkMethod;

import de.fu_berlin.inf.dpp.net.JID;
import de.fu_berlin.inf.dpp.stf.client.tester.AbstractTester;
import de.fu_berlin.inf.dpp.stf.client.util.Util;
import de.fu_berlin.inf.dpp.stf.server.rmi.remotebot.IRemoteWorkbenchBot;
import de.fu_berlin.inf.dpp.test.util.TestThread;

public abstract class StfTestCase {

    private static final Logger LOGGER = Logger.getLogger(StfTestCase.class
        .getName());

    /**
     * JUnit monitor. Do <b>NOT</b> call any method of this instance !
     */
    @Rule
    public final MethodRule watchman = new TestWatchman() {
        @Override
        public void failed(Throwable e, FrameworkMethod method) {
            logMessage("******* " + "TESTCASE "
                + method.getMethod().getDeclaringClass().getName() + ":"
                + method.getName() + " FAILED *******");
            captureScreenshot((method.getMethod().getDeclaringClass().getName()
                + "_" + method.getName()).replace('.', '_'));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(out));
            logMessage(new String(out.toByteArray()));
        }

        @Override
        public void succeeded(FrameworkMethod method) {
            logMessage("******* " + "TESTCASE "
                + method.getMethod().getDeclaringClass().getName() + ":"
                + method.getName() + " SUCCEDED *******");
        }

        @Override
        public void finished(FrameworkMethod method) {
            logMessage("******* " + "TESTCASE "
                + method.getMethod().getDeclaringClass().getName() + ":"
                + method.getName() + " FINISHED *******");
        }

        @Override
        public void starting(FrameworkMethod method) {

            lastTestClass = method.getMethod().getDeclaringClass();

            logMessage("******* " + "STARTING TESTCASE "
                + method.getMethod().getDeclaringClass().getName() + ":"
                + method.getName() + " *******");

        }

        private void logMessage(String message) {
            for (AbstractTester tester : currentTesters) {
                try {
                    tester.remoteBot().logMessage(message);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "could not log message '"
                        + message + "' at remote bot of tester " + tester, e);
                }
            }
        }

        private void captureScreenshot(String name) {

            for (AbstractTester tester : currentTesters) {
                try {
                    tester.remoteBot().captureScreenshot(
                        name + "_" + tester + "_" + System.currentTimeMillis()
                            + ".jpg");
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING,
                        "could capture a screenshot at remote bot of tester "
                            + tester, e);
                }
            }
        }
    };

    private static List<AbstractTester> currentTesters = new ArrayList<AbstractTester>();

    private static List<TestThread> currentTestThreads = new ArrayList<TestThread>();

    private static Class<?> lastTestClass;

    /*
     * Make sure that the JUNIT Ant task does not load every test case with a
     * new class loader or this will NOT work during a regression !
     */

    private static boolean abortAllTests = false;

    @BeforeClass
    public static void checkThreadsBeforeClass() {
        checkAndStopRunningTestThreads(true);
    }

    @Before
    public final void checkThreadsBeforeTest() {
        checkAndStopRunningTestThreads(false);
    }

    /**
     * Calls
     * <ol>
     * <li>reset all buddy names by calling
     * {@linkplain #initTesters(AbstractTester, AbstractTester...)}</li>
     * <li>{@linkplain #setUpWorkbench()}</li>
     * <li>{@linkplain #setUpSaros()}</li>
     * </ol>
     * 
     * @param tester
     *            a tester e.g ALICE
     * @param testers
     *            additional testers e.g BOB, CARL
     * @throws Exception
     */
    public static void select(AbstractTester tester, AbstractTester... testers)
        throws Exception {
        initTesters(tester, testers);
        setUpWorkbench();
        setUpSaros();
    }

    /**
     * Registers the given testers to be participants of the current test case.
     * <b>THIS METHOD SHOULD ALWAYS BE THE FIRST METHOD CALLED IN THE
     * <tt>BEFORECLASS</tt> METHOD.</b>
     * 
     * @param tester
     *            a tester e.g ALICE
     * @param testers
     *            additional testers e.g BOB, CARL
     */
    public static void initTesters(AbstractTester tester,
        AbstractTester... testers) {

        currentTesters.clear();
        currentTesters.add(tester);
        currentTesters.addAll(Arrays.asList(testers));

        for (AbstractTester t : currentTesters)
            if (t == null)
                throw new NullPointerException(
                    "got null reference for a tester object");
    }

    public static List<AbstractTester> getCurrentTesters() {
        return Collections.unmodifiableList(currentTesters);
    }

    /**
     * This method is called after every test case class. Override this method
     * with care! Internal calls {@linkplain #tearDownSaros}
     */
    @AfterClass
    public static void cleanUpSaros() throws Exception {
        tearDownSaros();
    }

    /**
     * Tries to reset Saros to a stable state for the given tester(s). It does
     * that be performing the following actions
     * <ol>
     * <li>reset all buddy names by calling
     * {@linkplain #resetBuddyNames(AbstractTester)}</li>
     * <li>disconnect from the current session</li>
     * <li>delete the contents of the workspace</li>
     * </ol>
     * 
     * 
     * @throws Exception
     *             if a (internal) failure occur
     */
    public static void tearDownSaros() throws Exception {

        try {
            terminateTestThreads(60 * 1000);
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE,
                "aborting execution of all tests: " + t.getMessage(), t);
            checkAndStopRunningTestThreads(true);
        }

        Exception exception = null;

        for (AbstractTester tester : currentTesters) {
            try {
                tester.superBot().internal().resetSarosVersion();

                if (tester.superBot().views().sarosView().isConnected())
                    tester.superBot().views().sarosView().disconnect();

                tester.superBot().views().sarosView().disconnect();
                tester.remoteBot().resetWorkbench();

                // TODO clear the chat history, should be done via non-gui
                // access
                if (tester.superBot().views().sarosView().hasOpenChatrooms()) {
                    try {
                        tester.superBot().views().sarosView()
                            .closeChatroomWithRegex(".*");
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING,
                            "chatsrooms were closed lately by Saros", e);
                    }
                }

                // Consistency watch dog seems to lock some files from time to
                // time
                int timeout = 0;
                boolean cleared = false;
                do {
                    cleared = tester.superBot().internal().clearWorkspace();
                    if (cleared)
                        break;
                    Thread.sleep(10000);
                } while (++timeout < 6);

                if (!cleared)
                    throw new IOException(
                        "could not clear the workspace of tester: "
                            + tester.toString());
            } catch (Exception e) {
                exception = e;
            }
        }
        currentTesters.clear();

        if (exception != null)
            throw exception;

    }

    /**
     * Brings workbench to a original state before beginning your tests
     * <ul>
     * <li>activate Saros instance workbench</li>
     * <li>close all opened popUp windows</li>
     * <li>close all opened editors</li>
     * <li>deletes all projects</li>
     * <li>close welcome view, if it is open</li>
     * <li>opens the default perspective</li>
     * <li>close all unnecessary views</li>
     * </ul>
     * 
     * @throws Exception
     *             if a (internal) failure occur
     */
    public static void setUpWorkbench() throws Exception {

        Exception exception = null;

        for (AbstractTester tester : currentTesters) {
            try {
                tester.remoteBot().activateWorkbench();
                if (tester.remoteBot().isViewOpen("Welcome"))
                    tester.remoteBot().view("Welcome").close();
                tester.superBot().menuBar().window().openPerspective();
                Util.closeUnnecessaryViews(tester);
                tester.superBot().internal().clearWorkspace();
            } catch (Exception e) {
                exception = e;
            }
        }

        if (exception != null)
            throw exception;
    }

    /**
     * Brings Saros to a original state before beginning your tests
     * <ul>
     * <li>make automaticReminder disable</li>
     * <li>open sarosViews</li>
     * <li>connect</li>
     * <li>check buddy lists, if all active testers are in contact</li>
     * </ul>
     * 
     * @throws Exception
     *             if a (internal) failure occur
     */
    public static void setUpSaros() throws Exception {
        Exception exception = null;

        for (AbstractTester tester : currentTesters) {
            try {
                tester.superBot().menuBar().saros().preferences()
                    .disableAutomaticReminder();
                tester.superBot().menuBar().saros().preferences()
                    .restoreDefaults();
                Util.openSarosView(tester);

                tester
                    .controlBot()
                    .getAccountManipulator()
                    .restoreDefaultAccount(tester.getName(),
                        tester.getPassword(), tester.getDomain());

                tester.superBot().views().sarosView()
                    .connectWith(tester.getJID(), tester.getPassword());
            } catch (Exception e) {
                exception = e;
            }
        }
        for (AbstractTester tester : currentTesters) {
            try {
                resetBuddyNames(tester);
                resetBuddies(tester);
            } catch (Exception e) {
                if (exception != null)
                    exception = e;
            }
        }

        if (exception != null)
            throw exception;
    }

    /**
     * Resets the workbench for every active tester to their original state
     * 
     * @see IRemoteWorkbenchBot#resetWorkbench()
     * @throws Exception
     *             if a (internal) failure occur
     */

    public static void resetWorkbenches() throws Exception {
        for (AbstractTester tester : currentTesters) {
            tester.remoteBot().resetWorkbench();
        }
    }

    /**
     * Closes all editors for all active testers
     * 
     * @see IRemoteWorkbenchBot#closeAllShells()
     * @throws Exception
     *             if a (internal) failure occur
     */
    public static void closeAllEditors() throws Exception {
        for (AbstractTester tester : currentTesters) {
            tester.remoteBot().closeAllEditors();
        }
    }

    /**
     * Closes all shells for all active testers
     * 
     * @see IRemoteWorkbenchBot#closeAllShells()
     * @throws Exception
     *             if a (internal) failure occur
     */

    public static void closeAllShells() throws Exception {
        for (AbstractTester tester : currentTesters) {
            tester.remoteBot().closeAllShells();
        }
    }

    /**
     * Resets the account of all testers.
     * <ul>
     * <li>Add the account of the tester to the account store</li>
     * <li>Activate the account of the tester</li>
     * <li>Deletes all non active accounts</li>
     * </ul>
     * 
     * @throws Exception
     *             if a (internal) failure occur
     */
    public static void resetDefaultAccount() throws Exception {
        for (AbstractTester tester : currentTesters)
            tester
                .controlBot()
                .getAccountManipulator()
                .restoreDefaultAccount(tester.getName(), tester.getPassword(),
                    tester.getDomain());
    }

    /**
     * Resets the buddy names for all active testers to their original by
     * sequentially calling {@link #resetBuddyNames(AbstractTester)}
     * 
     * @throws IllegalStateException
     *             if one of the current testers is not connected
     * @throws Exception
     *             for any other (internal) failure
     */

    public static void resetBuddyNames() throws Exception {
        for (AbstractTester tester : currentTesters)
            resetBuddyNames(tester);
    }

    /**
     * Resets the names of all buddies for the tester to their original states
     * as defined in the configuration file
     * 
     * @param tester
     *            the tester
     * @throws IllegalStateException
     *             if the tester is not connected
     * @throws Exception
     *             for any other (internal) failure
     */

    public static void resetBuddyNames(AbstractTester tester) throws Exception {

        if (!tester.superBot().views().sarosView().isConnected())
            throw new IllegalStateException(tester + " is not connected");

        for (int i = 0; i < currentTesters.size(); i++) {
            if (tester == currentTesters.get(i))
                continue;

            if (tester.superBot().views().sarosView()
                .hasBuddy(currentTesters.get(i).getJID())) {
                tester.superBot().views().sarosView()
                    .selectBuddy(currentTesters.get(i).getJID())
                    .rename(currentTesters.get(i).getBaseJid());
            }
        }
    }

    /**
     * Resets the buddies for all active testers to their original by
     * sequentially calling {@link #resetBuddies(AbstractTester)}
     * 
     * @throws IllegalStateException
     *             if one of the current testers is not connected
     * @throws Exception
     *             for any other (internal) failure
     * */
    public static void resetBuddies() throws Exception {
        for (AbstractTester tester : currentTesters)
            resetBuddies(tester);
    }

    /**
     * Resets the buddies of the tester to their original states as defined in
     * the configuration file. E.g if the test case included ALICE, BOB and
     * CARL, and ALICE removed BOB from her roster then after the method returns
     * ALICE will have BOB again in her roster and also BOB will have ALICE back
     * in his roster.
     * 
     * @param tester
     *            the tester
     * @throws IllegalStateException
     *             if the tester or one of its buddies is not connected
     * @throws Exception
     *             for any other (internal) failure
     */

    public static void resetBuddies(AbstractTester tester) throws Exception {
        for (int i = 0; i < currentTesters.size(); i++) {
            if (tester == currentTesters.get(i))
                continue;
            Util.addBuddiesToContactList(tester, currentTesters.get(i));
        }
    }

    /**
     * Deletes all projects from all active testers by sequentially removing all
     * projects from the workspace.
     * 
     * @NOTE this method does not invoke any GUI action, instead it uses the
     *       Eclipse API. Unexpected results can occur when there are still open
     *       unsaved editor windows before this method is called.
     * @return <code>true</code> if all workspaces could be cleared,
     *         <code>false</code> otherwise
     * @throws Exception
     *             if a (internal) failure occurs
     */
    public static boolean clearWorkspaces() throws Exception {
        boolean cleared = true;
        for (AbstractTester tester : currentTesters) {
            cleared &= tester.superBot().internal().clearWorkspace();
        }
        return cleared;
    }

    /**
     * Disconnects all active testers in the order the were initialized by
     * {@link #initTesters(AbstractTester tester, AbstractTester... testers)}
     * 
     * @throws Exception
     *             if a (internal) failure occurs
     */
    public static void disconnectAllActiveTesters() throws Exception {
        for (AbstractTester tester : currentTesters) {
            if (tester != null) {
                tester.superBot().views().sarosView().disconnect();
            }
        }
    }

    /**
     * Stops the current session for all participants. The host is leaving the
     * session first.
     * 
     * 
     * @param host
     *            the host of the current session
     * @throws IllegalStateException
     *             if the host is not host of the current session
     * @throws Exception
     *             if a (internal) failure occurs
     */
    public static void leaveSessionHostFirst(AbstractTester host)
        throws Exception {

        if (!host.superBot().views().sarosView().isHost())
            throw new IllegalStateException(host
                + " is not host of the current session");

        host.superBot().views().sarosView().leaveSession();
        for (final AbstractTester tester : currentTesters) {
            if (tester != host) {
                tester.superBot().views().sarosView().waitUntilIsNotInSession();
            }
        }
    }

    /**
     * Define the leave session with the following steps.
     * <ol>
     * <li>All invitees leave the session first.(concurrently)</li>
     * <li>Host waits until all invitees are no longer in the session.</li>
     * <li>Host leaves the session</li>
     * </ol>
     * 
     * @param host
     *            the host of the current session
     * @throws IllegalStateException
     *             if the host is not host of the current session
     * @throws Exception
     *             if a (internal) failure occurs
     */

    public static void leaveSessionPeersFirst(AbstractTester host)
        throws Exception {

        if (!host.superBot().views().sarosView().isHost())
            throw new IllegalStateException(host
                + " is not host of the current session");

        List<JID> peerJIDs = new ArrayList<JID>();
        List<Callable<Void>> leaveTasks = new ArrayList<Callable<Void>>();
        for (final AbstractTester tester : currentTesters) {

            if (tester != host) {
                peerJIDs.add(tester.getJID());
                leaveTasks.add(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        tester.superBot().views().sarosView().leaveSession();
                        return null;
                    }
                });
            }
        }

        Util.workAll(leaveTasks);

        host.superBot().views().sarosView()
            .waitUntilAllPeersLeaveSession(peerJIDs);

        host.superBot().views().sarosView().leaveSession();
        host.superBot().views().sarosView().waitUntilIsNotInSession();
    }

    /**
     * Creates a new {@link TestThread}. The test thread can be terminated by
     * calling {@link #terminateTestThreads}. The test thread is automatically
     * terminated after the <b>last</b> test of the test class is executed.
     * 
     * @param runnable
     *            the runnable that should be executed in this test thread
     * @return a new, not yet started, test thread
     */
    public static TestThread createTestThread(TestThread.Runnable runnable) {
        TestThread thread = new TestThread(runnable);
        currentTestThreads.add(thread);
        return thread;
    }

    /**
     * Terminates all test threads that were created with
     * {@link #createTestThread}.
     * 
     * @param timeout
     *            timeout in milliseconds to wait for the termination of all
     *            test threads
     * @throws IllegalStateException
     *             if there are still test threads running
     */
    public static void terminateTestThreads(long timeout) {
        for (TestThread thread : currentTestThreads)
            thread.interrupt();

        if (Util
            .joinAll(timeout, currentTestThreads.toArray(new TestThread[0]))) {
            currentTestThreads.clear();
            return;
        }

        for (Iterator<TestThread> it = currentTestThreads.iterator(); it
            .hasNext();) {

            TestThread thread = it.next();

            if (!thread.isAlive())
                it.remove();
        }

        if (!currentTestThreads.isEmpty())
            throw new IllegalStateException(currentTestThreads.size()
                + " test threads are still alive");
    }

    @SuppressWarnings("deprecation")
    private static void checkAndStopRunningTestThreads(boolean abortAllTests) {

        if (currentTestThreads.isEmpty() && !StfTestCase.abortAllTests)
            return;

        assert lastTestClass != null;

        IllegalStateException exception = new IllegalStateException(
            "could not terminate all test threads for test class "
                + lastTestClass.getName());

        if (!abortAllTests)
            throw exception;

        // do it the hard way and give up

        StfTestCase.abortAllTests = true;

        for (TestThread thread : currentTestThreads)
            thread.stop();

        currentTestThreads.clear();

        throw exception;
    }

}

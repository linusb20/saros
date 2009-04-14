package de.fu_berlin.inf.dpp.project;

import de.fu_berlin.inf.dpp.activities.IActivity;

/**
 * Every activity provider is responsible for one or more activity types. It
 * handles creating (fromXML) and executing (exec) the activities of some type
 * and notifies listeners.
 * 
 * @author rdjemili
 */
public interface IActivityProvider {

    public void exec(IActivity activity);

    public void addActivityListener(IActivityListener listener);

    public void removeActivityListener(IActivityListener listener);
}

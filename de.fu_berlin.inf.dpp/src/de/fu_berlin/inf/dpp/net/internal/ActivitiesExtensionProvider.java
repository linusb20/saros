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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jivesoftware.smack.provider.PacketExtensionProvider;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import de.fu_berlin.inf.dpp.activities.AbstractActivity;
import de.fu_berlin.inf.dpp.net.TimedActivity;

public class ActivitiesExtensionProvider implements PacketExtensionProvider {

    public ActivitiesPacketExtension parseExtension(XmlPullParser parser)
        throws XmlPullParserException, IOException {

        List<TimedActivity> timedActivities = new ArrayList<TimedActivity>();
        String sessionID = null;
        /*
         * There is only one sequence number in the message, so all activities
         * get increasing numbers based on that sequence number.
         */
        int sequenceNumber = TimedActivity.UNKNOWN_SEQUENCE_NR;
        boolean done = false;
        while (!done) {
            int eventType = parser.next();
            if (eventType == XmlPullParser.START_TAG) {
                if (parser.getName().equals(
                    ActivitiesPacketExtension.SESSION_ID)) {
                    sessionID = parseSessionId(parser);
                } else if (parser.getName().equals("timestamp")) {
                    sequenceNumber = parseSequenceNumber(parser);
                } else {
                    timedActivities.add(new TimedActivity(AbstractActivity
                        .parse(parser), sequenceNumber++));
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals("activities")) {
                    done = true;
                }
            }
        }

        return new ActivitiesPacketExtension(sessionID, timedActivities);
    }

    private String parseSessionId(XmlPullParser parser)
        throws XmlPullParserException, IOException {

        parser.next(); // read text
        String sessionID = parser.getText();
        parser.next(); // read end tag

        return sessionID;
    }

    private int parseSequenceNumber(XmlPullParser parser)
        throws XmlPullParserException, IOException {

        parser.next(); // read text
        int result = Integer.parseInt(parser.getText());
        parser.next(); // read end tag

        return result;
    }
}

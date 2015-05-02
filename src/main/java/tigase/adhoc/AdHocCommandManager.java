/*
 * AdHocCommandManager.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2012 "Artur Hefczyc" <artur.hefczyc@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package tigase.adhoc;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import tigase.adhoc.AdHocResponse.State;
import tigase.server.Packet;
import tigase.util.SimpleCache;
import tigase.xml.Element;
import tigase.xmpp.JID;

/**
 * Class description
 * 
 * 
 */
public class AdHocCommandManager {
	private final Map<String, AdHocCommand> commands = new HashMap<String, AdHocCommand>();
	private final SimpleCache<String, AdHocSession> sessions = new SimpleCache<String, AdHocSession>(100, 10 * 1000);

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	public Collection<AdHocCommand> getAllCommands() {
		return this.commands.values();
	}

	/**
	 * Method checks if exists implementation for this command in this
	 * CommandManager
	 * 
	 * @param node
	 * @return true - if command exists for this node
	 */
	public boolean hasCommand(String node) {
		return this.commands.containsKey(node);
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param element
	 * 
	 * @return
	 * 
	 * @throws AdHocCommandException
	 */
	public Packet process(Packet packet) throws AdHocCommandException {
		final Element element = packet.getElement();
		final JID senderJid = packet.getStanzaFrom();
		final Element command = element.getChild("command", "http://jabber.org/protocol/commands");
		final String node = command.getAttributeStaticStr("node");
		final String action = command.getAttributeStaticStr("action");
		final String sessionId = command.getAttributeStaticStr("sessionid");
		AdHocCommand adHocCommand = this.commands.get(node);

		if (adHocCommand == null) {
		} else {
			State currentState = null;
			final AdhHocRequest request = new AdhHocRequest(packet, command, node, senderJid, action, sessionId);
			final AdHocResponse response = new AdHocResponse(sessionId, currentState);
			final AdHocSession session = (sessionId == null) ? new AdHocSession() : this.sessions.get(sessionId);

			adHocCommand.execute(request, response);

			Element commandResult = new Element("command", new String[] { "xmlns", "node", }, new String[] {
					"http://jabber.org/protocol/commands", node });

			commandResult.addAttribute("status", response.getNewState().name());
			if ((response.getCurrentState() == null) && (response.getNewState() == State.executing)) {
				this.sessions.put(response.getSessionid(), session);
			} else if ((response.getSessionid() != null)
					&& ((response.getNewState() == State.canceled) || (response.getNewState() == State.completed))) {
				this.sessions.remove(response.getSessionid());
			}
			if (response.getSessionid() != null) {
				commandResult.addAttribute("sessionid", response.getSessionid());
			}
			for (Element r : response.getElements()) {
				commandResult.addChild(r);
			}
			final Packet okResult = packet.okResult(commandResult, 0);
			okResult.getElement().setXMLNS( Packet.CLIENT_XMLNS);

			return okResult;
		}

		return null;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param command
	 */
	public void registerCommand(AdHocCommand command) {
		this.commands.put(command.getNode(), command);
	}
}

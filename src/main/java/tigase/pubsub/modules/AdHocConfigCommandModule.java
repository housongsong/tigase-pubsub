/*
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2007 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
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
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.pubsub.modules;

import java.util.ArrayList;
import java.util.List;

import tigase.adhoc.AdHocCommand;
import tigase.adhoc.AdHocCommandException;
import tigase.adhoc.AdHocCommandManager;
import tigase.adhoc.AdHocScriptCommandManager;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractModule;
import tigase.pubsub.ElementWriter;
import tigase.pubsub.PacketWriter;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.server.Packet;
import tigase.util.JIDUtils;
import tigase.xml.Element;

public class AdHocConfigCommandModule extends AbstractModule {

	private static final Criteria CRIT = ElementCriteria.nameType("iq", "set").add(
			ElementCriteria.name("command", "http://jabber.org/protocol/commands"));

	private static final String[] COMMAND_PATH = { "iq", "command" };
	
	private final AdHocCommandManager commandsManager = new AdHocCommandManager();
	private AdHocScriptCommandManager scriptCommandManager;

	public AdHocConfigCommandModule(PubSubConfig config, IPubSubRepository pubsubRepository, AdHocScriptCommandManager scriptCommandManager) {
		super(config, pubsubRepository);
		this.scriptCommandManager = scriptCommandManager;
	}

	public List<Element> getCommandListItems(final String senderJid, final String toJid) {
		ArrayList<Element> commandsList = new ArrayList<Element>();
		for (AdHocCommand command : this.commandsManager.getAllCommands()) {
			if (config.isAdmin(JIDUtils.getNodeID(senderJid))) {
				commandsList.add(new Element("item", new String[] { "jid", "node", "name" }, new String[] { toJid,
						command.getNode(), command.getName() }));
			}
		}
		
		List<Element> scriptCommandsList = scriptCommandManager.getCommandListItems(senderJid, toJid);
		if (scriptCommandsList != null) {
			commandsList.addAll(scriptCommandsList);
		}
		return commandsList;
	}
	
	@Override
	public String[] getFeatures() {
		return new String[] { "http://jabber.org/protocol/commands" };
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	public List<Packet> process(Packet packet, PacketWriter packetWriter) throws PubSubException {
		String node = packet.getAttributeStaticStr(COMMAND_PATH, "node");
		if (commandsManager.hasCommand(node)) {
			try {
				List<Packet> result = makeArray(this.commandsManager.process(packet));
				return result;
			} catch (AdHocCommandException e) {
				throw new PubSubException(e.getErrorCondition(), e.getMessage());
			}
		}
		else {
			return scriptCommandManager.process(packet);
		}
	}

	public void register(AdHocCommand command) {
		this.commandsManager.registerCommand(command);
	}

}

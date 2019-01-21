/**
 * Tigase PubSub - Publish Subscribe component for Tigase
 * Copyright (C) 2008 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.pubsub.modules.ext.presence;

import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

import tigase.component2.PacketWriter;
import tigase.component2.exceptions.ComponentException;
import tigase.criteria.Criteria;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.modules.PublishItemModule;
import tigase.pubsub.modules.ext.presence.PresencePerNodeExtension.LoginToNodeHandler;
import tigase.pubsub.modules.ext.presence.PresencePerNodeExtension.LogoffFromNodeHandler;
import tigase.pubsub.modules.ext.presence.PresencePerNodeExtension.UpdatePresenceHandler;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.RepositoryException;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.StanzaType;

public class PresenceNotifierModule extends AbstractPubSubModule {

	private final PresencePerNodeExtension presencePerNodeExtension;

	private final PublishItemModule publishItemModule;

	public PresenceNotifierModule(PubSubConfig config, PacketWriter packetWriter, PublishItemModule publishItemModule) {
		super(config, packetWriter);
		this.presencePerNodeExtension = new PresencePerNodeExtension(config, packetWriter);
		this.publishItemModule = publishItemModule;

		this.presencePerNodeExtension.addLoginToNodeHandler(new LoginToNodeHandler() {

			@Override
			public void onLoginToNode(BareJID serviceJID, String node, JID occupantJID, Packet presenceStanza) {
				PresenceNotifierModule.this.onLoginToNode(serviceJID, node, occupantJID, presenceStanza);
			}
		});
		this.presencePerNodeExtension.addLogoffFromNodeHandler(new LogoffFromNodeHandler() {

			@Override
			public void onLogoffFromNode(BareJID serviceJID, String node, JID occupantJID, Packet presenceStanza) {
				PresenceNotifierModule.this.onLogoffFromNode(serviceJID, node, occupantJID, presenceStanza);
			}

		});
		this.presencePerNodeExtension.addUpdatePresenceHandler(new UpdatePresenceHandler() {

			@Override
			public void onPresenceUpdate(BareJID serviceJID, String node, JID occupantJID, Packet presenceStanza) {
				PresenceNotifierModule.this.onPresenceUpdate(serviceJID, node, occupantJID, presenceStanza);
			}
		});
	}

	protected Element createPresenceNotificationItem(BareJID serviceJID, String node, JID occupantJID, Packet presenceStanza) {
		Element notification = new Element("presence");
		notification.setAttribute("xmlns", PresencePerNodeExtension.XMLNS_EXTENSION);
		notification.setAttribute("node", node);
		notification.setAttribute("jid", occupantJID.toString());

		if (presenceStanza == null || presenceStanza.getType() == StanzaType.unavailable) {
			notification.setAttribute("type", "unavailable");
		} else if (presenceStanza.getType() == StanzaType.available) {
			notification.setAttribute("type", "available");
		}

		return notification;
	}

	@Override
	public String[] getFeatures() {
		return new String[] { PresencePerNodeExtension.XMLNS_EXTENSION };
	}

	@Override
	public Criteria getModuleCriteria() {
		return null;
	}

	public PresencePerNodeExtension getPresencePerNodeExtension() {
		return presencePerNodeExtension;
	}

	protected void onLoginToNode(BareJID serviceJID, String node, JID occupantJID, Packet presenceStanza) {
		try {
			Element notification = createPresenceNotificationItem(serviceJID, node, occupantJID, presenceStanza);
			// publish new occupant presence to all occupants
			publish(serviceJID, node, notification);

			// publish presence of all occupants to new occupant
			publishToOne(serviceJID, node, occupantJID);

		} catch (Exception e) {
			log.log(Level.WARNING, "Problem on sending LoginToNodeEvent", e);
		}
	}

	protected void onLogoffFromNode(BareJID serviceJID, String node, JID occupantJID, Packet presenceStanza) {
		try {
			Element notification = createPresenceNotificationItem(serviceJID, node, occupantJID, presenceStanza);

			publish(serviceJID, node, notification);
		} catch (Exception e) {
			log.log(Level.WARNING, "Problem on sending LogoffFromNodeEvent", e);
		}
	}

	protected void onPresenceUpdate(BareJID serviceJID, String node, JID occupantJID, Packet presenceStanza) {
	}

	@Override
	public void process(Packet packet) throws ComponentException, TigaseStringprepException {
	}

	protected void publish(BareJID serviceJID, String nodeName, Element itemToSend) throws RepositoryException {
		AbstractNodeConfig nodeConfig = getRepository().getNodeConfig(serviceJID, nodeName);
		final IAffiliations nodeAffiliations = getRepository().getNodeAffiliations(serviceJID, nodeName);
		final ISubscriptions nodeSubscriptions = getRepository().getNodeSubscriptions(serviceJID, nodeName);

		Element items = new Element("items");
		items.addAttribute("node", nodeName);

		Element item = new Element("item");
		items.addChild(item);
		item.addChild(itemToSend);
		
		publishItemModule.sendNotifications(items, JID.jidInstance(serviceJID), nodeName,
				nodeConfig, nodeAffiliations, nodeSubscriptions);
	}

	protected void publishToOne(BareJID serviceJID, String nodeName, JID destinationJID) throws RepositoryException {
		AbstractNodeConfig nodeConfig = getRepository().getNodeConfig(serviceJID, nodeName);

		Collection<JID> occupants = presencePerNodeExtension.getNodeOccupants(serviceJID, nodeName);
		for (JID jid : occupants) {

			if (jid.equals(destinationJID))
				continue;

			Packet p = presencePerNodeExtension.getPresence(serviceJID, nodeName, jid);
			if (p == null)
				continue;

			Element items = new Element("items");
			items.addAttribute("node", nodeName);
			Element item = new Element("item");
			items.addChild(item);
			item.addChild(createPresenceNotificationItem(serviceJID, nodeName, jid, p));

			publishItemModule.sendNotifications(new JID[] { destinationJID }, items,
					JID.jidInstance(serviceJID), nodeConfig, nodeName, null);
		}
	}

}

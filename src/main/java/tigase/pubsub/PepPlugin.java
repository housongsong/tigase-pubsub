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
package tigase.pubsub;

import tigase.db.NonAuthUserRepository;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.kernel.beans.config.ConfigField;
import tigase.server.Iq;
import tigase.server.Packet;
import tigase.server.Presence;
import tigase.server.xmppsession.SessionManager;
import tigase.util.dns.DNSResolverFactory;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.*;
import tigase.xmpp.impl.PresenceAbstract;
import tigase.xmpp.impl.ServiceDiscovery;
import tigase.xmpp.impl.VCardTemp;
import tigase.xmpp.jid.JID;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Implements PubSub support for every local user account on it's bare jid using local version of PubSub component.
 *
 * @author andrzej
 */
@Bean(name = "pep", parent = SessionManager.class, active = true)
public class PepPlugin
		extends XMPPProcessor
		implements XMPPProcessorIfc, XMPPStopListenerIfc, ServiceDiscovery.AccountServiceProvider {

	protected static final String PUBSUB_XMLNS = "http://jabber.org/protocol/pubsub";
	protected static final String PUBSUB_XMLNS_OWNER = PUBSUB_XMLNS + "#owner";
	protected static final Element[] DISCO_FEATURES = {
			new Element("feature", new String[]{"var"}, new String[]{PUBSUB_XMLNS}),
			new Element("feature", new String[]{"var"}, new String[]{PUBSUB_XMLNS + "#owner"}),
			new Element("feature", new String[]{"var"}, new String[]{PUBSUB_XMLNS + "#publish"}),
			new Element("identity", new String[]{"category", "type"}, new String[]{"pubsub", "pep"}),};
	protected static final Element[] DISCO_FEATURES_WITH_XEP0398 = Stream.concat(Arrays.stream(DISCO_FEATURES),
																				 Stream.of(new Element("feature",
																									   new String[]{
																											   "var"},
																									   new String[]{
																											   "urn:xmpp:pep-vcard-conversion:0"})))
																						 .toArray(Element[]::new);
	protected static final String DISCO_INFO_XMLNS = "http://jabber.org/protocol/disco#info";
	protected static final String DISCO_ITEMS_XMLNS = "http://jabber.org/protocol/disco#items";
	protected static final String[][] ELEMENTS = {Iq.IQ_PUBSUB_PATH, Iq.IQ_PUBSUB_PATH,
												  new String[]{Presence.ELEM_NAME} };
	protected static final String[] XMLNSS = {PUBSUB_XMLNS_OWNER, PUBSUB_XMLNS, Presence.CLIENT_XMLNS};
	private static final String CAPS_XMLNS = "http://jabber.org/protocol/caps";
	private static final String ID = "pep";
	private static final Logger log = Logger.getLogger(PepPlugin.class.getCanonicalName());
	private static final String[] PRESENCE_C_PATH = {Presence.ELEM_NAME, "c"};
	private static final Set<StanzaType> TYPES = new HashSet<StanzaType>(Arrays.asList(
			// stanza types for presences
			null, StanzaType.available, StanzaType.unavailable,
			// stanza types for iq
			StanzaType.get, StanzaType.set, StanzaType.result, StanzaType.error));
	protected final Set<String> simpleNodes = new HashSet<String>(
			Arrays.asList("http://jabber.org/protocol/tune", "http://jabber.org/protocol/mood",
						  "http://jabber.org/protocol/activity", "http://jabber.org/protocol/geoloc",
						  "urn:xmpp:avatar:data", "urn:xmpp:avatar:metadata"));
	@ConfigField(desc = "PubSub Component JID", alias = "pubsub-jid")
	protected JID pubsubJid = JID.jidInstanceNS("pubsub", DNSResolverFactory.getInstance().getDefaultHost(), null);
	@ConfigField(desc = "Enable simple PEP", alias = "simple-pep-enabled")
	protected boolean simplePepEnabled = false;
	@Inject(nullAllowed = true)
	private VCardTemp vcardTempProcessor;
	
	@Override
	public String id() {
		return ID;
	}

	public int concurrentQueuesNo() {
		return super.concurrentQueuesNo() * 2;
	}

	@Override
	public void process(Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo,
						Queue<Packet> results, Map<String, Object> settings) throws XMPPException {
		switch (packet.getElemName()) {
			case Iq.ELEM_NAME:
				processIq(packet, session, results);
				break;
			case Presence.ELEM_NAME:
				processPresence(packet, session, results);
				break;
		}
	}

	@Override
	public Element[] supDiscoFeatures(final XMPPResourceConnection session) {
		if (vcardTempProcessor ==  null) {
			return DISCO_FEATURES;
		} else {
			return DISCO_FEATURES_WITH_XEP0398;
		}
	}

	@Override
	public String[][] supElementNamePaths() {
		return ELEMENTS;
	}

	@Override
	public String[] supNamespaces() {
		return XMLNSS;
	}

	@Override
	public Set<StanzaType> supTypes() {
		return TYPES;
	}

	@Override
	public void stopped(XMPPResourceConnection session, Queue<Packet> results, Map<String, Object> settings) {

		synchronized (session) {
			try {
				Packet packet = Presence.packetInstance(PresenceAbstract.PRESENCE_ELEMENT_NAME,
														session.getJID().toString(),
														session.getJID().copyWithoutResource().toString(),
														StanzaType.unavailable);
				processPresence(packet, session, results);

			} catch (NotAuthorizedException | TigaseStringprepException e) {
				if (log.isLoggable(Level.FINER)) {
					log.log(Level.FINER, "Problem forwarding unavailable presence to PubSub component");
				}
			}
		}
	}

	@Override
	public JID getServiceProviderComponentJid() {
		return pubsubJid;
	}

	protected JID getPubsubJid(XMPPResourceConnection session, JID serviceJid) {
		return pubsubJid;
	}

	protected void processIq(Packet packet, XMPPResourceConnection session, Queue<Packet> results)
			throws XMPPException {
		if (vcardTempProcessor != null && packet.getStanzaFrom() != null && packet.getStanzaTo() != null && packet.getType() == StanzaType.result &&
				packet.getStanzaTo().getBareJID().equals(packet.getStanzaFrom().getBareJID()) &&
				packet.getAttributeStaticStr("id") != null && packet.getAttributeStaticStr("id").startsWith("sm-query-vcard-pep-")) {
			vcardTempProcessor.pepToVCardTemp_onDataRetrieved(packet, session);
			return;
		}

		if (session != null && session.isServerSession()) {
			return;
		}

		Element pubsubEl = packet.getElement().findChildStaticStr(Iq.IQ_PUBSUB_PATH);
		if (pubsubEl != null && simplePepEnabled) {
			boolean simple = pubsubEl.findChild(c -> simpleNodes.contains(c.getAttributeStaticStr("node"))) != null;
			if (simple) {
				// if simple and simple support is enabled we are leaving
				// support
				// for this node to default pep plugin (not presistent pep)
				return;
			}
		}

		// forwarding packet to particular resource
		if (packet.getStanzaTo() != null && packet.getStanzaTo().getResource() != null) {
			if (pubsubEl != null &&
					(pubsubEl.getXMLNS() == PUBSUB_XMLNS || pubsubEl.getXMLNS() == PUBSUB_XMLNS_OWNER)) {
				Packet result = null;
				if (session != null) {
					XMPPResourceConnection con = session.getParentSession()
							.getResourceForResource(packet.getStanzaTo().getResource());

					if (con != null) {
						result = packet.copyElementOnly();
						result.setPacketTo(con.getConnectionId());

						// In most cases this might be skept, however if there
						// is a
						// problem during packet delivery an error might be sent
						// back
						result.setPacketFrom(packet.getTo());
					}
				}
				// if result was not generated yet, this means that session is
				// null or
				// connection is null, so recipient is unavailable
				// in theory we could skip generation of error for performance
				// reason
				// as sending iq/error for iq/result will make no difference for
				// component
				// but for now let's send response to be compatible with
				// specification
				if (result == null) {
					result = Authorization.RECIPIENT_UNAVAILABLE.getResponseMessage(packet,
																					"The recipient is no longer available.",
																					true);
				}
				results.offer(result);
			}
			return;
		}

		// if packet is not for this session then we need to forward it
		if (session != null && packet.getStanzaTo() != null && !session.isUserId(packet.getStanzaTo().getBareJID())) {
			results.offer(packet.copyElementOnly());
			return;
		}

		if (packet.getStanzaTo() == null) {
			// we should not forward disco#info or disco#items with no "to" set
			// as they
			// need to be processed only by server
			if (pubsubEl == null ||
					(pubsubEl.getXMLNS() != PUBSUB_XMLNS && pubsubEl.getXMLNS() != PUBSUB_XMLNS_OWNER)) {
				// ignoring - disco#info or disco#items to server
				log.log(Level.FINEST, "got <iq/> packet with no 'to' attribute = {0}", packet);
				return;
			}
		} else if (packet.getStanzaTo().getResource() == null && packet.getType() == StanzaType.error &&
				packet.getType() == StanzaType.result) {
			// we are dropping packet of type error or result if they are sent
			// in from user
			return;
		}

		// this packet is to local user (offline or not) - forwarding to PubSub
		// component
		Packet result = packet.copyElementOnly();
		if (packet.getStanzaTo() == null && session != null) {
			// in case if packet is from local user without from/to
			JID userJid = JID.jidInstance(session.getBareJID());
			result.initVars(packet.getStanzaFrom() != null ? packet.getStanzaFrom() : session.getJID(), userJid);
		}
		if (packet.getStanzaFrom() == null) {
			// at this point we should already have "from" attribute set
			// if we do not have it, then we should drop this packet
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST,
						"received <iq/> packet to forward to PubSub component without 'from' attribute, dropping packet = {0}",
						packet);
			}
			return;
		}
		result.setPacketFrom(packet.getFrom());
		result.setPacketTo(getPubsubJid(session, packet.getStanzaTo()));

		results.offer(result);

		if (vcardTempProcessor != null && pubsubEl != null && packet.getType() == StanzaType.set && session != null &&
				packet.getStanzaFrom() != null && session.isUserId(packet.getStanzaFrom().getBareJID())) {
			Element publishEl = pubsubEl.getChild("publish");
//			if (publishEl != null) {
//				pepListeners.forEach(listener -> {
//					listener.itemSentForPublication(packet.getStanzaFrom().getBareJID(), publishEl, () -> getPubsubJid(session, packet.getStanzaFrom()), results::offer);
//				});
//			}
			if (publishEl != null && "urn:xmpp:avatar:metadata".equals(publishEl.getAttributeStaticStr("node"))) {
				String itemId = publishEl.getChildAttributeStaticStr("item", "id");
				if (itemId != null) {
					Element infoEl = publishEl.findChildStaticStr(new String[] { "publish", "item", "metadata", "info" });
					if (infoEl != null && infoEl.getAttributeStaticStr("url") == null) {
						String mimeType = Optional.ofNullable(infoEl.getAttributeStaticStr("type")).orElse("image/png");
						vcardTempProcessor.pepToVCardTemp_onPublication(packet.getStanzaFrom().getBareJID(), session, itemId, mimeType, () -> getPubsubJid(session, packet.getStanzaFrom()), results::offer);
					}
				}
			}
		}
	}

	protected void processPresence(Packet packet, XMPPResourceConnection session, Queue<Packet> results)
			throws NotAuthorizedException {
		boolean forward = false;
		if (packet.getType() == null || packet.getType() == StanzaType.available) {
			// forward only available packets with CAPS as without there is no point in doing this
			forward = packet.getElement().getXMLNSStaticStr(PRESENCE_C_PATH) == CAPS_XMLNS;
		} else if (packet.getType() == StanzaType.unavailable) {
			forward = true;
		}

		// is there a point in forwarding <presence/> of type error? we should forward only online/offline
		if (!forward) {
			return;
		}

		// if presence is to local user then forward it to PubSub component
		if ((packet.getStanzaTo() == null && session != null && session.isAuthorized()) ||
				(packet.getStanzaTo() != null && packet.getStanzaTo().getResource() == null &&
						(session == null || !session.isAuthorized() ||
								session.isUserId(packet.getStanzaTo().getBareJID())))) {

			Packet result = packet.copyElementOnly();
			if (packet.getStanzaTo() == null || packet.getStanzaFrom() == null) {
				if (session == null ||
						(packet.getStanzaTo() != null && session.isUserId(packet.getStanzaTo().getBareJID()))) {
					return;
				}
				// in case if packet is from local user without from/to
				JID userJid = JID.jidInstance(session.getBareJID());
				result.initVars(session.getJID(), userJid);
			}
			result.setPacketTo(getPubsubJid(session, packet.getStanzaTo()));
			results.offer(result);
		}
	}
}

/*
 * AbstractModule.java
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

package tigase.pubsub;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.component.PacketWriter;
import tigase.component.exceptions.RepositoryException;
import tigase.component.modules.Module;
import tigase.kernel.beans.Inject;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.server.Packet;
import tigase.stats.StatisticHolderImpl;
import tigase.util.JIDUtils;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.impl.roster.RosterAbstract.SubscriptionType;
import tigase.xmpp.impl.roster.RosterElement;

/**
 * Class description
 *
 *
 * @version 5.0.0, 2010.03.27 at 05:24:03 GMT
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 */
public abstract class AbstractPubSubModule extends StatisticHolderImpl implements Module {

	/** Field description */
	protected final static Logger log = Logger.getLogger(AbstractPubSubModule.class.getName());

	/**
	 * Method description
	 *
	 *
	 * @param iq
	 *
	 * @return
	 */
	public static Element createResultIQ(Element iq) {
		Element e = new Element("iq");
		e.setXMLNS(Packet.CLIENT_XMLNS);
		String id = iq.getAttributeStaticStr("id");
		String from = iq.getAttributeStaticStr("from");
		String to = iq.getAttributeStaticStr("to");

		e.addAttribute("type", "result");
		if (to != null) {
			e.addAttribute("from", to);
		}
		if (from != null) {
			e.addAttribute("to", from);
		}
		if (id != null) {
			e.addAttribute("id", id);
		}

		return e;
	}

	/**
	 * Method description
	 *
	 *
	 * @param iq
	 *
	 * @return
	 */
	public static List<Element> createResultIQArray(Element iq) {
		return makeArray(createResultIQ(iq));
	}

	/**
	 * Method description
	 *
	 *
	 * @param allSubscribers
	 * @param jid
	 *
	 * @return
	 */
	@Deprecated
	protected static String findBestJid(final String[] allSubscribers, final String jid) {
		final String bareJid = JIDUtils.getNodeID(jid);
		String best = null;

		for (String j : allSubscribers) {
			if (j.equals(jid)) {
				return j;
			} else {
				if (bareJid.equals(j)) {
					best = j;
				}
			}
		}

		return best;
	}

	/**
	 * Method description
	 *
	 *
	 * @param nodeConfig
	 * @param jids
	 * @param affiliations
	 * @param subscriptions
	 *
	 * @return
	 */
	public static Collection<BareJID> getActiveSubscribers(final AbstractNodeConfig nodeConfig, final BareJID[] jids,
			final IAffiliations affiliations, final ISubscriptions subscriptions) {
		Set<BareJID> result = new HashSet<BareJID>();
		final boolean presenceExpired = nodeConfig.isPresenceExpired();

		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "getActiveSubscribers[2,1] subscriptions: {0}, jids: {1}, presenceExpired: {2}",
					new Object[] { subscriptions, Arrays.asList(jids), presenceExpired });
		}

		if (jids != null) {
			for (BareJID jid : jids) {
				if (presenceExpired) {
				}

				UsersAffiliation affiliation = affiliations.getSubscriberAffiliation(jid);

				if (log.isLoggable(Level.FINEST)) {
					log.log(Level.FINEST, "getActiveSubscribers[2,2] jid: {0}, affiliation: {1}",
							new Object[] { jid, affiliation });
				}

				// /* && affiliation.getAffiliation() != Affiliation.none */
				if (affiliation.getAffiliation() != Affiliation.outcast) {
					Subscription subscription = subscriptions.getSubscription(jid);
					if (log.isLoggable(Level.FINEST)) {
						log.log(Level.FINEST, "getActiveSubscribers[2,2] jid: {0}, subscription: {1}}",
								new Object[] { jid, subscription });
					}

					if (subscription == Subscription.subscribed) {
						result.add(jid);
					}
				}
			}
		}

		return result;
	}

	/**
	 * Method description
	 *
	 *
	 * @param nodeConfig
	 * @param affiliations
	 * @param subscriptions
	 *
	 * @return
	 *
	 * @throws RepositoryException
	 */
	public static Collection<BareJID> getActiveSubscribers(final AbstractNodeConfig nodeConfig,
			final IAffiliations affiliations, final ISubscriptions subscriptions) throws RepositoryException {
		UsersSubscription[] subscribers = subscriptions.getSubscriptionsForPublish();

		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "getActiveSubscribers[1] subscriptions: {0}, subscribers: {1}",
					new Object[] { subscriptions, Arrays.asList(subscribers) });
		}

		if (subscribers == null) {
			return Collections.emptyList();
		}

		BareJID[] jids = new BareJID[subscribers.length];

		for (int i = 0; i < subscribers.length; i++) {
			jids[i] = subscribers[i].getJid();
		}

		return getActiveSubscribers(nodeConfig, jids, affiliations, subscriptions);
	}

	/**
	 * Method description
	 *
	 *
	 * @param elements
	 *
	 * @return
	 */
	public static List<Element> makeArray(Element... elements) {
		LinkedList<Element> result = new LinkedList<Element>();

		for (Element element : elements) {
			result.add(element);
		}

		return result;
	}

	/**
	 * Method description
	 *
	 *
	 * @param elements
	 *
	 * @return
	 */
	public static List<Packet> makeArray(Packet... packets) {
		LinkedList<Packet> result = new LinkedList<Packet>();

		for (Packet packet : packets) {
			result.add(packet);
		}

		return result;
	}

	@Inject
	protected PubSubConfig config;

	@Inject
	protected PacketWriter packetWriter;

	@Inject(nullAllowed = false)
	private IPubSubRepository repository;

	/**
	 * Constructs ...
	 *
	 *
	 * @param config
	 * @param pubsubRepository
	 * @param packetWriter
	 *            TODO
	 */
	public AbstractPubSubModule() {
		this.setStatisticsPrefix(getClass().getSimpleName());
	}

	protected IPubSubRepository getRepository() {
		return repository;
	}

	/**
	 * Method description
	 *
	 *
	 * @param jid
	 * @param affiliations
	 * @param subscriptions
	 *
	 * @return
	 *
	 * @throws RepositoryException
	 */
	protected boolean hasSenderSubscription(final BareJID bareJid, final IAffiliations affiliations,
			final ISubscriptions subscriptions) throws RepositoryException {
		final UsersSubscription[] subscribers = subscriptions.getSubscriptions();

		for (UsersSubscription owner : subscribers) {
			UsersAffiliation affiliation = affiliations.getSubscriberAffiliation(owner.getJid());

			if (affiliation.getAffiliation() != Affiliation.owner) {
				continue;
			}
			if (bareJid.equals(owner.getJid())) {
				return true;
			}

			Map<BareJID, RosterElement> buddies = getRepository().getUserRoster(owner.getJid());
			RosterElement re = buddies.get(bareJid);
			if (re != null) {
				if (re.getSubscription() == SubscriptionType.both || re.getSubscription() == SubscriptionType.from
						|| re.getSubscription() == SubscriptionType.from_pending_out)
					return true;
			}
		}

		return false;
	}

	/**
	 * Method description
	 *
	 *
	 * @param jid
	 * @param nodeConfig
	 * @param affiliations
	 * @param subscriptions
	 *
	 * @return
	 *
	 * @throws RepositoryException
	 */
	protected boolean isSenderInRosterGroup(BareJID bareJid, AbstractNodeConfig nodeConfig, IAffiliations affiliations,
			final ISubscriptions subscriptions) throws RepositoryException {
		final UsersSubscription[] subscribers = subscriptions.getSubscriptions();
		final String[] groupsAllowed = nodeConfig.getRosterGroupsAllowed();

		if ((groupsAllowed == null) || (groupsAllowed.length == 0)) {
			return true;
		}

		// @TODO - is there a way to optimize this?
		for (UsersSubscription owner : subscribers) {
			UsersAffiliation affiliation = affiliations.getSubscriberAffiliation(owner.getJid());

			if (affiliation.getAffiliation() != Affiliation.owner) {
				continue;
			}
			if (bareJid.equals(owner)) {
				return true;
			}

			Map<BareJID, RosterElement> buddies = getRepository().getUserRoster(owner.getJid());
			RosterElement re = buddies.get(bareJid);
			if (re != null && re.getGroups() != null) {
				for (String group : groupsAllowed) {
					if (Utils.contain(group, groupsAllowed)) {
						return true;
					}
				}
			}
		}

		return false;
	}
}

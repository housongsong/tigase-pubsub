/*
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2007 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.pubsub;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.util.JIDUtils;
import tigase.xml.Element;

public abstract class AbstractModule implements Module {

	public static Element createResultIQ(Element iq) {
		return new Element("iq", new String[] { "type", "from", "to", "id" }, new String[] { "result", iq.getAttribute("to"),
				iq.getAttribute("from"), iq.getAttribute("id") });
	}

	public static List<Element> createResultIQArray(Element iq) {
		return makeArray(createResultIQ(iq));
	}

	public static List<Element> makeArray(Element... elements) {
		ArrayList<Element> result = new ArrayList<Element>();
		for (Element element : elements) {
			result.add(element);

		}
		return result;
	}

	protected final PubSubConfig config;

	protected Logger log = Logger.getLogger(this.getClass().getName());

	protected final IPubSubRepository repository;

	public AbstractModule(final PubSubConfig config, final IPubSubRepository pubsubRepository) {
		this.config = config;
		this.repository = pubsubRepository;
	}

	protected String findBestJid(final String[] allSubscribers, final String jid) {
		final String bareJid = JIDUtils.getNodeID(jid);
		String best = null;
		for (String j : allSubscribers) {
			if (j.equals(jid)) {
				return j;
			} else if (bareJid.equals(j)) {
				best = j;
			}
		}
		return best;
	}

	public String[] getActiveSubscribers(final IAffiliations affiliations, final ISubscriptions subscriptions)
			throws RepositoryException {
		UsersSubscription[] subscribers = subscriptions.getSubscriptions();
		if (subscribers == null)
			return new String[] {};
		String[] jids = new String[subscribers.length];
		for (int i = 0; i < subscribers.length; i++) {
			jids[i] = subscribers[i].getJid();
		}
		return getActiveSubscribers(jids, affiliations, subscriptions);
	}

	public String[] getActiveSubscribers(final String[] jids, final IAffiliations affiliations, final ISubscriptions subscriptions) {
		List<String> result = new ArrayList<String>();
		if (jids != null) {
			for (String jid : jids) {
				UsersAffiliation affiliation = affiliations.getSubscriberAffiliation(jid);
				// /* && affiliation.getAffiliation() != Affiliation.none */
				if (affiliation.getAffiliation() != Affiliation.outcast) {
					Subscription subscription = subscriptions.getSubscription(jid);
					if (subscription == Subscription.subscribed) {
						result.add(jid);
					}
				}

			}
		}
		return result.toArray(new String[] {});
	}

	protected boolean hasSenderSubscription(final String jid, final IAffiliations affiliations, final ISubscriptions subscriptions)
			throws RepositoryException {
		final UsersSubscription[] subscribers = subscriptions.getSubscriptions();
		final String bareJid = JIDUtils.getNodeID(jid);
		for (UsersSubscription owner : subscribers) {
			UsersAffiliation affiliation = affiliations.getSubscriberAffiliation(owner.getJid());
			if (affiliation.getAffiliation() != Affiliation.owner)
				continue;
			if (bareJid.equals(owner))
				return true;
			String[] buddies = this.repository.getUserRoster(owner.getJid());
			for (String buddy : buddies) {
				if (bareJid.equals(buddy)) {
					String s = this.repository.getBuddySubscription(owner.getJid(), bareJid);
					if (s != null && ("from".equals(s) || "both".equals(s))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	protected boolean isSenderInRosterGroup(String jid, AbstractNodeConfig nodeConfig, IAffiliations affiliations,
			final ISubscriptions subscriptions) throws RepositoryException {
		final UsersSubscription[] subscribers = subscriptions.getSubscriptions();
		final String bareJid = JIDUtils.getNodeID(jid);
		final String[] groupsAllowed = nodeConfig.getRosterGroupsAllowed();
		if (groupsAllowed == null || groupsAllowed.length == 0)
			return true;
		for (UsersSubscription owner : subscribers) {
			UsersAffiliation affiliation = affiliations.getSubscriberAffiliation(owner.getJid());
			if (affiliation.getAffiliation() != Affiliation.owner)
				continue;
			if (bareJid.equals(owner))
				return true;
			String[] buddies = this.repository.getUserRoster(owner.getJid());
			for (String buddy : buddies) {
				if (bareJid.equals(buddy)) {
					String[] groups = this.repository.getBuddyGroups(owner.getJid(), bareJid);
					for (String group : groups) {
						if (Utils.contain(group, groupsAllowed))
							return true;
					}
				}
			}
		}
		return false;
	}

}

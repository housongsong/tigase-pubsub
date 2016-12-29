/*
 * Query.java
 *
 * Tigase PubSub Component
 * Copyright (C) 2004-2016 "Tigase, Inc." <office@tigase.com>
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
package tigase.pubsub.modules.mam;

import tigase.pubsub.CollectionItemsOrdering;

/**
 * Created by andrzej on 22.12.2016.
 */
public class Query
		extends tigase.xmpp.mam.QueryImpl {

	private String pubsubNode;
	private CollectionItemsOrdering order = CollectionItemsOrdering.byCreationDate;

	public String getPubsubNode() {
		return pubsubNode;
	}

	public void setPubsubNode(String pubsubNode) {
		this.pubsubNode = pubsubNode;
	}

	public CollectionItemsOrdering getOrder() {
		return order;
	}

	public void setOrder(CollectionItemsOrdering order) {
		this.order = order;
	}

}

/*
 * MAMQueryModule.java
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

import tigase.kernel.beans.Bean;
import tigase.pubsub.PubSubComponent;
import tigase.xmpp.mam.modules.QueryModule;

/**
 * Created by andrzej on 28.12.2016.
 */
@Bean(name = "mamQueryModule", parent = PubSubComponent.class)
public class MAMQueryModule extends QueryModule {

}

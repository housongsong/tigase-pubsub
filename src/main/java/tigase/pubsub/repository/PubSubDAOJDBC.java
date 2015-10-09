/*
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2009 "Tomasz Sterna" <tomek@xiaoka.com>
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
 * $Rev: $
 * Last modified by $Author: $
 * $Date: $
 */
package tigase.pubsub.repository;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import tigase.component.exceptions.RepositoryException;
import tigase.db.DBInitException;
import tigase.db.DataRepository;
import tigase.db.Repository;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.Affiliation;
import tigase.pubsub.NodeType;
import tigase.pubsub.Subscription;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.server.XMPPServer;
import tigase.xml.Element;
import tigase.xmpp.BareJID;

@Repository.Meta(supportedUris = { "jdbc:[^:]+:.*" })
public class PubSubDAOJDBC extends PubSubDAO<Long> {

	/**
	 * Database active connection.
	 */
	protected Connection conn = null;
	/**
	 * Prepared statement for testing whether database connection is still
	 * working. If not connection to database is recreated.
	 */
	private PreparedStatement conn_valid_st = null;
	/**
	 * Connection validation helper.
	 */
	private long connectionValidateInterval = 1000 * 60;
	private CallableStatement create_node_sp = null;
	/**
	 * Database connection string.
	 */
	private DataRepository.dbTypes database = null;
	private String db_conn = null;
	private CallableStatement delete_all_nodes_sp = null;
	private CallableStatement delete_item_sp = null;
	private CallableStatement delete_node_subscriptions_sp = null;
	private CallableStatement get_all_nodes_sp = null;
	private CallableStatement get_child_nodes_sp = null;
	private CallableStatement get_item_sp = null;
	private CallableStatement get_node_affiliations_sp = null;
	private CallableStatement get_node_configuration_sp = null;
	private CallableStatement get_node_id_sp = null;
	private CallableStatement get_node_items_ids_since_sp = null;
	private CallableStatement get_node_items_ids_sp = null;
	private CallableStatement get_node_items_meta_sp = null;
	private CallableStatement get_node_subscriptions_sp = null;
	private CallableStatement get_root_nodes_sp = null;
	private CallableStatement get_user_affiliations_sp = null;
	private CallableStatement get_user_subscriptions_sp = null;
	/**
	 * Connection validation helper.
	 */
	private long lastConnectionValidated = 0;
	private CallableStatement remove_node_sp = null;
	private CallableStatement remove_service_sp = null;
	private boolean schemaOk = false;
	private CallableStatement set_node_affiliations_sp = null;
	private CallableStatement set_node_configuration_sp = null;
	private CallableStatement set_node_subscriptions_sp = null;

	private CallableStatement write_item_sp = null;

	public PubSubDAOJDBC() {
	}

	@Override
	public void addToRootCollection(BareJID serviceJid, String nodeName) throws RepositoryException {
		// TODO
		// We do not support collections yet, so all nodes are in root
		// collection.
		return;
	}

	/**
	 * <code>checkConnection</code> method checks database connection before any
	 * query. For some database servers (or JDBC drivers) it happens the
	 * connection is dropped if not in use for a long time or after certain
	 * timeout passes. This method allows us to detect the problem and
	 * reinitialize database connection.
	 *
	 * @return a <code>boolean</code> value if the database connection is
	 *         working.
	 * @exception SQLException
	 *                if an error occurs on database query.
	 */
	private boolean checkConnection() throws SQLException {
		try {
			ResultSet rs = null;
			synchronized (conn_valid_st) {
				try {
					long tmp = System.currentTimeMillis();
					if ((tmp - lastConnectionValidated) >= connectionValidateInterval) {
						rs = conn_valid_st.executeQuery();
						lastConnectionValidated = tmp;
					} // end of if ()
				} finally {
					release(null, rs);
				}
			}
		} catch (Exception e) {
			initRepo();
		} // end of try-catch
		return true;
	}

	private void checkSchema() {
		if (schemaOk)
			return;

		try {
			CallableStatement testCall = conn.prepareCall("{ call TigPubSubGetNodeId(?,?) }");
			testCall.setString(1, "tigase-pubsub");
			testCall.setString(2, "tigase-pubsub");
			testCall.execute();
			testCall.close();
			schemaOk = true;
		} catch (Exception ex) {
			String[] msg = { "", "  ---------------------------------------------", "  ERROR! Terminating the server process.",
					"  PubSub Component is not compatible with", "  database schema which exists in", "  " + db_conn,
					"  This component uses newer schema. To continue", "  use of currently deployed schema, please use",
					"  older version of PubSub Component.", "  To convert database to new schema please see:",
					"  https://projects.tigase.org/projects/tigase-pubsub/wiki/PubSub_database_schema_conversion" };
			if (XMPPServer.isOSGi()) {
				// for some reason System.out.println is not working in OSGi
				for (String line : msg) {
					log.log(Level.SEVERE, line);
				}
			} else {
				for (String line : msg) {
					System.out.println(line);
				}
			}
			log.log(Level.FINEST, "Exception during checkSchema: ", ex);

			System.exit(1);
		}
	}

	@Override
	public Long createNode(BareJID serviceJid, String nodeName, BareJID ownerJid, AbstractNodeConfig nodeConfig,
			NodeType nodeType, Long collectionId) throws RepositoryException {
		Long nodeId = null;
		try {
			ResultSet rs = null;
			String serializedNodeConfig = null;
			if (nodeConfig != null) {
				nodeConfig.setNodeType(nodeType);
				serializedNodeConfig = nodeConfig.getFormElement().toString();
			}

			checkConnection();
			synchronized (create_node_sp) {
				try {
					create_node_sp.setString(1, serviceJid.toString());
					create_node_sp.setString(2, nodeName);
					create_node_sp.setInt(3, nodeType.ordinal());
					create_node_sp.setString(4, ownerJid.toString());
					create_node_sp.setString(5, serializedNodeConfig);
					if (collectionId == null) {
						create_node_sp.setNull(6, java.sql.Types.BIGINT);
					} else {
						create_node_sp.setLong(6, collectionId);
					}

					if (db_conn != null) {
						switch (this.database) {
							case sqlserver:
							case jtds:
								create_node_sp.executeUpdate();
								return getNodeId(serviceJid, nodeName);

						default:
							rs = create_node_sp.executeQuery();
							break;
						}

						if ( rs.next() ){
							nodeId = rs.getLong( 1 );
						}
					}
				} finally {
					release(null, rs);
				}
			}
		} catch (SQLIntegrityConstraintViolationException e) {
			throw new RepositoryException("Error while adding node to repository, already exists?", e);
		} catch (SQLException e) {
			e.printStackTrace();
			throw new RepositoryException("Problem accessing repository.", e);
		}

		return nodeId;
	}

	@Override
	public void deleteItem(BareJID serviceJid, Long nodeId, String id) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "deleting Item: serviceJid: {0}, nodeId: {1}, id: {2}",
					new Object[] { serviceJid, nodeId, id });
		}
		try {
			checkConnection();
			synchronized (delete_item_sp) {
				delete_item_sp.setLong(1, nodeId);
				delete_item_sp.setString(2, id);
				delete_item_sp.execute();
			}
		} catch (SQLException e) {
			throw new RepositoryException("Item removing error", e);
		}
	}

	@Override
	public void deleteNode(BareJID serviceJid, Long nodeId) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "deleting Node: serviceJid: {0}, nodeId: {1}", new Object[] { serviceJid, nodeId });
		}
		try {
			checkConnection();
			synchronized (remove_node_sp) {
				remove_node_sp.setLong(1, nodeId);
				remove_node_sp.execute();
			}
		} catch (SQLException e) {
			throw new RepositoryException("Node deleting error", e);
		}
	}

	@Override
	public void destroy() {
		try {
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "closing JDBC connection: {0} - {1}",
						new Object[] { conn.getClass().getCanonicalName(), conn });
			}
			if (!conn.isClosed()) {
				conn.close();
			}
		} catch (SQLException e) {
			log.log(Level.WARNING, "Problem closing jdbc connection: " + db_conn, e);
		}
		super.destroy();
	}

	@Override
	public String[] getAllNodesList(BareJID serviceJid) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "get all nodes list: serviceJid: {0}", new Object[] { serviceJid });
		}
		try {
			ResultSet rs = null;
			checkConnection();
			synchronized (get_all_nodes_sp) {
				try {
					get_all_nodes_sp.setString(1, serviceJid.toString());
					rs = get_all_nodes_sp.executeQuery();
					List<String> names = new ArrayList<String>();
					while (rs.next()) {
						names.add(rs.getString(1));
					}
					return names.toArray(new String[0]);
				} finally {
					release(null, rs);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Nodes list getting error", e);
		} // end of catch
	}

	@Override
	public String[] getChildNodes(BareJID serviceJid, String nodeName) throws RepositoryException {
		return getNodesList(serviceJid, nodeName);
	}

	protected Date getDateFromItem(BareJID serviceJid, long nodeId, String id, int field) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "getting date from item: serviceJid: {0}, nodeId: {1}, id: {2}, field: {3}",
					new Object[] { serviceJid, nodeId, id, field });
		}
		try {
			ResultSet rs = null;
			checkConnection();
			synchronized (get_item_sp) {
				try {
					get_item_sp.setLong(1, nodeId);
					get_item_sp.setString(2, id);
					rs = get_item_sp.executeQuery();
					if (rs.next()) {
						// String date = rs.getString( field );
						// if ( date == null ) {
						// return null;
						// }
						// -- why do we need this?
						// return DateFormat.getDateInstance().parse( date );
						return rs.getTimestamp(field);
					}
				} finally {
					release(null, rs);
				}
				return null;
			}
		} catch (SQLException e) {
			throw new RepositoryException("Item field " + field + " reading error", e);
			// } catch ( ParseException e ) {
			// throw new RepositoryException( "Item field " + field + " parsing
			// error", e );
		} // end of catch
	}

	// @Override
	// public String getItemPublisher( BareJID serviceJid, long nodeId, String
	// id ) throws RepositoryException {
	// return getStringFromItem( serviceJid, nodeId, id, 2 );
	// }

	@Override
	public Element getItem( BareJID serviceJid, Long nodeId, String id ) throws RepositoryException {
		String data = getStringFromItem( serviceJid, nodeId, id, 1 );
		if (data == null)
			return null;
		return itemDataToElement( data.toCharArray() );
	}

	@Override
	public Date getItemCreationDate(final BareJID serviceJid, final Long nodeId, final String id) throws RepositoryException {
		return getDateFromItem(serviceJid, nodeId, id, 3);
	}

	@Override
	public String[] getItemsIds(BareJID serviceJid, Long nodeId) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "getting items IDs: serviceJid: {0}, nodeId: {1}", new Object[] { serviceJid, nodeId });
		}
		if (null != nodeId) {
			try {
				ResultSet rs = null;
				checkConnection();
				synchronized (get_node_items_ids_sp) {
					try {
						get_node_items_ids_sp.setLong(1, nodeId);
						rs = get_node_items_ids_sp.executeQuery();
						List<String> ids = new ArrayList<String>();
						while (rs.next()) {
							ids.add(rs.getString(1));
						}
						return ids.toArray(new String[ids.size()]);
					} finally {
						release(null, rs);
					}
				}
			} catch (SQLException e) {
				throw new RepositoryException("Items list reading error", e);
			} // end of catch
		} else {
			return null;
		}
	}

	@Override
	public String[] getItemsIdsSince(BareJID serviceJid, Long nodeId, Date since) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Getting items since: serviceJid: {0}, nodeId: {1}, since: {2}",
					new Object[] { serviceJid, nodeId, since });
		}
		try {
			ResultSet rs = null;
			Timestamp sinceTs = new Timestamp(since.getTime());
			checkConnection();
			synchronized (get_node_items_ids_since_sp) {
				try {
					get_node_items_ids_since_sp.setLong(1, nodeId);
					get_node_items_ids_since_sp.setTimestamp(2, sinceTs);
					rs = get_node_items_ids_since_sp.executeQuery();
					List<String> ids = new ArrayList<String>();
					while (rs.next()) {
						ids.add(rs.getString(1));
					}
					return ids.toArray(new String[ids.size()]);
				} finally {
					release(null, rs);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Items list reading error", e);
		} // end of catch
	}

	@Override
	public List<IItems.ItemMeta> getItemsMeta(BareJID serviceJid, Long nodeId, String nodeName) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Getting items meta: serviceJid: {0}, nodeId: {1}, nodeName: {2}",
					new Object[] { serviceJid, nodeId, nodeName });
		}
		try {
			ResultSet rs = null;
			checkConnection();
			synchronized (get_node_items_meta_sp) {
				try {
					get_node_items_meta_sp.setLong(1, nodeId);
					rs = get_node_items_meta_sp.executeQuery();
					List<IItems.ItemMeta> results = new ArrayList<IItems.ItemMeta>();
					while (rs.next()) {
						String id = rs.getString(1);
						Date creationDate = rs.getTimestamp(2);
						Date updateDate = rs.getTimestamp(3);
						results.add(new IItems.ItemMeta(nodeName, id, creationDate, updateDate));
					}
					return results;
				} finally {
					release(null, rs);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Items list reading error", e);
		} // end of catch
	}

	@Override
	public Date getItemUpdateDate(BareJID serviceJid, Long nodeId, String id) throws RepositoryException {
		return getDateFromItem(serviceJid, nodeId, id, 4);
	}

	@Override
	public NodeAffiliations getNodeAffiliations(BareJID serviceJid, Long nodeId) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Getting node affiliation: serviceJid: {0}, nodeId: {1}",
					new Object[] { serviceJid, nodeId });
		}
		try {
			ResultSet rs = null;
			checkConnection();
			synchronized (get_node_affiliations_sp) {
				try {
					get_node_affiliations_sp.setLong(1, nodeId);
					rs = get_node_affiliations_sp.executeQuery();
					ArrayDeque<UsersAffiliation> data = new ArrayDeque<UsersAffiliation>();
					while (rs.next()) {
						BareJID jid = BareJID.bareJIDInstanceNS(rs.getString(1));
						Affiliation affil = Affiliation.valueOf(rs.getString(2));
						data.offer(new UsersAffiliation(jid, affil));
					}
					return NodeAffiliations.create(data);
				} finally {
					release(null, rs);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Node subscribers reading error", e);
		} // end of catch
	}

	@Override
	public String getNodeConfig(BareJID serviceJid, Long nodeId) throws RepositoryException {
		return readNodeConfigFormData(serviceJid, nodeId);
	}

	@Override
	public Long getNodeId(BareJID serviceJid, String nodeName) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Getting Node ID: serviceJid: {0}, nodeName: {1}", new Object[] { serviceJid, nodeName });
		}
		try {
			ResultSet rs = null;
			checkConnection();
			synchronized (get_node_id_sp) {
				try {
					get_node_id_sp.setString(1, serviceJid.toString());
					get_node_id_sp.setString(2, nodeName);
					rs = get_node_id_sp.executeQuery();
					if (rs.next()) {
						return rs.getLong(1);
					}
					return null;
				} finally {
					release(null, rs);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Retrieving node id error", e);
		}
	}

	@Override
	public String[] getNodesList(BareJID serviceJid, String nodeName) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Getting nodes list: serviceJid: {0}, nodeName: {1}", new Object[] { serviceJid, nodeName });
		}
		try {
			ResultSet rs = null;
			checkConnection();
			if (nodeName == null) {
				synchronized (get_root_nodes_sp) {
					try {
						get_root_nodes_sp.setString(1, serviceJid.toString());
						rs = get_root_nodes_sp.executeQuery();
						List<String> names = new ArrayList<String>();
						while (rs.next()) {
							names.add(rs.getString(1));
						}
						return names.toArray(new String[0]);
					} finally {
						release(null, rs);
					}
				}
			} else {
				synchronized (get_child_nodes_sp) {
					try {
						get_child_nodes_sp.setString(1, serviceJid.toString());
						get_child_nodes_sp.setString(2, nodeName);
						rs = get_child_nodes_sp.executeQuery();
						List<String> names = new ArrayList<String>();
						while (rs.next()) {
							names.add(rs.getString(1));
						}
						return names.toArray(new String[0]);
					} finally {
						release(null, rs);
					}
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Nodes list getting error", e);
		} // end of catch
	}

	@Override
	public NodeSubscriptions getNodeSubscriptions(BareJID serviceJid, Long nodeId) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Getting node subscriptions: serviceJid: {0}, nodeId: {1}",
					new Object[] { serviceJid, nodeId });
		}
		try {
			ResultSet rs = null;
			final NodeSubscriptions ns = NodeSubscriptions.create();
			checkConnection();
			synchronized (get_node_subscriptions_sp) {
				try {
					get_node_subscriptions_sp.setLong(1, nodeId);
					rs = get_node_subscriptions_sp.executeQuery();
					ArrayDeque<UsersSubscription> data = new ArrayDeque<UsersSubscription>();
					while (rs.next()) {
						BareJID jid = BareJID.bareJIDInstanceNS(rs.getString(1));
						Subscription subscr = Subscription.valueOf(rs.getString(2));
						String subscrId = rs.getString(3);
						data.offer(new UsersSubscription(jid, subscrId, subscr));
					}
					ns.init(data);
					return ns;
				} finally {
					release(null, rs);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Node subscribers reading error", e);
		} // end of catch
	}

	public String getResourceUri() {
		return db_conn;
	}

	protected String getStringFromItem(BareJID serviceJid, long nodeId, String id, int field) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Getting string from item: serviceJid: {0}, nodeId: {1}",
					new Object[] { serviceJid, nodeId });
		}
		try {
			ResultSet rs = null;
			checkConnection();
			synchronized (get_item_sp) {
				try {
					get_item_sp.setLong(1, nodeId);
					get_item_sp.setString(2, id);
					rs = get_item_sp.executeQuery();
					if (rs.next()) {
						return rs.getString(field);
					}
					return null;
				} finally {
					release(null, rs);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Item field " + field + " reading error", e);
		} // end of catch
	}

	@Override
	public Map<String, UsersAffiliation> getUserAffiliations(BareJID serviceJid, BareJID jid) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Getting user affiliation: serviceJid: {0}, jid: {1}", new Object[] { serviceJid, jid });
		}
		try {
			ResultSet rs = null;
			Map<String, UsersAffiliation> result = new HashMap<String, UsersAffiliation>();
			synchronized (get_user_affiliations_sp) {
				try {
					get_user_affiliations_sp.setString(1, serviceJid.toString());
					get_user_affiliations_sp.setString(2, jid.toString());
					rs = get_user_affiliations_sp.executeQuery();
					while (rs.next()) {
						String nodeName = rs.getString(1);
						Affiliation affil = Affiliation.valueOf(rs.getString(2));
						result.put(nodeName, new UsersAffiliation(jid, affil));
					}
				} finally {
					release(null, rs);
				}
			}
			return result;
		} catch (SQLException e) {
			throw new RepositoryException("User affiliations reading error", e);
		}
	}

	@Override
	public Map<String, UsersSubscription> getUserSubscriptions(BareJID serviceJid, BareJID jid) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Getting user subs: serviceJid: {0}, jid: {1}", new Object[] { serviceJid, jid });
		}
		try {
			ResultSet rs = null;
			Map<String, UsersSubscription> result = new HashMap<String, UsersSubscription>();
			synchronized (get_user_subscriptions_sp) {
				try {
					get_user_subscriptions_sp.setString(1, serviceJid.toString());
					get_user_subscriptions_sp.setString(2, jid.toString());
					rs = get_user_subscriptions_sp.executeQuery();
					while (rs.next()) {
						String nodeName = rs.getString(1);
						Subscription subscr = Subscription.valueOf(rs.getString(2));
						String subscrId = rs.getString(3);
						result.put(nodeName, new UsersSubscription(jid, subscrId, subscr));
					}
				} finally {
					release(null, rs);
				}
			}
			return result;
		} catch (SQLException e) {
			throw new RepositoryException("User affiliations reading error", e);
		}
	}

	/**
	 * <code>initPreparedStatements</code> method initializes internal database
	 * connection variables such as prepared statements.
	 *
	 * @exception SQLException
	 *                if an error occurs on database query.
	 */
	private void initPreparedStatements() throws SQLException {
		String query;
		switch (database) {
		case derby:
			query = "VALUES 1";
			break;
		default:
			query = "select 1";
			break;
		}
		conn_valid_st = conn.prepareStatement(query);

		query = "{ call TigPubSubCreateNode(?, ?, ?, ?, ?, ?) }";
				create_node_sp = conn.prepareCall( query );

		query = "{ call TigPubSubRemoveNode(?) }";
		remove_node_sp = conn.prepareCall( query );
		
		query = "{ call TigPubSubRemoveService(?) }";
		remove_service_sp = conn.prepareCall( query );

		query = "{ call TigPubSubGetNodeId(?, ?) }";
		get_node_id_sp = conn.prepareCall(query);

		query = "{ call TigPubSubGetItem(?, ?) }";
		get_item_sp = conn.prepareCall(query);

		query = "{ call TigPubSubWriteItem(?, ?, ?, ?) }";
		write_item_sp = conn.prepareCall(query);

		query = "{ call TigPubSubDeleteItem(?, ?) }";
		delete_item_sp = conn.prepareCall(query);

		query = "{ call TigPubSubGetNodeItemsIds(?) }";
		get_node_items_ids_sp = conn.prepareCall(query);

		query = "{ call TigPubSubGetNodeItemsIdsSince(?,?) }";
		get_node_items_ids_since_sp = conn.prepareCall(query);

		query = "{ call TigPubSubGetNodeItemsMeta(?) }";
		get_node_items_meta_sp = conn.prepareCall(query);

		query = "{ call TigPubSubGetAllNodes(?) }";
		get_all_nodes_sp = conn.prepareCall(query);

		query = "{ call TigPubSubGetRootNodes(?) }";
		get_root_nodes_sp = conn.prepareCall(query);

		query = "{ call TigPubSubGetChildNodes(?,?) }";
		get_child_nodes_sp = conn.prepareCall(query);

		query = "{ call TigPubSubDeleteAllNodes(?) }";
		delete_all_nodes_sp = conn.prepareCall(query);

		query = "{ call TigPubSubSetNodeConfiguration(?, ?, ?) }";
		set_node_configuration_sp = conn.prepareCall(query);

		query = "{ call TigPubSubSetNodeAffiliation(?, ?, ?) }";
		set_node_affiliations_sp = conn.prepareCall(query);

		query = "{ call TigPubSubGetNodeConfiguration(?) }";
		get_node_configuration_sp = conn.prepareCall(query);

		query = "{ call TigPubSubGetNodeAffiliations(?) }";
		get_node_affiliations_sp = conn.prepareCall(query);

		query = "{ call TigPubSubGetNodeSubscriptions(?) }";
		get_node_subscriptions_sp = conn.prepareCall(query);

		query = "{ call TigPubSubSetNodeSubscription(?, ?, ?, ?) }";
		set_node_subscriptions_sp = conn.prepareCall(query);

		query = "{ call TigPubSubDeleteNodeSubscription(?, ?) }";
		delete_node_subscriptions_sp = conn.prepareCall(query);

		query = "{ call TigPubSubGetUserAffiliations(?, ?) }";
		get_user_affiliations_sp = conn.prepareCall(query);

		query = "{ call TigPubSubGetUserSubscriptions(?, ?) }";
		get_user_subscriptions_sp = conn.prepareCall(query);
	}

	/**
	 * <code>initRepo</code> method initializes database connection and data
	 * repository.
	 *
	 * @exception SQLException
	 *                if an error occurs on database query.
	 */
	private void initRepo() throws SQLException {
		synchronized (db_conn) {
			if (conn != null) {
				try {
					if (!conn.isClosed()) {
						if (log.isLoggable(Level.FINEST)) {
							log.log(Level.FINEST, "closing JDBC connection: {0}", conn);
						}
						conn.close();
					}
				} catch (Exception ex) {
					log.log(Level.WARNING, "Exception occured while closing old DB connection for reinitialization", ex);
				}
			}
			String driverClass = null;
			switch (database) {
			case postgresql:
				driverClass = "org.postgresql.Driver";
				break;
			case mysql:
				driverClass = "com.mysql.jdbc.Driver";
				break;
			case derby:
				driverClass = "org.apache.derby.jdbc.EmbeddedDriver";
				break;
			case jtds:
				driverClass = "net.sourceforge.jtds.jdbc.Driver";
				break;
			case sqlserver:
				driverClass = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
				break;
			default:
				driverClass = "net.sf.log4jdbc.sql.jdbcapi.DriverSpy";
				break;
			}

			try {
				Class.forName(driverClass, true, this.getClass().getClassLoader());
			} catch (ClassNotFoundException ex) {
				log.log(Level.SEVERE, null, ex);
			}

			conn = DriverManager.getConnection(db_conn);
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "establishing JDBC connection: {0} for {1}", new Object[] { conn, db_conn });
			}
			checkSchema();
			initPreparedStatements();
		}
	}

	@Override
	public void initRepository(String resource_uri, Map<String, String> params) throws DBInitException {
		this.db_conn = resource_uri;

		if (db_conn.startsWith("jdbc:postgresql")) {
			database = DataRepository.dbTypes.postgresql;
		} else if (db_conn.startsWith("jdbc:mysql")) {
			database = DataRepository.dbTypes.mysql;
		} else if (db_conn.startsWith("jdbc:derby")) {
			database = DataRepository.dbTypes.derby;
		} else if (db_conn.startsWith("jdbc:jtds:sqlserver")) {
			database = DataRepository.dbTypes.jtds;
		} else if (db_conn.startsWith("jdbc:sqlserver")) {
			database = DataRepository.dbTypes.sqlserver;
		}

		try {
			initRepo();
		} catch (SQLException e) {
			conn = null;
			throw new DBInitException("Problem initializing jdbc connection: " + db_conn, e);
		}
	}

	protected String readNodeConfigFormData(final BareJID serviceJid, final long nodeId) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "reding node config: serviceJid: {0}, nodeId: {1}", new Object[] { serviceJid, nodeId });
		}
		try {
			ResultSet rs = null;
			checkConnection();
			synchronized (get_node_configuration_sp) {
				try {
					get_node_configuration_sp.setLong(1, nodeId);
					rs = get_node_configuration_sp.executeQuery();
					if (rs.next()) {
						return rs.getString(1);
					}
					return null;
				} finally {
					release(null, rs);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Node subscribers reading error", e);
		} // end of catch
	}

	private void release(Statement stmt, ResultSet rs) {
		if (rs != null) {
			try {
				rs.close();
			} catch (SQLException sqlEx) {
			}
		}
		if (stmt != null) {
			try {
				stmt.close();
			} catch (SQLException sqlEx) {
			}
		}
	}

	@Override
	public void removeAllFromRootCollection(BareJID serviceJid) throws RepositoryException {
		// TODO check it
		try {
			checkConnection();
			synchronized (delete_all_nodes_sp) {
				delete_all_nodes_sp.setString(1, serviceJid.toString());
				delete_all_nodes_sp.execute();
			}
		} catch (SQLException e) {
			throw new RepositoryException("Removing root collection error", e);
		}
	}

	@Override
	public void removeFromRootCollection(BareJID serviceJid, Long nodeId) throws RepositoryException {
		// TODO check it
		deleteNode(serviceJid, nodeId);
	}

	@Override
	public void removeNodeSubscription(BareJID serviceJid, Long nodeId, BareJID jid) throws RepositoryException {
		try {
			checkConnection();
			synchronized (delete_node_subscriptions_sp) {
				delete_node_subscriptions_sp.setLong(1, nodeId);
				delete_node_subscriptions_sp.setString(2, jid.toString());
				delete_node_subscriptions_sp.execute();
			}
		} catch (SQLException e) {
			throw new RepositoryException("Node subscribers fragment removing error", e);
		}
	}

	@Override
	public void removeService( BareJID serviceJid ) throws RepositoryException {
		try {
			checkConnection();
			synchronized ( remove_service_sp ) {
				remove_service_sp.setString( 1, serviceJid.toString() );
				remove_service_sp.execute();
			}
		} catch ( SQLException e ) {
			throw new RepositoryException( "Node subscribers fragment removing error", e );
		}
	}
	
	@Override
	public void updateNodeAffiliation( BareJID serviceJid, Long nodeId, String nodeName, UsersAffiliation affiliation ) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.finest("Updating node affiliation[1]: " + nodeName + " / " + affiliation);
		}

		try {
			ResultSet rs = null;
			checkConnection();
			synchronized (set_node_affiliations_sp) {
				try {
					set_node_affiliations_sp.setLong(1, nodeId);
					set_node_affiliations_sp.setString(2, affiliation.getJid().toString());
					set_node_affiliations_sp.setString(3, affiliation.getAffiliation().name());
					switch (database) {
					// if ( db_conn != null ){
					// if ( db_conn.contains( "mysql" ) ){
					case mysql:
						rs = set_node_affiliations_sp.executeQuery();
						break;
					// rs = set_node_affiliations_sp.executeQuery();
					// }
					// if ( db_conn.contains( "sqlserver" ) ){
					// set_node_affiliations_sp.executeUpdate();
					// }
					default:
						set_node_affiliations_sp.execute();
						break;
					}
				} finally {
					release(null, rs);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Node subscribers writing error", e);
		} // end of catch
		if (log.isLoggable(Level.FINEST)) {
			log.finest("Updating node affiliation[2]");
		}

	}

	@Override
	public void updateNodeConfig(final BareJID serviceJid, final Long nodeId, final String serializedData,
			final Long collectionId) throws RepositoryException {
		try {
			ResultSet rs = null;
			checkConnection();
			synchronized (set_node_configuration_sp) {
				try {
					set_node_configuration_sp.setLong(1, nodeId);
					set_node_configuration_sp.setString(2, serializedData);
					if (collectionId == null) {
						set_node_configuration_sp.setNull(3, java.sql.Types.BIGINT);
					} else {
						set_node_configuration_sp.setLong(3, collectionId);
					}
					switch (database) {
					// if ( db_conn != null ){
					// if ( db_conn.contains( "mysql" ) ){
					case mysql:
						rs = set_node_configuration_sp.executeQuery();
						break;
					// rs = set_node_configuration_sp.executeQuery();
					// }
					// if ( db_conn.contains( "sqlserver" ) ){
					// set_node_configuration_sp.executeUpdate();
					// }
					default:
						set_node_configuration_sp.execute();
						break;
					}
				} finally {
					release(null, rs);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Node configuration writing error", e);
		} // end of catch
	}

	@Override
	public void updateNodeSubscription(BareJID serviceJid, Long nodeId, String nodeName, UsersSubscription subscription)
			throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.finest("Updating node subscriptions[1]: " + nodeName + " / " + subscription);
		}

		try {
			ResultSet rs = null;
			checkConnection();
			synchronized (set_node_subscriptions_sp) {
				try {
					set_node_subscriptions_sp.setLong(1, nodeId);
					set_node_subscriptions_sp.setString(2, subscription.getJid().toString());
					set_node_subscriptions_sp.setString(3, subscription.getSubscription().name());
					set_node_subscriptions_sp.setString(4, subscription.getSubid());
					switch (database) {
					// if ( db_conn != null ){
					// if ( db_conn.contains( "mysql" ) ){
					case mysql:
						rs = set_node_subscriptions_sp.executeQuery();
						break;
					// }
					// if ( db_conn.contains( "sqlserver" ) ){
					// set_node_subscriptions_sp.executeUpdate();
					// }
					default:
						set_node_subscriptions_sp.execute();
						break;
					}
				} finally {
					release(null, rs);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Node subscribers writing error", e);
		} // end of catch
		if (log.isLoggable(Level.FINEST)) {
			log.finest("Updating node subscriptions[2]");
		}

	}

	@Override
	public void writeItem(final BareJID serviceJid, final Long nodeId, long timeInMilis, final String id,
			final String publisher, final Element item) throws RepositoryException {
		try {
			ResultSet rs = null;
			checkConnection();
			synchronized (write_item_sp) {
				try {
					write_item_sp.setLong(1, nodeId);
					write_item_sp.setString(2, id);
					write_item_sp.setString(3, publisher);
					write_item_sp.setString(4, item.toString());
					if (db_conn != null) {
						// if ( db_conn.contains( "mysql" ) ){
						// rs = write_item_sp.executeQuery();
						// }
						// if ( db_conn.contains( "sqlserver" ) ){
						// write_item_sp.executeUpdate();
						// }
						write_item_sp.execute();
					}
				} finally {
					release(null, rs);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Item writing error", e);
		}
	}
}

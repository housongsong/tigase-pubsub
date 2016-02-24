/*
 * StoredProcedures.java
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
package tigase.pubsub.repository.derby;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

/**
 *
 * @author andrzej
 */
public class StoredProcedures {

	private static final Logger log = Logger.getLogger(tigase.db.derby.StoredProcedures.class.getName());

	public static void tigPubSubCreateNode(String serviceJid, String nodeName, Integer nodeType, String nodeCreator,
			String nodeConf, Long collectionId, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			long serviceJidId = tigPubSubEnsureServiceJid(serviceJid);
			long nodeCreatorId = tigPubSubEnsureJid(nodeCreator);
			PreparedStatement ps =
					conn.prepareStatement("insert into tig_pubsub_nodes (service_id,name,type,creator_id,creation_date,configuration,collection_id)" +
							" values (?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);

			ps.setLong(1, serviceJidId);
			ps.setString(2, nodeName);
			ps.setInt(3, nodeType);
			ps.setLong(4, nodeCreatorId);
			ps.setTimestamp(5, new java.sql.Timestamp(System.currentTimeMillis()));
			ps.setString(6, nodeConf);
			if (collectionId == null) {
				ps.setNull(7, java.sql.Types.BIGINT);
			}
			else {
				ps.setLong(7, collectionId);
			}

			ps.executeUpdate();
			data[0] = ps.getGeneratedKeys();
		} catch (SQLException e) {

			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubDeleteAllNodes(String serviceJid, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("delete from tig_pubsub_items where node_id in ("
					+ "select n.node_id from tig_pubsub_nodes n"
					+ " inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id" + " where sj.service_jid = ?)");
			ps.setString(1, serviceJid);
			ps.executeUpdate();
			ps = conn.prepareStatement("delete from tig_pubsub_affiliations where node_id in ("
					+ "select n.node_id from tig_pubsub_nodes n"
					+ " inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id" + " where sj.service_jid = ?)");
			ps.setString(1, serviceJid);
			ps.executeUpdate();
			ps = conn.prepareStatement("delete from tig_pubsub_subscriptions where node_id in ("
					+ "select n.node_id from tig_pubsub_nodes n"
					+ " inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id" + " where sj.service_jid = ?)");
			ps.setString(1, serviceJid);
			ps.executeUpdate();
			ps = conn.prepareStatement("delete from tig_pubsub_nodes where node_id in ("
					+ "select n.node_id from tig_pubsub_nodes n"
					+ " inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id" + " where sj.service_jid = ?)");
			ps.setString(1, serviceJid);
			ps.executeUpdate();
		} catch (SQLException e) {
			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubDeleteItem(Long nodeId, String itemId, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("delete from tig_pubsub_items where node_id = ? and id = ?");
			ps.setLong(1, nodeId);
			ps.setString(2, itemId);
			ps.executeUpdate();
		} catch (SQLException e) {
			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubDeleteNodeSubscription(Long nodeId, String jid, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("delete from tig_pubsub_subscriptions where node_id = ?"
					+ " and jid_id = (select jid_id from tig_pubsub_jids where jid = ?)");
			ps.setLong(1, nodeId);
			ps.setString(2, jid);
			ps.executeUpdate();
		} catch (SQLException e) {
			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static Long tigPubSubEnsureJid(String jid) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("select jid_id from tig_pubsub_jids where jid = ?");

			ps.setString(1, jid);
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				return rs.getLong(1);
			} else {
				ps = conn.prepareStatement("insert into tig_pubsub_jids (jid) values (?)", Statement.RETURN_GENERATED_KEYS);
				ps.setString(1, jid);
				ps.executeUpdate();
				rs = ps.getGeneratedKeys();
				if (rs.next())
					return rs.getLong(1);
			}
			return null;
		} catch (SQLException e) {

			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static Long tigPubSubEnsureServiceJid(String serviceJid) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(
					"select service_id from tig_pubsub_service_jids where service_jid = ?");

			ps.setString(1, serviceJid);
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				return rs.getLong(1);
			} else {
				ps = conn.prepareStatement("insert into tig_pubsub_service_jids (service_jid) values (?)",
						Statement.RETURN_GENERATED_KEYS);
				ps.setString(1, serviceJid);
				ps.executeUpdate();
				rs = ps.getGeneratedKeys();
				if (rs.next())
					return rs.getLong(1);
			}
			return null;
		} catch (SQLException e) {

			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubFixItem(Long nodeId, String itemId, java.sql.Timestamp creationDate,
		java.sql.Timestamp updateDate) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(
					"update tig_pubsub_items set creation_date = ?," + " update_date = ? where node_id = ? and id = ?");
			if (creationDate == null)
				ps.setNull(1, java.sql.Types.TIMESTAMP);
			else
				ps.setTimestamp(1, creationDate);
			if (updateDate == null)
				ps.setNull(2, java.sql.Types.TIMESTAMP);
			else
				ps.setTimestamp(2, updateDate);
			ps.setLong(3, nodeId);
			ps.setString(4, itemId);
			ps.executeUpdate();
		} catch (SQLException e) {
			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubFixNode(Long nodeId, java.sql.Timestamp creationDate) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(
					"update tig_pubsub_nodes set creation_date = ?" + " where node_id = ?");
			if (creationDate == null)
				ps.setNull(1, java.sql.Types.TIMESTAMP);
			else
				ps.setTimestamp(1, creationDate);
			ps.setLong(2, nodeId);
			ps.executeUpdate();
		} catch (SQLException e) {
			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubGetAllNodes(String serviceJid, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("select n.name, n.node_id from tig_pubsub_nodes n"
					+ " inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id" + " where sj.service_jid = ?");
			ps.setString(1, serviceJid);
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubGetChildNodes(String serviceJid, String collection, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("select n.name, n.node_id from tig_pubsub_nodes n"
					+ " inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id"
					+ " inner join tig_pubsub_nodes p on p.node_id = n.collection_id and p.service_id = sj.service_id"
					+ " where sj.service_jid = ? and p.name = ?");
			ps.setString(1, serviceJid);
			ps.setString(2, collection);
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubGetItem(Long nodeId, String itemId, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(
					"select data, p.jid, creation_date, update_date " + "from tig_pubsub_items pi "
							+ "inner join tig_pubsub_jids p on p.jid_id = pi.publisher_id " + "where node_id = ? and id = ?");
			ps.setLong(1, nodeId);
			ps.setString(2, itemId);
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubGetNodeAffiliations(Long nodeId, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("select pj.jid, pa.affiliation" + " from tig_pubsub_affiliations pa"
					+ " inner join tig_pubsub_jids pj on pa.jid_id = pj.jid_id" + " where pa.node_id = ?");
			ps.setLong(1, nodeId);
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubGetNodeConfiguration(Long nodeId, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("select configuration from tig_pubsub_nodes where node_id = ?");
			ps.setLong(1, nodeId);
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubGetNodeId(String serviceJid, String nodeName, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("select n.node_id from tig_pubsub_nodes n "
					+ "inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id "
					+ "where sj.service_jid = ? and n.name = ?");
			ps.setString(1, serviceJid);
			ps.setString(2, nodeName);
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubGetNodeItemIds(Long nodeId, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(
					"select id from tig_pubsub_items where node_id = ?" + " order by creation_date");
			ps.setLong(1, nodeId);
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubGetNodeItemIdsSince(Long nodeId, java.sql.Timestamp since, ResultSet[] data)
			throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(
					"select id from tig_pubsub_items where node_id = ?" + " and creation_date >= ? order by creation_date");
			ps.setLong(1, nodeId);
			ps.setTimestamp(2, since);
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubGetNodeItemsMeta(Long nodeId, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("select id, creation_date, update_date from tig_pubsub_items"
					+ " where node_id = ? order by creation_date");
			ps.setLong(1, nodeId);
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubGetNodeMeta(String serviceJid, String nodeName, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("select n.node_id, n.configuration, cj.jid, n.creation_date "
					+ "from tig_pubsub_nodes n "
					+ "inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id "
					+ "inner join tig_pubsub_jids cj on cj.jid_id = n.creator_id "
					+ "where sj.service_jid = ? and n.name = ?");
			ps.setString(1, serviceJid);
			ps.setString(2, nodeName);
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubGetNodeSubscriptions(Long nodeId, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(
					"select pj.jid, ps.subscription, ps.subscription_id" + " from tig_pubsub_subscriptions ps"
							+ " inner join tig_pubsub_jids pj on ps.jid_id = pj.jid_id" + " where ps.node_id = ?");
			ps.setLong(1, nodeId);
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubGetRootNodes(String serviceJid, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("select n.name, n.node_id from tig_pubsub_nodes n"
					+ " inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id"
					+ " where sj.service_jid = ? and collection_id is null");
			ps.setString(1, serviceJid);
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubGetUserAffiliations(String serviceJid, String jid, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("select n.name, pa.affiliation" + " from tig_pubsub_nodes n"
					+ " inner join tig_pubsub_service_jids sj on sj.service_id = n.service_id"
					+ " inner join tig_pubsub_affiliations pa on pa.node_id = n.node_id"
					+ " inner join tig_pubsub_jids pj on pj.jid_id = pa.jid_id" + " where pj.jid = ? and sj.service_jid = ?");
			ps.setString(1, jid);
			ps.setString(2, serviceJid);
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubGetUserSubscriptions(String serviceJid, String jid, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("select n.name, ps.subscription, ps.subscription_id"
					+ " from tig_pubsub_nodes n" + " inner join tig_pubsub_service_jids sj on sj.service_id = n.service_id"
					+ " inner join tig_pubsub_subscriptions ps on ps.node_id = n.node_id"
					+ " inner join tig_pubsub_jids pj on pj.jid_id = ps.jid_id" + " where pj.jid = ? and sj.service_jid = ?");
			ps.setString(1, jid);
			ps.setString(2, serviceJid);
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubRemoveNode(Long nodeId, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("delete from tig_pubsub_items where node_id = ?");
			ps.setLong(1, nodeId);
			ps.executeUpdate();
			ps = conn.prepareStatement("delete from tig_pubsub_subscriptions where node_id = ?");
			ps.setLong(1, nodeId);
			ps.executeUpdate();
			ps = conn.prepareStatement("delete from tig_pubsub_affiliations where node_id = ?");
			ps.setLong(1, nodeId);
			ps.executeUpdate();
			ps = conn.prepareStatement("delete from tig_pubsub_nodes where node_id = ?");
			ps.setLong(1, nodeId);
			ps.executeUpdate();
		} catch (SQLException e) {
			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubSetNodeAffiliation(Long nodeId, String jid, String affil, ResultSet[] data)
			throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("select 1 from tig_pubsub_affiliations pa"
					+ " inner join tig_pubsub_jids pj on pa.jid_id = pj.jid_id" + " where pa.node_id = ? and pj.jid = ?");
			ps.setLong(1, nodeId);
			ps.setString(2, jid);
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				if ("none".equals(affil)) {
					ps = conn.prepareStatement("delete from tig_pubsub_affiliations" + " where node_id = ? and jid_id = ("
							+ " select jid_id from tig_pubsub_jids where jid = ?)");
					ps.setLong(1, nodeId);
					ps.setString(2, jid);
					ps.executeUpdate();
				} else {
					long jidId = tigPubSubEnsureJid(jid);
					ps = conn.prepareStatement(
							"update tig_pubsub_affiliations set affiliation = ?" + " where node_id = ? and jid_id = ?;");
					ps.setString(1, affil);
					ps.setLong(2, nodeId);
					ps.setLong(3, jidId);
					ps.executeUpdate();
				}
			} else {
				if ("none".equals(affil))
					return;

				long jidId = tigPubSubEnsureJid(jid);
				ps = conn.prepareStatement(
						"insert into tig_pubsub_affiliations (node_id, jid_id," + " affiliation) values (?, ?, ?)");
				ps.setLong(1, nodeId);
				ps.setLong(2, jidId);
				ps.setString(3, affil);
				ps.executeUpdate();
			}
		} catch (SQLException e) {
			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubSetNodeConfiguration(Long nodeId, String conf, Long collectionId, ResultSet[] data)
			throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(
					"update tig_pubsub_nodes " + "set configuration = ?, collection_id = ? where node_id = ?");
			ps.setString(1, conf);
			if (collectionId == null) {
				ps.setNull(2, java.sql.Types.BIGINT);
			} else {
				ps.setLong(2, collectionId);
			}
			ps.setLong(3, nodeId);
			ps.executeUpdate();
		} catch (SQLException e) {
			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubSetNodeSubscription(Long nodeId, String jid, String subscr, String subscrId, ResultSet[] data)
			throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			long jidId = tigPubSubEnsureJid(jid);
			PreparedStatement ps = conn.prepareStatement(
					"select 1 from tig_pubsub_subscriptions" + " where node_id = ? and jid_id = ?");
			ps.setLong(1, nodeId);
			ps.setLong(2, jidId);
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				ps = conn.prepareStatement(
						"update tig_pubsub_subscriptions set subscription = ?" + " where node_id = ? and jid_id = ?;");
				ps.setString(1, subscr);
				ps.setLong(2, nodeId);
				ps.setLong(3, jidId);
				ps.executeUpdate();
			} else {
				ps = conn.prepareStatement("insert into tig_pubsub_subscriptions (node_id, jid_id,"
						+ " subscription, subscription_id) values (?, ?, ?, ?)");
				ps.setLong(1, nodeId);
				ps.setLong(2, jidId);
				ps.setString(3, subscr);
				ps.setString(4, subscrId);
				ps.executeUpdate();
			}
		} catch (SQLException e) {
			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}
	
	public static void tigPubSubRemoveService(String serviceJid, ResultSet[] data) throws SQLException {	
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("delete from tig_pubsub_items where node_id in ("
					+ "select n.node_id from tig_pubsub_nodes n"
					+ " inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id"
					+ " where sj.service_jid = ?)");
			ps.setString(1, serviceJid);
			ps.executeUpdate();
			ps = conn.prepareStatement("delete from tig_pubsub_affiliations where node_id in ("
					+ "select n.node_id from tig_pubsub_nodes n"
					+ " inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id"
					+ " where sj.service_jid = ?)");
			ps.setString(1, serviceJid);
			ps.executeUpdate();
			ps = conn.prepareStatement("delete from tig_pubsub_subscriptions where node_id in ("
					+ "select n.node_id from tig_pubsub_nodes n"
					+ " inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id"
					+ " where sj.service_jid = ?)");
			ps.setString(1, serviceJid);
			ps.executeUpdate();
			ps = conn.prepareStatement("delete from tig_pubsub_nodes where node_id in ("
					+ "select n.node_id from tig_pubsub_nodes n"
					+ " inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id"
					+ " where sj.service_jid = ?)");	
			ps.setString(1, serviceJid);
			ps.executeUpdate();

			ps = conn.prepareStatement("delete from tig_pubsub_service_jids where service_jid = ?");	
			ps.setString(1, serviceJid);
			ps.executeUpdate();
			ps = conn.prepareStatement("delete from tig_pubsub_affiliations where jid_id in (select j.jid_id from tig_pubsub_jids j where j.jid = ?)");	
			ps.setString(1, serviceJid);
			ps.executeUpdate();
			ps = conn.prepareStatement("delete from tig_pubsub_subscriptions where jid_id in (select j.jid_id from tig_pubsub_jids j where j.jid = ?)");	
			ps.setString(1, serviceJid);
			ps.executeUpdate();		

		} catch (SQLException e) {
			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}			
	}	
}

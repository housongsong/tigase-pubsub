package tigase.pubsub.repository;

import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.NodeType;
import tigase.xmpp.BareJID;

public class PubSubRepositoryWrapper implements IPubSubRepository {

	private IPubSubRepository repo;

	public PubSubRepositoryWrapper(IPubSubRepository repo) {
		this.repo = repo;
	}

	@Override
	public void addToRootCollection(BareJID serviceJid, String nodeName) throws RepositoryException {
		repo.addToRootCollection(serviceJid, nodeName);
	}

	@Override
	public void createNode(BareJID serviceJid, String nodeName, BareJID ownerJid, AbstractNodeConfig nodeConfig,
			NodeType nodeType, String collection) throws RepositoryException {
		repo.createNode(serviceJid, nodeName, ownerJid, nodeConfig, nodeType, collection);
	}

	@Override
	public void deleteNode(BareJID serviceJid, String nodeName) throws RepositoryException {
		repo.deleteNode(serviceJid, nodeName);
	}

	@Override
	public void destroy() {
		repo.destroy();
	}

	@Override
	public void forgetConfiguration(BareJID serviceJid, String nodeName) throws RepositoryException {
		repo.forgetConfiguration(serviceJid, nodeName);
	}

	@Override
	public String[] getBuddyGroups(BareJID owner, BareJID buddy) throws RepositoryException {
		return repo.getBuddyGroups(owner, buddy);
	}

	@Override
	public String getBuddySubscription(BareJID owner, BareJID buddy) throws RepositoryException {
		return repo.getBuddySubscription(owner, buddy);
	}

	@Override
	public IAffiliations getNodeAffiliations(BareJID serviceJid, String nodeName) throws RepositoryException {
		return repo.getNodeAffiliations(serviceJid, nodeName);
	}

	@Override
	public AbstractNodeConfig getNodeConfig(BareJID serviceJid, String nodeName) throws RepositoryException {
		return repo.getNodeConfig(serviceJid, nodeName);
	}

	@Override
	public IItems getNodeItems(BareJID serviceJid, String nodeName) throws RepositoryException {
		return repo.getNodeItems(serviceJid, nodeName);
	}

	@Override
	public ISubscriptions getNodeSubscriptions(BareJID serviceJid, String nodeName) throws RepositoryException {
		return repo.getNodeSubscriptions(serviceJid, nodeName);
	}

	@Override
	public IPubSubDAO getPubSubDAO() {
		return repo.getPubSubDAO();
	}

	@Override
	public String[] getRootCollection(BareJID serviceJid) throws RepositoryException {
		return repo.getRootCollection(serviceJid);
	}

	@Override
	public BareJID[] getUserRoster(BareJID owner) throws RepositoryException {
		return repo.getUserRoster(owner);
	}

	@Override
	public void init() {
		repo.init();
	}

	@Override
	public void removeFromRootCollection(BareJID serviceJid, String nodeName) throws RepositoryException {
		repo.removeFromRootCollection(serviceJid, nodeName);
	}

	@Override
	public void update(BareJID serviceJid, String nodeName, AbstractNodeConfig nodeConfig) throws RepositoryException {
		repo.update(serviceJid, nodeName, nodeConfig);
	}

	@Override
	public void update(BareJID serviceJid, String nodeName, IAffiliations affiliations) throws RepositoryException {
		repo.update(serviceJid, nodeName, affiliations);
	}

	@Override
	public void update(BareJID serviceJid, String nodeName, ISubscriptions subscriptions) throws RepositoryException {
		repo.update(serviceJid, nodeName, subscriptions);
	}
}
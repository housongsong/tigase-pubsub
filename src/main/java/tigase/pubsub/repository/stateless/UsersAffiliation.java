package tigase.pubsub.repository.stateless;

import tigase.pubsub.Affiliation;
import tigase.util.JIDUtils;

public class UsersAffiliation {

	private Affiliation affiliation;

	private final String jid;

	public UsersAffiliation(final String jid) {
		this.affiliation = Affiliation.none;
		this.jid = jid == null ? null : JIDUtils.getNodeID(jid);
	}

	public UsersAffiliation(final String jid, final Affiliation affiliation) {
		this.affiliation = affiliation == null ? Affiliation.none : affiliation;
		this.jid = jid == null ? null : JIDUtils.getNodeID(jid);
	}

	public Affiliation getAffiliation() {
		return affiliation;
	}

	public String getJid() {
		return jid;
	}

	public void setAffiliation(Affiliation affiliation) {
		this.affiliation = affiliation;
	}

}
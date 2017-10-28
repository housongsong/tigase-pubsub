package tigase.pubsub.cluster;

import java.util.Arrays;
import java.util.Map.Entry;

import tigase.component.adhoc.AdHocCommand;
import tigase.component.adhoc.AdHocCommandException;
import tigase.component.adhoc.AdHocResponse;
import tigase.component.adhoc.AdhHocRequest;
import tigase.form.Field;
import tigase.form.Form;
import tigase.pubsub.PubSubConfig;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.JID;

public class ViewNodeLoadCommand implements AdHocCommand {

	private final PubSubConfig config;

	private final ClusterNodeMap nodeMap;

	public ViewNodeLoadCommand(PubSubConfig config, ClusterNodeMap nodeMap) {
		this.config = config;
		this.nodeMap = nodeMap;
	}

	@Override
	public void execute(AdhHocRequest request, AdHocResponse response) throws AdHocCommandException {
		try {
			if (!config.isAdmin(request.getSender())) {
				throw new AdHocCommandException(Authorization.FORBIDDEN);
			}

			Form form = new Form("result", "Cluster nodes load", "Statistics of cluster nodes");

			for (Entry<String, Integer> entry : this.nodeMap.getClusterNodesLoad().entrySet()) {
				Field field = Field.fieldTextSingle("tigase#node-" + entry.getKey(), entry.getValue().toString(),
						entry.getKey());
				form.addField(field);

			}

			response.getElements().add(form.getElement());
			response.completeSession();

		} catch (AdHocCommandException e) {
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			throw new AdHocCommandException(Authorization.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	@Override
	public String getName() {
		return "View cluster load";
	}

	@Override
	public String getNode() {
		return "cluster-load";
	}

	@Override
	public boolean isAllowedFor(JID jid) {
		return Arrays.asList(config.getAdmins()).contains(jid.toString());
	}

}

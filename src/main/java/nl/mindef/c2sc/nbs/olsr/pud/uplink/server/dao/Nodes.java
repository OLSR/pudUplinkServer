package nl.mindef.c2sc.nbs.olsr.pud.uplink.server.dao;

import java.net.InetAddress;
import java.util.List;

import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.domainmodel.Node;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public interface Nodes {
	/**
	 * Get a node.<br/>
	 * <br/>
	 * Only the node itself is retrieved, no linked objects (non-eager fetching) <br/>
	 * <br/>
	 * 
	 * @param mainIp
	 *            the IP address of the node to fetch
	 * @return a list of all cluster leader nodes
	 */
	public Node getNode(InetAddress mainIp);

	/**
	 * Save a node.
	 * 
	 * @param node
	 *            the node to save
	 * @param newObject
	 *            true when the node is new, false when already exists
	 */
	public void saveNode(Node node, boolean newObject);

	/**
	 * Remove all expired nodes.<br/>
	 * <br/>
	 * An expired node is a node the has a reception time that is at least
	 * 'validityTime*validityTimeMultiplier' seconds in the past.
	 * 
	 * @param validityTimeMultiplier
	 *            the validity time multiplier
	 */
	public void removeExpiredNodes(double validityTimeMultiplier);

	/**
	 * Get a list of all cluster leaders.<br/>
	 * <br/>
	 * Only the cluster leader nodes themselves are retrieved, no linked objects
	 * (non-eager fetching) <br/>
	 * <br/>
	 * 
	 * A cluster leader is:
	 * <ul>
	 * <li>a node that points to itself as a cluster leader</li>
	 * <li>a node that points to another node that does not point to itself as a
	 * cluster leader</li>
	 * </ul>
	 * 
	 * @return a list of all cluster leader nodes
	 */
	public List<Node> getClusterLeaders();

	public void log(Logger logger, Level level);
}
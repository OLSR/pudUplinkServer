package nl.mindef.c2sc.nbs.olsr.pud.uplink.server.uplink;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.dao.RelayServers;
import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.dao.domainmodel.RelayServer;
import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.distributor.Distributor;
import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.handlers.PacketHandler;
import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.logger.DatabaseLogger;
import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.signals.StopHandlerConsumer;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Required;

public class UplinkReceiver extends Thread implements StopHandlerConsumer {
	private Logger logger = Logger.getLogger(this.getClass().getName());

	static private int BUFFERSIZE = 16 * 1024; /* 16KB */

	/** the UDP port to listen on for uplink messages */
	private Integer uplinkUdpPort = null;

	/**
	 * @param uplinkUdpPort
	 *          the uplinkUdpPort to set
	 */
	@Required
	public final void setUplinkUdpPort(int uplinkUdpPort) {
		this.uplinkUdpPort = uplinkUdpPort;
	}

	private PacketHandler packetHandler;

	/**
	 * @param packetHandler
	 *          the packetHandler to set
	 */
	@Required
	public final void setPacketHandler(PacketHandler packetHandler) {
		this.packetHandler = packetHandler;
	}

	private Distributor distributor;

	/**
	 * @param distributor
	 *          the distributor to set
	 */
	@Required
	public final void setDistributor(Distributor distributor) {
		this.distributor = distributor;
	}

	private DatabaseLogger databaseLogger;

	/**
	 * @param databaseLogger
	 *          the databaseLogger to set
	 */
	@Required
	public final void setDatabaseLogger(DatabaseLogger databaseLogger) {
		this.databaseLogger = databaseLogger;
	}

	private RelayServers relayServers;

	/**
	 * @param relayServers
	 *          the relayServers to set
	 */
	@Required
	public final void setRelayServers(RelayServers relayServers) {
		this.relayServers = relayServers;
	}

	private Set<RelayServer> configuredRelayServers = new HashSet<RelayServer>();

	private static final String ipMatcher = "(\\d{1,3}\\.){0,3}\\d{1,3}";
	private static final String portMatcher = "\\d{1,5}";
	private static final String entryMatcher = "\\s*" + ipMatcher + "(:" + portMatcher + ")?\\s*";
	private static final String matcher = "^\\s*" + entryMatcher + "(," + entryMatcher + ")*\\s*$";

	/**
	 * @param relayServers
	 *          the relayServers to set
	 * @throws UnknownHostException
	 *           upon error converting an IP address or host name to an INetAddress
	 */
	@Required
	public final void setConfiguredRelayServers(String relayServers) throws UnknownHostException {
		if ((relayServers == null) || relayServers.trim().isEmpty()) {
			configuredRelayServers.clear();
			return;
		}

		if (!relayServers.matches(matcher)) {
			throw new IllegalArgumentException("Configured relayServers string does not comply to regular expression \""
					+ matcher + "\"");
		}

		String[] splits = relayServers.split("\\s*,\\s*");
		for (String split : splits) {
			String[] fields = split.split(":", 2);

			InetAddress ip = InetAddress.getByName(fields[0].trim());

			RelayServer relayServer = new RelayServer();
			relayServer.setIp(ip);

			if (fields.length == 2) {
				Integer port = Integer.valueOf(fields[1].trim());
				if ((port <= 0) || (port > 65535)) {
					throw new IllegalArgumentException("Configured port " + port + " for IP address " + ip.getHostAddress()
							+ " is outside valid range of [1, 65535]");
				}
				relayServer.setPort(port.intValue());
			}

			this.configuredRelayServers.add(relayServer);
		}
	}

	private void initRelayServers() {
		this.configuredRelayServers.add(relayServers.getMe());

		/* save into database */
		for (RelayServer relayServer : configuredRelayServers) {
			relayServers.addRelayServer(relayServer, true);
		}
	}

	/*
	 * Main
	 */

	private DatagramSocket sock = null;
	private AtomicBoolean run = new AtomicBoolean(true);

	public void init() throws SocketException {
		this.setName(this.getClass().getSimpleName());
		sock = new DatagramSocket(uplinkUdpPort);
		this.start();
	}

	public void destroy() {
		run.set(false);
		synchronized (run) {
			run.notifyAll();
		}
	}

	/**
	 * Run the relay server.
	 * 
	 * @throws SocketException
	 *           when the socket could not be created
	 */
	@Override
	public void run() {
		initRelayServers();

		byte[] receiveBuffer = new byte[BUFFERSIZE];
		DatagramPacket packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);

		while (run.get()) {
			try {
				sock.receive(packet);
				try {
					if (packetHandler.processPacket(packet)) {
						databaseLogger.log(logger, Level.DEBUG);
						distributor.signalUpdate();
					}
				} catch (Throwable e) {
					logger.error(e);
				}
			} catch (Exception e) {
				if (!SocketException.class.equals(e.getClass())) {
					e.printStackTrace();
				}
			}
		}

		sock.close();
	}

	/*
	 * Signal Handling
	 */

	@Override
	public void signalStop() {
		run.set(false);
		if (sock != null) {
			/* this is crude but effective */
			sock.close();
		}
	}
}

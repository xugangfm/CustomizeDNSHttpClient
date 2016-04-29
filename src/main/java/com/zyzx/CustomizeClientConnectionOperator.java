package com.zyzx;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHost;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.conn.HttpInetSocketAddress;
import org.apache.http.conn.OperatedClientConnection;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SchemeSocketFactory;
import org.apache.http.impl.conn.DefaultClientConnectionOperator;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

public class CustomizeClientConnectionOperator extends DefaultClientConnectionOperator {
	private final Log log = LogFactory.getLog(CustomizeClientConnectionOperator.class);
	private final CustomizeNameService ns ;
	
	public CustomizeClientConnectionOperator(SchemeRegistry schemes,CustomizeNameService tns) {
		super(schemes);
		this.ns = tns;
	}
	
	protected InetAddress[] resolveHostname(String host) throws UnknownHostException {
		return this.ns.lookupAllHostAddr(host);
	}
	
	public void openConnection(OperatedClientConnection conn, HttpHost target, InetAddress local, HttpContext context, HttpParams params) throws IOException {
		if (conn == null) {
			throw new IllegalArgumentException("Connection may not be null");
		}
		if (target == null) {
			throw new IllegalArgumentException("Target host may not be null");
		}
		if (params == null) {
			throw new IllegalArgumentException("Parameters may not be null");
		}
		if (conn.isOpen()) {
			throw new IllegalStateException("Connection must not be open");
		}

		Scheme schm = this.schemeRegistry.getScheme(target.getSchemeName());
		SchemeSocketFactory sf = schm.getSchemeSocketFactory();

		InetAddress[] addresses = resolveHostname(target.getHostName());
		int port = schm.resolvePort(target.getPort());
		for (int i = 0; i < addresses.length; ++i) {
			InetAddress address = addresses[i];
			boolean last = i == addresses.length - 1;

			Socket sock = sf.createSocket(params);
			conn.opening(sock, target);

			InetSocketAddress remoteAddress = new HttpInetSocketAddress(target, address, port);
			InetSocketAddress localAddress = null;
			if (local != null) {
				localAddress = new InetSocketAddress(local, 0);
			}
			if (log.isDebugEnabled())
				log.debug("Connecting to host " + remoteAddress +" IP :" + address);
			try {
				Socket connsock = sf.connectSocket(sock, remoteAddress, localAddress, params);
				if (sock != connsock) {
					sock = connsock;
					conn.opening(sock, target);
				}
				prepareSocket(sock, context, params);
				conn.openCompleted(sf.isSecure(sock), params);
				
				ns.moveToFirstInetAddress(target.getHostName(), i,addresses[i]);
				
				return;
			} catch (ConnectException ex) {
				if (last)
					throw new HttpHostConnectException(target, ex);
				
			} catch (ConnectTimeoutException ex) {
				if (last) {
					throw ex;
				}
				
			}
			if (log.isDebugEnabled())
				log.debug("Connect to " + remoteAddress + " timed out. " + "Connection will be retried using another IP address");
		}
	}

}
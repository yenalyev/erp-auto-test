package com.erp.utils;

import com.erp.utils.config.ConfigProvider;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Opens an SSH port-forward tunnel so that a remote PostgreSQL database
 * is accessible via a local port on the developer's machine.
 *
 * Equivalent shell command:
 *   ssh -L {localPort}:{remoteDbHost}:{remoteDbPort} {sshUser}@{sshHost} -p {sshPort} -i {keyFile}
 *
 * Usage:
 *   SshTunnelManager tunnel = new SshTunnelManager();
 *   String jdbcUrl = tunnel.open("jdbc:postgresql://localhost:5432/mydb");
 *   // ... run tests ...
 *   tunnel.close();
 */
@Slf4j
public class SshTunnelManager implements AutoCloseable {

    private static final Pattern JDBC_HOST_PORT = Pattern.compile(
            "jdbc:postgresql://([^:/]+):(\\d+)/(.+)"
    );

    private Session session;

    /**
     * Opens an SSH tunnel and returns a JDBC URL that points to the forwarded local port.
     *
     * The original {@code jdbcUrl} is rewritten so that:
     *   jdbc:postgresql://localhost:5432/mydb  →  jdbc:postgresql://localhost:{sshLocalPort}/mydb
     *
     * @param jdbcUrl original JDBC URL from config (host/port are replaced by tunnel values)
     * @return rewritten JDBC URL targeting the local tunnel port
     */
    public String open(String jdbcUrl) {
        if (session != null && session.isConnected()) {
            log.warn("SSH tunnel is already open — skipping");
            return rewriteUrl(jdbcUrl);
        }

        String sshHost     = ConfigProvider.getSshHost();
        int    sshPort     = ConfigProvider.getSshPort();
        String sshUser     = ConfigProvider.getSshUsername();
        String sshKeyPath  = ConfigProvider.getSshKeyPath();
        String remoteHost  = ConfigProvider.getSshRemoteDbHost();
        int    remotePort  = ConfigProvider.getSshRemoteDbPort();
        int    localPort   = ConfigProvider.getSshLocalPort();

        validateConfig(sshHost, sshUser, sshKeyPath);

        log.info("Opening SSH tunnel: {}@{}:{} -L {}:{}:{}",
                sshUser, sshHost, sshPort, localPort, remoteHost, remotePort);

        try {
            JSch jsch = new JSch();
            jsch.addIdentity(sshKeyPath);

            session = jsch.getSession(sshUser, sshHost, sshPort);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setConfig("PreferredAuthentications", "publickey");
            session.connect(15_000);

            session.setPortForwardingL(localPort, remoteHost, remotePort);
            log.info("SSH tunnel established: localhost:{} -> {}:{}", localPort, remoteHost, remotePort);

        } catch (JSchException e) {
            throw new RuntimeException("Failed to open SSH tunnel: " + e.getMessage(), e);
        }

        return rewriteUrl(jdbcUrl);
    }

    /**
     * Closes the SSH session (and therefore the port-forward).
     * Safe to call multiple times.
     */
    public void close() {
        if (session != null && session.isConnected()) {
            session.disconnect();
            log.info("SSH tunnel closed");
        }
        session = null;
    }

    public boolean isOpen() {
        return session != null && session.isConnected();
    }

    /**
     * Replaces the host:port in the JDBC URL with localhost:{sshLocalPort}.
     * The database name part is preserved unchanged.
     */
    private String rewriteUrl(String jdbcUrl) {
        int localPort = ConfigProvider.getSshLocalPort();

        Matcher m = JDBC_HOST_PORT.matcher(jdbcUrl);
        if (m.matches()) {
            String dbName = m.group(3);
            String rewritten = "jdbc:postgresql://localhost:" + localPort + "/" + dbName;
            log.debug("JDBC URL rewritten: {} -> {}", jdbcUrl, rewritten);
            return rewritten;
        }

        log.warn("Could not parse JDBC URL '{}' — using as-is with local port replacement", jdbcUrl);
        return jdbcUrl;
    }

    private void validateConfig(String sshHost, String sshUser, String sshKeyPath) {
        if (sshHost == null || sshHost.isBlank()) {
            throw new IllegalStateException("ssh.host is not configured in properties");
        }
        if (sshUser == null || sshUser.isBlank()) {
            throw new IllegalStateException("ssh.username is not configured in properties");
        }
        if (sshKeyPath == null || sshKeyPath.isBlank()) {
            throw new IllegalStateException("ssh.key.path is not configured in properties");
        }
    }
}

package org.jabref.logic.remote.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import javafx.util.Pair;

import org.jabref.logic.l10n.Localization;
import org.jabref.logic.remote.Protocol;
import org.jabref.logic.remote.RemoteMessage;
import org.jabref.logic.remote.RemotePreferences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteClient.class);

    // Opening a library can take time, thus 2 minutes is a reasonable timeout.
    private static final int TIMEOUT = 120_000;

    private final int port;

    public RemoteClient(int port) {
        this.port = port;
    }

    public boolean ping() {
        try (Protocol protocol = openNewConnection()) {
            protocol.sendMessage(RemoteMessage.PING);
            Pair<RemoteMessage, Object> response = protocol.receiveMessage();

            if ((response.getKey() == RemoteMessage.PONG) && Protocol.IDENTIFIER.equals(response.getValue())) {
                return true;
            } else {
                String port = String.valueOf(this.port);
                String errorMessage = Localization.lang("Cannot use port %0 for remote operation; another application may be using it. Try specifying another port.", port);
                LOGGER.error(errorMessage);
                return false;
            }
        } catch (IOException e) {
            LOGGER.debug("Could not ping server at port {}", port, e);
            return false;
        }
    }

    /**
     * Attempt to send command line arguments to already running JabRef instance.
     *
     * @param args command line arguments.
     * @return true if successful, false otherwise.
     */
    public boolean sendCommandLineArguments(String[] args) {
        try (Protocol protocol = openNewConnection()) {
            protocol.sendMessage(RemoteMessage.SEND_COMMAND_LINE_ARGUMENTS, args);
            Pair<RemoteMessage, Object> response = protocol.receiveMessage();
            return response.getKey() == RemoteMessage.OK;
        } catch (IOException e) {
            LOGGER.debug("Could not send args {} to the server at port {}", String.join(", ", args), port, e);
            return false;
        }
    }

    /**
     * Attempt to send a focus command to already running JabRef instance.
     *
     * @return true if successful, false otherwise.
     */
    public boolean sendFocus() {
        try (Protocol protocol = openNewConnection()) {
            protocol.sendMessage(RemoteMessage.FOCUS);
            Pair<RemoteMessage, Object> response = protocol.receiveMessage();
            return response.getKey() == RemoteMessage.OK;
        } catch (IOException e) {
            LOGGER.debug("Could not send focus command to the server at port {}", port, e);
            return false;
        }
    }

    private Protocol openNewConnection() throws IOException {
        Socket socket = new Socket();
        socket.setSoTimeout(TIMEOUT);
        socket.connect(new InetSocketAddress(RemotePreferences.getIpAddress(), port), TIMEOUT);
        return new Protocol(socket);
    }
}

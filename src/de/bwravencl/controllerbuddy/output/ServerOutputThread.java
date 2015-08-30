/* Copyright (C) 2015  Matteo Hausner
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package de.bwravencl.controllerbuddy.output;

import java.io.IOException;
import java.io.StringWriter;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JOptionPane;

import de.bwravencl.controllerbuddy.gui.Main;
import de.bwravencl.controllerbuddy.input.Input;
import de.bwravencl.controllerbuddy.input.KeyStroke;

public class ServerOutputThread extends OutputThread {

	public static final int DEFAULT_PORT = 28789;
	public static final int DEFAULT_TIMEOUT = 2000;

	public static final int PROTOCOL_VERSION = 2;
	public static final String PROTOCOL_MESSAGE_DELIMITER = ":";
	public static final String PROTOCOL_MESSAGE_CLIENT_HELLO = "CLIENT_HELLO";
	public static final String PROTOCOL_MESSAGE_SERVER_HELLO = "SERVER_HELLO";
	public static final String PROTOCOL_MESSAGE_UPDATE = "UPDATE";
	public static final String PROTOCOL_MESSAGE_UPDATE_REQUEST_ALIVE = PROTOCOL_MESSAGE_UPDATE + "_ALIVE";
	public static final String PROTOCOL_MESSAGE_CLIENT_ALIVE = "CLIENT_ALIVE";

	private static final int REQUEST_ALIVE_INTERVAL = 100;

	private enum ServerState {
		Listening, Connected
	}

	private int port = DEFAULT_PORT;
	private int timeout = DEFAULT_TIMEOUT;
	private DatagramSocket serverSocket;
	private InetAddress clientIPAddress;

	public ServerOutputThread(Main main, Input input) {
		super(main, input);
	}

	@Override
	public void run() {
		final int clientPort = port + 1;
		ServerState serverState = ServerState.Listening;
		DatagramPacket receivePacket;
		String message;
		long counter = 0;

		try {
			serverSocket = new DatagramSocket(port);
			final byte[] receiveBuf = new byte[1024];

			setListeningStatusbarText();

			while (true) {
				switch (serverState) {
				case Listening:
					counter = 0;
					receivePacket = new DatagramPacket(receiveBuf, receiveBuf.length);
					serverSocket.setSoTimeout(0);
					serverSocket.receive(receivePacket);
					clientIPAddress = receivePacket.getAddress();
					message = new String(receivePacket.getData(), 0, receivePacket.getLength());

					if (message.startsWith(PROTOCOL_MESSAGE_CLIENT_HELLO)) {
						final String[] messageParts = message.split(PROTOCOL_MESSAGE_DELIMITER);

						if (messageParts.length == 4) {
							minAxisValue = Integer.parseInt(messageParts[1]);
							maxAxisValue = Integer.parseInt(messageParts[2]);
							setnButtons(Integer.parseInt(messageParts[3]));

							StringWriter sw = new StringWriter();
							sw.append(PROTOCOL_MESSAGE_SERVER_HELLO);
							sw.append(PROTOCOL_MESSAGE_DELIMITER);
							sw.append(String.valueOf(PROTOCOL_VERSION));
							sw.append(PROTOCOL_MESSAGE_DELIMITER);
							sw.append(String.valueOf(updateRate));

							final byte[] sendBuf = sw.toString().getBytes("ASCII");
							final DatagramPacket sendPacket = new DatagramPacket(sendBuf, sendBuf.length,
									clientIPAddress, clientPort);
							serverSocket.send(sendPacket);

							serverState = ServerState.Connected;
							main.setStatusbarText(
									rb.getString("STATUS_CONNECTED_TO_PART_1") + clientIPAddress.getCanonicalHostName()
											+ rb.getString("STATUS_CONNECTED_TO_PART_2") + clientPort
											+ rb.getString("STATUS_CONNECTED_TO_PART_3") + updateRate
											+ rb.getString("STATUS_CONNECTED_TO_PART_4"));
						}
					}
					break;
				case Connected:
					try {
						Thread.sleep(updateRate);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

					StringWriter sw = new StringWriter();
					boolean doAliveCheck = false;
					if (counter % REQUEST_ALIVE_INTERVAL == 0) {
						sw.append(PROTOCOL_MESSAGE_UPDATE_REQUEST_ALIVE);
						doAliveCheck = true;
					} else
						sw.append(PROTOCOL_MESSAGE_UPDATE);
					sw.append(PROTOCOL_MESSAGE_DELIMITER + counter);

					input.poll();

					for (int v : input.getAxis().values())
						sw.append(PROTOCOL_MESSAGE_DELIMITER + v);

					for (boolean v : input.getButtons())
						sw.append(PROTOCOL_MESSAGE_DELIMITER + v);

					sw.append(PROTOCOL_MESSAGE_DELIMITER + input.getCursorDeltaX() + PROTOCOL_MESSAGE_DELIMITER
							+ input.getCursorDeltaY());
					input.setCursorDeltaX(0);
					input.setCursorDeltaY(0);

					sw.append(PROTOCOL_MESSAGE_DELIMITER + input.getDownMouseButtons().size());
					for (int b : input.getDownMouseButtons())
						sw.append(PROTOCOL_MESSAGE_DELIMITER + b);

					sw.append(PROTOCOL_MESSAGE_DELIMITER + input.getDownUpMouseButtons().size());
					for (int b : input.getDownUpMouseButtons())
						sw.append(PROTOCOL_MESSAGE_DELIMITER + b);
					input.getDownUpMouseButtons().clear();

					sw.append(PROTOCOL_MESSAGE_DELIMITER + input.getDownKeyCodes().size());
					for (int k : input.getDownKeyCodes())
						sw.append(PROTOCOL_MESSAGE_DELIMITER + k);

					sw.append(PROTOCOL_MESSAGE_DELIMITER + input.getDownUpKeyStrokes().size());
					for (KeyStroke ks : input.getDownUpKeyStrokes()) {
						sw.append(PROTOCOL_MESSAGE_DELIMITER + ks.getModifierCodes().length);
						for (int k : ks.getModifierCodes())
							sw.append(PROTOCOL_MESSAGE_DELIMITER + k);

						sw.append(PROTOCOL_MESSAGE_DELIMITER + ks.getKeyCodes().length);
						for (int k : ks.getKeyCodes())
							sw.append(PROTOCOL_MESSAGE_DELIMITER + k);
					}
					input.getDownUpKeyStrokes().clear();

					sw.append(PROTOCOL_MESSAGE_DELIMITER + input.getScrollClicks());
					input.setScrollClicks(0);

					final byte[] sendBuf = sw.toString().getBytes("ASCII");

					final DatagramPacket sendPacket = new DatagramPacket(sendBuf, sendBuf.length, clientIPAddress,
							clientPort);
					serverSocket.send(sendPacket);

					if (doAliveCheck) {
						receivePacket = new DatagramPacket(receiveBuf, receiveBuf.length);
						serverSocket.setSoTimeout(timeout);
						try {
							serverSocket.receive(receivePacket);

							if (clientIPAddress.equals(receivePacket.getAddress())) {
								message = new String(receivePacket.getData(), 0, receivePacket.getLength());

								if (PROTOCOL_MESSAGE_CLIENT_ALIVE.equals(message))
									counter++;
							}
						} catch (SocketTimeoutException e) {
							serverState = ServerState.Listening;

							main.setStatusbarText(rb.getString("STATUS_TIMEOUT"));
							new Timer().schedule(new TimerTask() {
								@Override
								public void run() {
									setListeningStatusbarText();
								}
							}, 5000L);
						}
					} else
						counter++;

					break;
				}
			}
		} catch (BindException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(main.getFrame(),
					rb.getString("COULD_NOT_OPEN_SOCKET_DIALOG_TEXT_PREFIX") + port
							+ rb.getString("COULD_NOT_OPEN_SOCKET_DIALOG_TEXT_SUFFIX"),
					rb.getString("ERROR_DIALOG_TITLE"), JOptionPane.ERROR_MESSAGE);
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(main.getFrame(), rb.getString("GENERAL_INPUT_OUTPUT_ERROR_DIALOG_TEXT"),
					rb.getString("ERROR_DIALOG_TITLE"), JOptionPane.ERROR_MESSAGE);
		} finally {
			closeSocket();
		}
	}

	private void setListeningStatusbarText() {
		main.setStatusbarText(rb.getString("STATUS_LISTENING") + port);
	}

	public void closeSocket() {
		if (serverSocket != null)
			serverSocket.close();

		main.setStatusbarText(rb.getString("STATUS_SOCKET_CLOSED"));
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public int getTimeout() {
		return timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

}
package jycraft.plugin.servers;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import jycraft.plugin.interpreter.PyInterpreter;
import jycraft.plugin.utils.PluginUtils;

import org.java_websocket.WebSocket;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

public class PyWebSocketServer extends WebSocketServer {
	private Object plugin;
	private String password;
	private Map<WebSocket, PyInterpreter> connections;
	private Map<WebSocket, String> buffers;
	private Map<WebSocket, Boolean> authorized;
	
	public PyWebSocketServer (Object caller, int port, String password) {
		super(new InetSocketAddress(port));
		this.plugin = caller;
		this.password = password;
		this.connections = new HashMap<WebSocket, PyInterpreter>();
		this.buffers = new HashMap<WebSocket, String>();
		this.authorized = new HashMap<WebSocket, Boolean>();
	}
	
	public String getPassword() {
		return password;
	}

	public Object getPlugin() {
		return plugin;
	}

	@Override
	public void onOpen(WebSocket ws, ClientHandshake chs) {
		PluginUtils.log(plugin, "New websocket connection");
		PyInterpreter interpreter = new PyInterpreter();
		OutputStream os = new MyOutputStream(ws);
		interpreter.setOut(os);
		interpreter.setErr(os);
		connections.put(ws, interpreter);
		buffers.put(ws, "");
		authorized.put(ws, password == null || "".equals(password));
		ws.send("Login by sending 'login!<PASSWORD>'\n");
	}

	@Override
	public void onClose(WebSocket ws, int arg1, String arg2, boolean arg3) {
		connections.get(ws).close();
		connections.remove(ws);
		buffers.remove(ws);
		authorized.remove(ws);
	}

	@Override
	public void onMessage(WebSocket ws, final String message) {
		boolean auth = authorized.get(ws);
		
		if (message.startsWith("login!")) {
			String p = message.split("!")[1];
			if (!password.equals(p)) {
				ws.send("Incorrect password!\n");
			} else {
				authorized.put(ws, true);
				ws.send("Welcome!\n");
				ws.send(">>> ");
			}
			return;
		}
		
		if (message.equals("exit!")) {
			ws.close(CloseFrame.NORMAL);
			return;
		}
		
		if (!auth) {
			ws.send("Not authorized, login first by sending 'login!<PASSWORD>'\n");
			return;
		}
		
		final PyInterpreter interpreter = connections.get(ws);
		boolean more = false;
		try {
			if (message.contains("\n")) {
				more = PluginUtils.parse(getPlugin(), interpreter, message, true);
			} else {
				buffers.put(ws, buffers.get(ws)+"\n"+message); 
				more = PluginUtils.parse(getPlugin(), interpreter, buffers.get(ws), false);
			}
		} catch (Exception e) {
			ws.send(e.toString()+"\n");
		}
		if (!more) buffers.put(ws, "");
		if (more) ws.send("... ");
		else ws.send(">>> ");
	}

	@Override
	public void onError(WebSocket ws, Exception exc) {

	}

	public class MyOutputStream extends OutputStream {
		WebSocket ws;
		public MyOutputStream(WebSocket ws) {
			this.ws = ws;
		}
		@Override
		public void write(int b) {
			int[] bytes = { b };
			write(bytes, 0, bytes.length);
		}
		public void write(int[] bytes, int offset, int length) {
			String s = new String(bytes, offset, length);
			this.ws.send(s);
		}
	}

}
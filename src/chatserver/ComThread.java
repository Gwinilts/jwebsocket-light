/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package chatserver;

//import com.sun.org.apache.xml.internal.security.utils.Base64;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedList;
import java.util.Random;
import java.util.TreeMap;

/**
 *
 * @author gwion
 */
public class ComThread extends Thread {

    public static final String webSocketMagic = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private enum HS {
        RecieveHeaders,
        ResponseHeaders,
        HSComplete,
        GettingFrameDesc,
        GettingFrameBody,
        GotFrame
    }

    private enum WSOP {
        Ping((byte) 0x9),
        Pong((byte) 0xA),
        Text((byte) 0x1);

        private final byte value;

        WSOP(byte v) {
            value = v;
        }
    }
    private Socket link;
    private InputListener in;
    private volatile boolean running;
    private TreeMap<String, String> headers;
    private BufferedOutputStream out;
    private HS stage;
    private boolean pong;
    private long pingTime;
    private int pingFail;

    private volatile LinkedList<String> msgQueue;

    private volatile LinkedList<String> recQueue;
    private volatile boolean recLock;

    public synchronized boolean getRecLock() {
        return ((recLock) ? (false) : (recLock = true));
    }

    public synchronized void rec(String msg) {
        recQueue.addFirst(msg);
    }

    public synchronized boolean remRecLock() {
        return recLock = false;
    }

    private volatile MsgPool msgPool;

    public String vString(String a) {
        if (a == null) {
            return "";
        }
        return a.toLowerCase();
    }

    public static String sha1(byte[] base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(base);
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public ComThread(Socket l, MsgPool m) throws IOException {
        link = l;
        msgPool = m;

        msgQueue = new LinkedList();
        recQueue = new LinkedList();

        recQueue.addFirst("SACK CONNECT");

        in = new InputListener(l.getInputStream());
        out = new BufferedOutputStream(l.getOutputStream());
        running = true;
        headers = new TreeMap();
        stage = HS.RecieveHeaders;
        pingTime = 0;
        pong = true;
        pingFail = 0;
        start();
    }

    public synchronized void kill() {
        running = false;
        System.out.println("I will soon die...");
    }

    private void ping(OutputStream out) {
        WebSocketFrame snd = new WebSocketFrame();
        snd.setFin(1);
        snd.setMask(0);
        snd.setOpCode(WSOP.Ping.value);
        snd.setPayload(new byte[]{WSOP.Ping.value});
        snd.send(out);
        pong = false;
        pingTime = System.currentTimeMillis();
    }

    private void pong(OutputStream out) {
        WebSocketFrame snd = new WebSocketFrame();
        snd.setFin(1);
        snd.setMask(0);
        snd.setOpCode(WSOP.Pong.value);
        snd.setPayload(new byte[]{WSOP.Pong.value});
        snd.send(out);
        pong = true;
        pingFail = 0;
    }

    private void pong(OutputStream out, byte[] data) {
        WebSocketFrame snd = new WebSocketFrame();
        snd.setFin(1);
        snd.setMask(0);
        snd.setOpCode(WSOP.Pong.value);
        snd.setPayload(data);
        snd.send(out);
    }

    private void text(OutputStream out, String txt) {
        WebSocketFrame snd = new WebSocketFrame();
        snd.setFin(1);
        snd.setMask(0);
        snd.setOpCode(WSOP.Text.value);
        snd.setPayload(txt.getBytes());
        snd.send(out);
    }

    public void run() {
        try {
            String line;
            LinkedList<String> unprocessed = new LinkedList<String>();
            String splitter[];
            String sMsg;
            byte[] msg;
            Random r = new Random();
            WebSocketFrame rec = null;
            WebSocketFrame snd = null;
            int hInt;
            while (running) {
                if (!msgQueue.isEmpty()) {
                    msgPool.getAccess(this);
                    if (msgPool.hasAccess(this)) {
                        try {
                            sMsg = msgQueue.getLast();
                            msgPool.addMsg(sMsg, this);
                            msgQueue.removeLast();
                        } catch (NotYourTurnException e) {
                            // Can't send message right now
                            // No need to worry ,try again later.
                        }
                    }
                }
                if (stage == HS.RecieveHeaders) {
                    while (!in.hasNext("\r\n")) {

                    }
                    line = in.getNext("\r\n");
                    if (line.contains("GET")) {
                        System.out.println("Got the request line!");
                    } else if (line.length() > 0) {
                        headers.put((splitter = line.split(":"))[0].trim().toLowerCase(), splitter[1].trim());
                        //System.out.println("Accepted Header " + splitter[0]);
                    } else {
                        //System.out.println("Headers probably over");
                        stage = HS.ResponseHeaders;
                        in.resetBBB();
                    }
                }
                if (stage == HS.ResponseHeaders) {
                    if (vString(headers.get("upgrade")).contains("websocket")) {
                        if (vString(headers.get("sec-websocket-key")).length() > 0) {
                            if (!vString(headers.get("sec-websocket-version")).contains("13")) {
                                out.write("Sec-WebSocket-Version: 13\r\n".getBytes());
                                out.flush();
                            }
                            out.write("HTTP/1.1 101 Switching Protocols\r\n".getBytes());
                            out.write("Upgrade: websocket\r\n".getBytes());
                            out.write("Connection: Upgrade\r\n".getBytes());
                            out.write("Sec-WebSocket-Protocol: chat.stuffinator.tk\r\n".getBytes());
                            out.write(("Sec-WebSocket-Accept: " + sha1((headers.get("sec-websocket-key") + webSocketMagic).getBytes()) + "\r\n\r\n").getBytes());
                            out.flush();
                            stage = HS.HSComplete;
                            System.out.println("Handshake Complete!");
                            in.unlockBuffer();
                            ping(out);
                        } else {
                            System.out.println("Rejected bad request!");
                            out.write("HTTP/1.1 400 Bad Request\r\n".getBytes());
                            out.flush();
                            in.kill();
                            link.close();
                        }
                    }
                }
                if (stage == HS.HSComplete) {
                    // It's appropriate to start handling frames
                    if (getRecLock()) {
                        if (!recQueue.isEmpty()) {
                            System.out.println("Yay! the queue worked!");
                            text(out, recQueue.removeLast());
                        }
                        remRecLock();
                    }

                    if (System.currentTimeMillis() > pingTime + 1000) {
                        if (!pong) {
                            pingFail++;
                        }
                        // Send a new ping
                        ping(out);
                        // Increment the ping fail
                        pingTime = System.currentTimeMillis();
                    }

                    if (pingFail > 5) {
                        // If 5 pings fail, drop the connection
                        kill();
                        System.out.println("Thread did not respond...");
                    }

                    if (in.has(2)) {
                        //System.out.println("Incoming...");
                        rec = new WebSocketFrame();
                        rec.recieve(in);
                        if (rec.getOp() == WSOP.Ping.value) {
                            pong(out, rec.getPayload());
                        }
                        if (rec.getOp() == WSOP.Pong.value) {
                            pong = true;
                        }
                        if (rec.getOp() == WSOP.Text.value) {
                            //System.out.println("Text: " + new String(rec.getPayload()));
                            msgQueue.addFirst(new String(rec.getPayload()));
                            /*s
                            snd = new WebSocketFrame();
                            snd.setOpCode(0x1);
                            snd.setMask(0);
                            snd.setFin(1);
                            snd.setPayload("Message Recieved!".getBytes());
                            snd.send(out);
                            //System.out.println("Sending message...");
                            */
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Error, dying...");
            e.printStackTrace();
            kill();
        }
    }
}

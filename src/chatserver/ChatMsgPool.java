/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package chatserver;

import java.util.HashMap;
import java.util.Set;
import java.util.TreeMap;

/**
 *
 * @author gwion
 */
public class ChatMsgPool extends MsgPool {

    private TreeMap<String, ComThread> users;
    private HashMap<ComThread, String> userNames;
    private ComThread[] knownUsers;

    public ChatMsgPool() {
        users = new TreeMap();
        userNames = new HashMap();
    }

    private Msg interp(Msg msg) {
        String t = msg.msg();
        if (t.contains("SET NAME")) {
            String name = t.replace("SET NAME", "").trim();
            users.put(name, msg.getSender());
            userNames.put(msg.getSender(), name);
            return new Msg("NAME SET " + name, msg.getSender());
        }
        if (t.contains("POLL USERS")) {
            String list = "";
            for (String user : users.keySet()) {
                list += user + "; ";
            }
            return new Msg(list, msg.getSender());
        }

        if (t.contains("TO USER ")) {
            String getName = t.substring(t.indexOf("TO USER ") + "TO USER ".length(), t.indexOf("SEND")).trim();
            String theMsg = t.substring(t.indexOf("SEND") + "SEND".length()).trim();
            if (users.get(getName) != null) {
                if (userNames.get(msg.getSender()) == null) {
                    return new Msg("SERROR LOG IN", msg.getSender());
                }
                try {
                    response("FROM " + userNames.get(msg.getSender()) + " SEND " + theMsg, users.get(getName), this);
                    return new Msg("Sent msg", msg.getSender());
                } catch (Exception e) {
                    users.put(getName, null);
                    return new Msg("SERROR " + getName + " DEAD", msg.getSender());
                }
            } else {
                return new Msg("SERROR NO " + getName, msg.getSender());
            }
        }
        return new Msg("Not handled...", msg.getSender());

    }

    @Override
    public boolean handleMsg(Msg msg) throws DeadThreadException, NotYourTurnException {
        ComThread t;
        if (msg.isResponse()) {
            t = msg.getTo();
            System.out.println("Handling response");
            if (t.getRecLock()) {
                t.rec(msg.msg());
                msgs.remove(msg);
                t.remRecLock();
                return true;
            } else {
                System.out.println("Response Target was busy");
                return false;
            }
        } else {
            Msg resp = interp(msg);
            try {
                response(resp.msg(), resp.getTo(), this);
                msgs.remove(msg);
                return true;
            } catch (UnSolicitedResponseException e) {
                msgs.remove(msg);
                return false;
            }
        }
    }

    @Override
    public void extra() {
        boolean notify = false;
        Set<ComThread> ts = userNames.keySet();
        if (knownUsers == null) {
            knownUsers = ts.toArray(new ComThread[0]);
            notify = true;
        } else {
            if (ts.size() != knownUsers.length) {
                System.out.println("User list size changed! " + ts.size() + ", " + knownUsers.length);
                knownUsers = ts.toArray(new ComThread[0]);
                notify = true;
            } else {
                for (ComThread t: knownUsers) {
                    if (!ts.contains(t)) notify = true;
                    if (notify) {
                        knownUsers = ts.toArray(new ComThread[0]);
                        break;
                    }
                }
            }
        }
        boolean caughtDeath = false;
        if (notify) {
            String u = "";
            for (ComThread t: ts) {
                if (t.isAlive()) {
                    u += userNames.get(t) + ";";
                } else {
                    caughtDeath = true;
                    users.put(userNames.get(t), null);
                    userNames.put(t, null);
                }
            }
            
            if (caughtDeath) return;
            
            for (ComThread t: ts) {
                try {
                    response("USER POLL " + u, t, this);
                } catch (Exception e) {
                    System.out.println("Something bad happened.");
                }
            }
        }
    }
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package chatserver;

import java.util.LinkedList;

/**
 *
 * @author gwion
 */
public abstract class MsgPool extends Thread {

    /*
    access is a FILO queue that determines who can use the pool at any time
    accessor threads must request access and can have only one request open at a time to prevent slamming.
    when an accessor is the last in the queue, they can do things. Single actions cause the thread to lose access
    The thread is then removed from the queue and can request access once again.
    If there is not much congestion (there are few accessor threads) piling may occur (threads may perform actions and request access imediatly therefore hogging the queue)
    // TODO: Handle Piling
     */
    LinkedList<Thread> access;
    boolean accessLock;

    /*
        msgs needs no lock as the access queue gaurentees that only one thread may use a resource at a time.
     */
    LinkedList<Msg> msgs;

    LinkedList<ComThread> activeComs;

    public MsgPool() {
        access = new LinkedList<Thread>();
        msgs = new LinkedList<Msg>();
        activeComs = new LinkedList();
        accessLock = false;
    }

    /**
     * getAccess attempts to lodge an access request
     *
     * @param t the caller thread
     * @return true, if the request was lodged, false if the queue was busy
     */
    public synchronized final boolean getAccess(Thread t) {
        if (!accessLock) {
            accessLock = true;
            if (access.contains(t)) {
                // This is a problem,
                accessLock = false;
                return true;
            } else {
                // All is well.
                access.addFirst(t);
                accessLock = false;
                return true;
            }
        } else {
            return false;
        }
    }

    /**
     * hasAccess determines whether the caller thread is at the top of the
     * access queue
     *
     * @param t the caller thread
     * @return true if the caller thread is at the top. False if either: the
     * queue was busy OR the caller is not at the top.
     */
    public synchronized final boolean hasAccess(Thread t) {
        // If the last thread in the queue is t
        // This could cause problems if a thread thinks it has a pending request
        if (!accessLock) {
            accessLock = true;
            if (!access.isEmpty()) {
                if (access.getLast().equals(t)) {
                    return !(accessLock = false);
                } else {
                    return (accessLock = false);
                }
            } else {
                return (accessLock = false);
            }
        } else {
            return false;
        }
    }

    /**
     * revoke revokes access to a thread thus removing them from the queue
     */
    private int lastPoolSize;
    
    private synchronized final void revoke() {
        access.removeLast();
        if (access.size() != lastPoolSize) {
            System.out.println("Pool size: " + (lastPoolSize = access.size()));
        }
    }

    /**
     * addMsg attempts to add a message to the msg queue
     *
     * @param msg the message to be added
     * @param t the caller thread
     * @throws NotYourTurnException if the caller thread is not at the top of
     * the access queue
     */
    public synchronized final void addMsg(String msg, ComThread t) throws NotYourTurnException {
        if (access.getLast() == t) {
            msgs.add(new Msg(t, msg));
            activeComs.add(t);
            revoke();
            System.out.println("A massage was added to the queue");
            System.out.println("Msg pool size: " + msgs.size());
        } else {
            throw new NotYourTurnException("Access denied to " + t.getName() + ". Access violation!");
        }
    }

    public abstract boolean handleMsg(Msg msg) throws DeadThreadException, NotYourTurnException;
    public abstract void extra();

    public final void response(String msg, ComThread t, Thread caller) throws NotYourTurnException, UnSolicitedResponseException {
        if (activeComs.contains(t)) {
            if (access.getLast() == caller) {
                System.out.println("A message became a response");
                msgs.add(new Msg(msg, t));
                //revoke();
                System.out.println("Msg pool size: " + msgs.size());
            } else {
                throw new NotYourTurnException("Access denied to " + t.getName() + ". Access violation!");
            }
        } else {
            System.out.println("Unsolicited response...");
            throw new UnSolicitedResponseException();
        }
    }

    /**
     * The run class must not be overridden in any implementation
     */
    @Override
    public final void run() {
        Msg h;
        while (true) {
            // Step one, get access
            if (getAccess(this)) {
                //System.out.println("I have a place in the queue");
                if (hasAccess(this)) {
                    try {
                        if (msgs.size() > 0) {
                            if (handleMsg(msgs.getLast())) {
                                System.out.println("A msg was handled");
                            } 
                        }
                        extra();
                        revoke();
                    } catch (DeadThreadException e) {
                        activeComs.removeFirstOccurrence(e.getDeadThread());
                        revoke();
                    } catch (NotYourTurnException e) {
                        System.out.println("Not my turn");
                        if (access.getLast().equals(this)) {
                            revoke();
                        }
                    }
                } else {
                    //System.out.println("It's not my turn yet.");
                }
            }
        }
    }
}

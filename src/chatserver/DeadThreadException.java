/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package chatserver;

/**
 *
 * @author gwion
 */
public class DeadThreadException extends Exception {

    /**
     * Creates a new instance of <code>DeadThreadException</code> without detail
     * message.
     */
    private ComThread deadThread;
    
    public DeadThreadException(ComThread t) {
        this.deadThread = t;
    }
    
    public ComThread getDeadThread() {
        return deadThread;
    }

    /**
     * Constructs an instance of <code>DeadThreadException</code> with the
     * specified detail message.
     *
     * @param msg the detail message.
     */
    public DeadThreadException(String msg) {
        super(msg);
    }
}

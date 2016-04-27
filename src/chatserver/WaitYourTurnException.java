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
public class WaitYourTurnException extends Exception {

    /**
     * Creates a new instance of <code>WaitYourTurnException</code> without
     * detail message.
     */
    public WaitYourTurnException() {
    }

    /**
     * Constructs an instance of <code>WaitYourTurnException</code> with the
     * specified detail message.
     *
     * @param msg the detail message.
     */
    public WaitYourTurnException(String msg) {
        super(msg);
    }
}

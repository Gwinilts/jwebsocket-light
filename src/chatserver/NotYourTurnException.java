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
public class NotYourTurnException extends Exception {

    /**
     * Creates a new instance of <code>NotYourTurnException</code> without
     * detail message.
     */
    public NotYourTurnException() {
    }

    /**
     * Constructs an instance of <code>NotYourTurnException</code> with the
     * specified detail message.
     *
     * @param msg the detail message.
     */
    public NotYourTurnException(String msg) {
        super(msg);
    }
}

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
public class Msg {
    
    private ComThread sender;
    private String msg;
    private ComThread to;
    private boolean response;
    
    public Msg(ComThread sender, String msg) {
        this.sender = sender;
        this.msg = msg;
        this.to = null;
        response = false;
        System.out.println("Notice created");
    }
    
    public Msg(String msg, ComThread to) {
        this.msg = msg;
        this.to = to;
        response = true;
        System.out.println("Response created");
    }
    
    public ComThread getSender() {
        return sender;
    }
    
    public boolean isResponse() {
        return response;
    }
    
    public ComThread getTo() {
        return to;
    }
    
    public String msg() {
        return msg;
    }
    
}

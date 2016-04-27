/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package chatserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;

/**
 *
 * @author gwion
 */
public class ChatServer {
    
    public static boolean alive = true;
    
    public static void main(String[] args) {
        LinkedList<ComThread> a = new LinkedList();
        ChatMsgPool mainPool = new ChatMsgPool();
        mainPool.start();
        try {
            ServerSocket srv = new ServerSocket(8080);
            Socket newClient;
            while (true) {
                newClient = srv.accept();
                System.out.println("Accepted A Connection!");
                a.addLast(new ComThread(newClient, mainPool));
            }
        } catch (IOException e) {
            
        }
    }
    
}

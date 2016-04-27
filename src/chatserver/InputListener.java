/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package chatserver;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author gwion
 */
public class InputListener extends Thread {
    
    private String sBuffer;
    private ArrayList<byte[]> bbBuffer;
    private int bbBufferSize;
    private volatile boolean bufferLock;
    private InputStream l;
    private volatile boolean running;
    
    private boolean d = false;
    
    public synchronized boolean lockBuffer() {
        if (!bufferLock) {
            return (bufferLock = true);
        } else {
            return false;
        }
    }
    
    public synchronized boolean has(int bytes) {
        if (lockBuffer()) {
            boolean ok = bytes <= bbBufferSize;
            unlockBuffer();
            return ok;
        } else {
            return false;
        }
    }
    
    public synchronized int poll() {
        if (lockBuffer()) {
            return bbBufferSize;
        } else {
            return 0;
        }
    }
    
    public synchronized byte[] get(int size) {
        if (lockBuffer()) {
            if (size <= bbBufferSize) {
                byte[] hBuffer = new byte[bbBufferSize];
                
                int seek = 0;
                
                for (byte[] pBuffer: bbBuffer) {
                    System.arraycopy(pBuffer, 0, hBuffer, seek, pBuffer.length);
                    seek += pBuffer.length;
                }
                
                byte[] rBuffer = new byte[size];
                byte[] nBuffer = new byte[bbBufferSize - size];
                
                System.arraycopy(hBuffer, 0, rBuffer, 0, size);
                System.arraycopy(hBuffer, size, nBuffer, 0, bbBufferSize - size);
                
                bbBuffer.clear();
                bbBuffer.add(nBuffer);
                bbBufferSize = nBuffer.length;
                
                unlockBuffer();
                
                return rBuffer;
            } else {
                unlockBuffer();
                return new byte[0];
            }
        } else {
            return new byte[0];
        }
    }
    
    public synchronized void resetBBB() {
        bbBuffer.clear();
        bbBufferSize = 0;
        d = true;
    } 
    
    public synchronized void unlockBuffer() {
        bufferLock = false;
    }
    
    public InputListener(InputStream l) {
        running = true;
        this.l = l;
        sBuffer = new String();
        bbBuffer = new ArrayList();
        bbBufferSize = 0;
        
        start();
    }
    
    public synchronized boolean hasNext(String pattern) {
        if (lockBuffer()) {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(sBuffer);
            unlockBuffer();
            return m.find();
        } else {
            return false;
        }
    }
    
    public synchronized String getNext(String pattern) {
        if (lockBuffer()) {
            Pattern m = Pattern.compile(pattern);
            Matcher mt = m.matcher(sBuffer);
            mt.find();
            
            int len = mt.end() - mt.start();

            String result = sBuffer.substring(0, mt.start() + len);
            
            sBuffer = sBuffer.replace(result, "");
            result = result.replace(pattern, "");
            unlockBuffer();
            return result;
        } else {
            return null;
        }
    }
    
    public synchronized void kill() {
        running = false;
    }
    
    public void run() {
        byte[] bBuffer = new byte[2048];
        byte[] cBuffer;
        int readSize = 0;
        boolean vb = false;
        while (running) {
            try {
                if (l.available() > 0) {
                    readSize = l.read(bBuffer);
                }
            } catch (IOException e) {
                e.printStackTrace();
                kill();
            }
            if (readSize > 0 && lockBuffer()) {
                
                cBuffer = new byte[readSize];
                
                System.arraycopy(bBuffer, 0, cBuffer, 0, readSize);
                
                sBuffer += new String(cBuffer);
                bbBuffer.add(cBuffer);
                bbBufferSize += cBuffer.length;
                readSize = 0;
                cBuffer = null;
                
                unlockBuffer();
            } else if (readSize > 0) {
                System.out.println(readSize + " b on stream...");
            }
        }
    }
    
}

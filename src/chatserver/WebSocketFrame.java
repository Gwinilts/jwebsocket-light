/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package chatserver;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;

/**
 *
 * @author gwion
 */
public class WebSocketFrame {

    private boolean fin;
    private byte opcode;
    private boolean mask;
    private long pLen;
    private boolean complete;
    private byte[] pl;
    
    public byte getOp() {
        return opcode;
    }
    
    public byte[] getPayload() {
        return pl;
    }

    public WebSocketFrame() {
        fin = false;
        opcode = 0;
        mask = false;
        pLen = 0;
        complete = false;
    }

    public void setFin(int i) {
        fin = i > 0;
    }

    public void setMask(int i) {
        mask = i > 0;
    }

    public void setOpCode(int i) {
        //System.out.println(i);
        opcode = (byte) i;
    }

    public void setPLen(int i) {
        pLen = i;
    }

    public void setPayload(byte[] a) {
        pl = a;
        pLen = pl.length;
    }

    public boolean recieve(InputListener is) {
        //System.out.println("Rec start...");
        byte[] iData = is.get(2);
        int fCount = 0;

        fin = (iData[fCount] >>> 7) > 0;
        opcode = (byte) ((~((iData[fCount] >>> 4) << 4)) & iData[fCount]);

        fCount++;
        String tBuff = "";
        
        mask = (iData[fCount] >>> 7) > 0;
        iData[fCount] = (byte) (((byte) (iData[fCount] << 1)) >>> 1);
        
        for (int i = 7; i > -1; i--) {
            tBuff += ((iData[fCount] >>> i) % 2 == 0) ? "0" : "1";
        }
        //System.out.println(tBuff);
        long spLen = 0;
        byte[] masker = null;

        if (iData[fCount] <= 125) {
            // we're relatively ok
            //System.out.println("PS 7");
            spLen = iData[fCount];
            //System.out.println("pLen: " + spLen);

        } else if (iData[fCount] == 126) {
            // 16 bit unsigned plen
            // read and parse the next 2
            while (!is.has(2)) {

            }
            iData = new byte[0];
            while (iData.length == 0) {
                iData = is.get(2);
            }
            
            //System.out.println("PS 16");

            for (int i = 1; i > -1; i--) {
                spLen |= iData[i] >>> (i * 8);
            }
        } else if (iData[fCount] == 127) {
            // 64 bit unsigned plen
            // read and parse the next 8
            while (!is.has(8)) {

            }
            iData = new byte[0];
            while (iData.length == 0) {
                iData = is.get(8);
            }
            
            //System.out.println("PS 64");

            for (int i = 7; i > -1; i--) {
                spLen |= iData[i] >>> (i * 8);
            }
        }

        // Now deal with the mask
        if (mask) {
            //System.out.println("Has mask");
            while (!is.has(4)) {

            }
            masker = new byte[0];
            while (masker.length == 0) {
                masker = is.get(4);
            }
            //System.out.println("mask done");
        }

        // Now deal with the payload
        //System.out.println("Get payload");
        is.unlockBuffer();
        byte[] payload;
        while (!is.has((int)spLen)) {
            //System.out.println("Want " + spLen + " only have " + is.poll());
        }
        payload = new byte[0];
        while (payload.length == 0) {
            //System.out.println("Want " + spLen + " only have " + is.poll());
            payload = is.get((int)spLen);
        }
        
        //System.out.println("Got payload");
        
        if (mask) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (payload[i] ^ masker[i % 4]);
            }
        }
        
        pl = payload;
        pLen = spLen;

        
        //System.out.println("Rec Stop");
        //System.out.println("OP: " + Integer.toHexString(opcode));
        return true;
    }
    
    public String dbuff(int i, int len) {
        String out = "";
        for (int o = len - 1; o > -1; o--) {
            out += ((i >>> o) % 2 == 0) ? "0" : "1";
        }
        return out;
    }

    public boolean send(OutputStream os) {
        //System.out.println("Begin send...");
        // byte 1, F R R R O O O O
        int fSize = pl.length + 2;
        int fCount = 0;
        if (pl.length >= 125) {
            if (pl.length > Math.pow(2, 16) - 1) {
                fSize += 2;
                //System.out.println("Allocated 2 for 7+ 16bit");
            } else {
                fSize += 8;
                //System.out.println("Allocated 8 for 7+ 64bit");
            }
        }
        if (mask) {
            fSize += 4;
        }
        
        //System.out.println("Allocated: " + fSize);

        byte[] data = new byte[fSize];

        data[fCount] = fin ? (byte) (1 << 7) : (byte) 0;
        data[fCount] |= (byte) opcode;
        
        //System.out.println("op: " + opcode + ", fin: " + fin + ", dbuff: " + dbuff(data[fCount], 8));

        fCount++;

        data[fCount] = mask ? (byte) (1 << 7) : (byte) 0;

        // byte 2, M P P P P P P P
        if (pLen <= 125) { // 1 byte for pLen
            // Just write pLen in 7 bits
            // pLen -> 0 0 0 0 0 0 0 0
            data[fCount] |= (byte) pLen;
            fCount++;
            //System.out.println("Plen: " + pLen + ", mask: " + mask + ", dbuff " + dbuff(data[fCount - 1], 8));
            //System.out.println("Written: " + (fCount));

        } else if (pLen <= (Math.pow(2, 16) - 1)) { // 2 bytes
            // Write 126 then pLen in 16 bits (unsigned)
            data[fCount] |= (byte) 126;
            // pLen -> 0 0 0 0 0 0 0 0 | 0 0 0 0 0 0 0 0

            // To turn 1 1 1 1 1 1 1 1 -> 1 1 1 1 0 0 0 0, use
            // ~((msg[0] >>> 4) << 4))
            data[2] = (byte) ((~(((byte) pLen) >>> 8) << 8) & ((byte) pLen));
            data[3] = (byte) ((~(((byte) pLen) << 8) >>> 8) & ((byte) pLen));

            fCount += 3;

        } else if (pLen <= (Math.pow(2, 64)) - 1) { // 8 bytes
            // Write 127 then pLen in 64 bits (unsigned)

            data[fCount] |= (byte) 127;

            data[9] = (byte) pLen;
            data[8] = (byte) (pLen >>> 8);
            data[7] = (byte) (pLen >>> 16);
            data[6] = (byte) (pLen >>> 24);
            data[5] = (byte) (pLen >>> 32);
            data[4] = (byte) (pLen >>> 40);
            data[3] = (byte) (pLen >>> 48);
            data[2] = (byte) (pLen >>> 56);

            fCount += 9;
        }

        // Payload length is sent!
        // Mask time
        if (mask) {
            Random masker = new Random();
            byte[] bMask = new byte[4];
            masker.nextBytes(bMask);

            for (int i = 0; i < pl.length; i++) {
                pl[i] = (byte) (pl[i] ^ bMask[i % 4]);
            }

            for (int s = 0; s < bMask.length; s++) {
                data[fCount++] = bMask[s];
            }
            
            //System.out.println("Written: " + fCount);
            
        }

        // mask is sent, write the payload
        for (int i = 0; i < pl.length; i++) {
            data[fCount++] = pl[i];
        }

        try {
            os.write(data);
            //System.out.println("PLEN: " + pLen + ", Written: " + fCount);
            os.flush();
            return true;
        } catch (IOException e) {
            //System.out.println("IO Fail!");
            return false;
        }
    }
}

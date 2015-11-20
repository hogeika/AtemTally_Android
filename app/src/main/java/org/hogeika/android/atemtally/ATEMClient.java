package org.hogeika.android.atemtally;

import android.os.AsyncTask;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Created by yuki on 2015/11/11.
 */
public class ATEMClient {
    protected final int CONECT_TIMEOUT = 10000;
    protected final int SO_TIMEOUT = 300;
    protected final int SLEEP = 100;

    protected static final byte CONNECT_HELLO[] = {0x10, 0x14, 0x53, (byte)0xAB, 0x00, 0x00, 0x00, 0x00, 0x00, 0x3A, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
    protected static final byte CONNECT_HELLO_ANSWER[] = {(byte)0x80, 0x0c, 0x53, (byte)0xab, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03, 0x00, 0x00 };

    protected int SWITCHER_PORT = 9910;
    protected String remote_addr_str;
    protected InetAddress remote_addr;
    protected boolean is_connected = false;
    protected int local_port;
    protected DatagramSocket socket;

    /*
    private void send(){
        try{
            String remote = "127.0.0.1";
            int port = 1234;
            InetAddress host = InetAddress.getByName(remote);
            String message = "send by Android " + " \n";  // 送信メッセージ
            DatagramSocket ds = new DatagramSocket();  //DatagramSocket 作成
            byte[] data = message.getBytes();
            DatagramPacket dp = new DatagramPacket(data, data.length, host, port);  //DatagramPacket 作成
            ds.send(dp);
//            tv2.setText("送信完了しました");
        }catch(Exception e) {
            System.err.println("Exception : " + e);
//            tv2.setText("送信失敗しました");
        }
    }
    */

    public static interface Callback {
        public void onConnect();
        public void onConnectTimeout();
        public void onConnectError();
        public void onTallyChange(int pgm, int sby);
        public void onError();
    }

    public static abstract class AbstructCallback implements Callback {
        @Override
        public void onConnect() {

        }

        @Override
        public void onConnectTimeout() {

        }

        @Override
        public void onConnectError() {

        }

        @Override
        public void onError() {

        }

        @Override
        public void onTallyChange(int pgm, int sby) {

        }
    }

    private Set<Callback> callbacks = new HashSet<>();
    public void addCallback(Callback callback){
        callbacks.add(callback);
    }
    public void removeCallback(Callback callback){
        callbacks.remove(callback);
    }

    protected void notifyConnect(){
        for(Callback callback : callbacks){
            callback.onConnect();
        }
    }
    protected void notifyConnectTimeout(){
        for(Callback callback : callbacks){
            callback.onConnectTimeout();
        }
    }
    protected void notifyConnectError(){
        for(Callback callback : callbacks){
            callback.onConnectError();
        }
    }
    protected void notifyTallyChange(int prg, int sby){
        for(Callback callback : callbacks){
            callback.onTallyChange(prg, sby);
        }
    }
    protected void notifyError(){
        for(Callback callback : callbacks){
            callback.onError();
        }
    }
    public boolean isConnected(){
        return is_connected;
    }

    private class ConnectTask extends AsyncTask<Object, Object, Boolean> {
        @Override
        protected Boolean doInBackground(Object[] params) {
            try {
                remote_addr = InetAddress.getByName(remote_addr_str);
            } catch (UnknownHostException e) {
                notifyConnectError();
                return Boolean.FALSE;
            }
            try {
                socket = new DatagramSocket(/* local_port */);
                socket.setSoTimeout(SO_TIMEOUT);
            } catch (SocketException e) {
                return false;
            }

            send(CONNECT_HELLO);

            long start_time = System.currentTimeMillis();
            do {
                try {

                    byte data[] = receive();
                    if (data == null){
                        continue;
                    }
                    if (data.length == 20) {
                        send(CONNECT_HELLO_ANSWER);
                        notifyConnect();
                        return Boolean.TRUE;
                    }
                } catch (SocketTimeoutException e) {
                    // retry
                } catch (IOException e) {
                    e.printStackTrace();
                    notifyConnectError();
                    return Boolean.FALSE;
                }
            } while((System.currentTimeMillis() - start_time) < CONECT_TIMEOUT);
            notifyConnectTimeout();
            return Boolean.FALSE;
        }
        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            connect_task = null; // clean up

        }
    }
    private ConnectTask connect_task = new ConnectTask();

    private class MainLoopTask extends AsyncTask<Object, Object, Boolean> {
        int prg_i = 0;
        int prv_i = 0;
        boolean prg_array[] = new boolean[16];
        boolean prv_array[] = new boolean[16];
        byte ver_m, ver_l;

        @Override
        protected Boolean doInBackground(Object... params) {
            byte session_id[] = new byte[2];
            byte last_remote_packet_id[] = new byte[2];
            boolean has_initialized = false;
            boolean hello_answerd = true;

            while(!isCancelled()){
                try {
                    byte data[] = receive();
                    if (data == null){
                        continue;
                    }
                    if (!hello_answerd) {
                        if (data.length == 20) {
                            System.out.println("Send Hello Answer");
                            send(CONNECT_HELLO_ANSWER);
                            hello_answerd = true;
                        }
                    }

                    int packet_length = word((byte) (data[0] & 0b00000111),data[1]);
                    session_id[0] = data[2];
                    session_id[1] = data[3];
                    last_remote_packet_id[0] = data[10];
                    last_remote_packet_id[1] = data[11];
                    byte command = (byte)(data[0] & 0b11111000);
                    boolean cmd_ack = (command & 0b00001000) != 0;
                    boolean cmd_init = (command & 0b00010000) != 0;

                    if(data.length == packet_length){
                        long last_contact = System.currentTimeMillis();

                        if (!has_initialized && packet_length == 12){
                            has_initialized = true;
//                            System.out.printf("Session ID: %x02:%x02\n", session_id[0], session_id[1] );
                        }
                        if ((packet_length > 12) && !cmd_init)	{
                            parsePacket(data);
                        }
                        if (has_initialized && cmd_ack) {
//                            System.out.printf("ACK, rpID: %x02:%x02\n", last_remote_packet_id[0], last_remote_packet_id[1]);
//                            sendAnswerPacket(session_id, last_remote_packet_id);
                        }
                    }
                } catch (SocketTimeoutException e){
//                    try {
//                        Thread.sleep(SLEEP);
//                    } catch (InterruptedException e2) {
//                    }
                    send(CONNECT_HELLO);
                    System.out.println("Send Hello");
                    hello_answerd = false;
                } catch (IOException e) {
                    e.printStackTrace();
                    notifyError();
                    return Boolean.FALSE;
                }
            }
            return Boolean.TRUE;
        }

        int word(byte hb, byte lb){
            return ((int)hb << 8) + lb;
        }

        boolean ver42()	{
            return (ver_m > 2) || (ver_m>=2 && ver_l>=12);
        }
        void parsePacket(byte data[]){
            int cmd_index = 12;
            while(cmd_index < data.length){
                int index = cmd_index;
                int cmd_length = word(data[index + 0], data[index + 1]);
                String cmd_str = new String(Arrays.copyOfRange(data,index + 4, index + 8));

                if (cmd_length > 8) {
                    index += 8;
                    if (cmd_str.equals("PrgI")) {
                        if (data[index] == 0) {
                            if (!ver42()) {
                                prg_i = data[index + 1];
                            } else {
                                prg_i = word(data[index + 2], data[index + 3]);
                            }
                            System.out.printf("Program Bus : %d\n", prg_i);
                            notifyTallyChange(prg_i, prv_i);
                        }
                    } else if (cmd_str.equals("PrvI")) {
                        if (data[index] == 0) {
                            if (!ver42()) {
                                prv_i = data[index + 1];
                            } else {
                                prv_i = word(data[index + 2], data[index + 3]);
                            }
                            System.out.printf("Preview Bus : %d\n", prv_i);
                            notifyTallyChange(prg_i, prv_i);
                        }
                    } else if (cmd_str.equals("TlIn")) {
                        int count = data[index + 1];
                        if(count > 16) {
                            count = 16;
                        }
                        for (int i = 0; i < count; i++){
                            byte flag = data[2 + i];
                            if ((flag & 0x01) != 0){
                                prg_array[i] = true;
//                                System.out.printf("Program Bus : %d\n", i+1);
//                                notifyTallyChange(i+1, 0);
                            }else{
                                prg_array[i] = false;
                            }
                            if ((flag & 0x02) != 0){
                                prv_array[i] = true;
//                                System.out.printf("Preview Bus : %d\n", i+1);
//                                notifyTallyChange(0, i+1);
                            }else{
                                prv_array[i] = false;
                            }
                        }
                        System.out.println("Tally updated:\n");
                    } else if (cmd_str.equals("_ver")){
                        ver_m = data[index + 1];
                        ver_l = data[index + 3];

                    }
                }
                cmd_index += cmd_length;
            }
        }
    }

    private MainLoopTask main_loop_task = new MainLoopTask();

    public boolean connect(String address_str) {
        if (isConnected()){
            return false;
        }
        remote_addr_str = address_str;
//        local_port = 32763 + new Random().nextInt(32763);
        connect_task.execute();
        return true;
    }

    public void start(){
        main_loop_task.execute();
    }

    public void stop(){
        main_loop_task.cancel(true);
    }

    private void send(byte packet[]){
        try{
            DatagramPacket dp = new DatagramPacket(packet, packet.length, remote_addr, SWITCHER_PORT);  //DatagramPacket 作成
            socket.send(dp);
        }catch(Exception e) {
            System.err.println("Exception : " + e);
        }
    }

    private void sendAnswerPacket(byte[] session_id, byte[] packet_id) {
        byte buf[] = new byte[12];

        buf[0] = (byte) 0b10000000;
        buf[1] = (byte) buf.length;
        buf[2] = session_id[0];
        buf[3] = session_id[1];
        buf[4] = packet_id[0];
        buf[5] = packet_id[1];
        buf[6] = 0;
        buf[7] = 0;
        buf[8] = 0;
        buf[9] = 0x41;
        buf[10] = 0;
        buf[11] = 0;

        send(buf);
        System.out.println("Send Answer packet.");
    }

    private byte[] receive() throws IOException {
        byte buf[] = new byte[40960];
        DatagramPacket packet= new DatagramPacket(buf,buf.length);
        socket.receive(packet);//受信 & wait
//        SocketAddress  sockAddress = packet.getSocketAddress(); //送信元情報取得
        // TODO 送信元チェック
        SocketAddress addr = packet.getSocketAddress();
        if (addr instanceof InetSocketAddress){
            InetAddress from_addr = ((InetSocketAddress) addr).getAddress();
            if (!remote_addr.equals(from_addr)){
                return null;
            }
        }
        byte result[] = Arrays.copyOfRange(packet.getData(), packet.getOffset(), packet.getOffset() + packet.getLength());

        return result;
    }
}

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
import java.util.Set;

/**
 * Created by yuki on 2015/11/11.
 */
public class ATEMClient {
    protected final int CONNECT_TIMEOUT = 10000;
    protected final int CONTACT_TIMEOUT = 5000;
    protected final int SO_TIMEOUT = 300;
    protected final int SLEEP = 100;

    protected static final byte HEADER_BIT_ACK_REQUEST = 0x01;
    protected static final byte HEADER_BIT_HELLO_PACKET = 0x02;
    protected static final byte HEADER_BIT_RESEND = 0x04;
    protected static final byte HEADER_BIT_REQUEST_NEXT_AFTER = 0x08;
    protected static final byte HEADER_BIT_ACK = 0x10;

    protected int SWITCHER_PORT = 9910;
    protected String remote_addr_str;
    protected InetAddress remote_addr;
    protected int local_port;
    protected DatagramSocket socket;

    protected long connect_start_time = 0;
    protected long last_contact = 0;
    protected boolean init_payload_sent = false;
    protected boolean has_initialized = false;
    protected boolean is_connected = false;
    protected byte session_id[] = new byte[]{0x53, (byte)0xab};
    protected byte last_remote_packet_id[] = new byte[]{0,0};
    protected boolean[] missed_init_package = new boolean[24];

    protected int prg_i = 0;
    protected int prv_i = 0;
    protected boolean prg_array[] = new boolean[16];
    protected boolean prv_array[] = new boolean[16];
    protected byte ver_m, ver_l;

    /*
    private void sendPacket(){
        try{
            String remote = "127.0.0.1";
            int port = 1234;
            InetAddress host = InetAddress.getByName(remote);
            String message = "sendPacket by Android " + " \n";  // 送信メッセージ
            DatagramSocket ds = new DatagramSocket();  //DatagramSocket 作成
            byte[] data = message.getBytes();
            DatagramPacket dp = new DatagramPacket(data, data.length, host, port);  //DatagramPacket 作成
            ds.sendPacket(dp);
//            tv2.setText("送信完了しました");
        }catch(Exception e) {
            System.err.println("Exception : " + e);
//            tv2.setText("送信失敗しました");
        }
    }
    */

    public static interface Callback {
        public void onStart();
        public void onConnect();
        public void onConnectTimeout();
        public void onConnectError();
        public void onTallyChange(int pgm, int sby);
        public void onError();
        public void onConnectionLoss();
        public void onStop();
    }

    public static abstract class AbstructCallback implements Callback {
        @Override
        public void onStart(){

        }

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

        @Override
        public void onConnectionLoss() {

        }

        @Override
        public void onStop(){

        }
    }

    private Set<Callback> callbacks = new HashSet<>();

    public void addCallback(Callback callback){
        callbacks.add(callback);
    }
    public void removeCallback(Callback callback){
        callbacks.remove(callback);
    }

    protected void notifyStart(){
        for(Callback callback : callbacks){
            callback.onStart();
        }
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
    protected void notifyConnectionLoss(){
        for(Callback callback : callbacks){
            callback.onConnectionLoss();
        }
    }
    protected void notifyError(){
        for(Callback callback : callbacks){
            callback.onError();
        }
    }
    protected void notifyStop(){
        for(Callback callback : callbacks){
            callback.onStop();
        }
    }
    public boolean isConnected(){
        return is_connected;
    }

    public boolean isRunning(){
        return main_loop_task != null;
    }

    private class MainLoopTask extends AsyncTask<Object, Object, Boolean> {
        /**
         * Runs on the UI thread before {@link #doInBackground}.
         *
         * @see #onPostExecute
         * @see #doInBackground
         */
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            notifyStart();
        }

        @Override
        protected Boolean doInBackground(Object... params) {
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

            boolean waiting_for_incoming = false;
            sendHello();
//            long last_contact = System.currentTimeMillis();

            while(!isCancelled()){
                try {
                    byte data[] = receive();
                    if (data == null){
                        continue;
                    }
                    int packet_length = word((byte) (data[0] & 0b00000111),data[1]);
                    byte header_bits = (byte) (data[0]>>3);
                    session_id[0] = data[2];
                    session_id[1] = data[3];
                    last_remote_packet_id[0] = data[10];
                    last_remote_packet_id[1] = data[11];
                    int id = word(last_remote_packet_id[0], last_remote_packet_id[1]);
                    if (id < 0){
                        System.out.printf("id = %d, %02x %02x\n", id, last_remote_packet_id[0], last_remote_packet_id[1]);
                    }
                    if (0 <= id && id < missed_init_package.length){
                        missed_init_package[id] = false;
                    }

                    byte command = (byte)(data[0] & 0b11111000);
                    boolean cmd_ack = (command & 0b00001000) != 0;
                    boolean cmd_init = (command & 0b00010000) != 0;

                    if(data.length == packet_length){
                        last_contact = System.currentTimeMillis();
                        waiting_for_incoming = false;

                        if ((header_bits & HEADER_BIT_HELLO_PACKET) > 0) {
                            System.out.println("Send Hello Answer");
                            sendHelloAnswer();
                            is_connected = true;
                            notifyConnect();
                        }


                        if (!init_payload_sent && packet_length == 12){
                            init_payload_sent = true;
                            for (int i = id; i < missed_init_package.length; i++){
                                missed_init_package[i] = false;
                            }
//                            _initPayloadSentAtPacketId = _lastRemotePacketID;
//                            System.out.printf("Session ID: %x02:%x02\n", session_id[0], session_id[1] );
                        }
                        if (init_payload_sent && (((header_bits & HEADER_BIT_ACK_REQUEST) > 0) && (has_initialized || ((header_bits & HEADER_BIT_RESEND) == 0)))) {
//                            System.out.printf("ACK, rpID: %x02:%x02\n", last_remote_packet_id[0], last_remote_packet_id[1]);
                            sendAck(last_remote_packet_id);
                        }
                        if ((packet_length > 12) && !((header_bits & HEADER_BIT_HELLO_PACKET)>0))	{
                            parsePacket(data);
                        }
                    }

                } catch (SocketTimeoutException e){
//                    try {
//                        Thread.sleep(SLEEP);
//                    } catch (InterruptedException e2) {
//                    }
//                    sendPacket(CONNECT_HELLO);
//                    System.out.println("Send Hello");
//                    hello_answerd = false;
                    if(!has_initialized && init_payload_sent && !waiting_for_incoming){
                        for(int id = 1; id < missed_init_package.length; id++){
                            if (missed_init_package[id]){
                                sendRequestNextAfter(id - 1);
                                waiting_for_incoming = true;
                                break;
                            }
                        }
                        if (!waiting_for_incoming){
                            has_initialized = true;
                        }
                    }
                    if (!is_connected && (System.currentTimeMillis() - last_contact) > CONNECT_TIMEOUT){
                        notifyConnectTimeout();
                        return Boolean.FALSE;
                    }
                    if ((System.currentTimeMillis() - last_contact) > CONTACT_TIMEOUT){
                        notifyConnectionLoss();
                        sendHello();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    notifyError();
                    return Boolean.FALSE;
                }
            }
            notifyStop();
            return Boolean.TRUE;
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

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
        }
    }

    private MainLoopTask main_loop_task = null;

    public void start(String remote_addr_str){
        this.remote_addr_str = remote_addr_str;
        if (main_loop_task != null){
            return;
        }
        main_loop_task = new MainLoopTask();
        main_loop_task.execute();
    }

    public void stop(){
        main_loop_task.cancel(true);
        main_loop_task = null;
    }

    private byte[] initPacket(byte header, int length){
        if (length>255){
            new IllegalArgumentException("Not implemented yet.");
        }
        byte packet[] = new byte[length];
        Arrays.fill(packet, (byte)0);
        packet[0] = (byte) (header << 3);
        packet[1] = (byte) packet.length;
        packet[2] = session_id[0];
        packet[3] = session_id[1];
        return packet;
    }
    private void sendPacket(byte packet[]){
        byte answer_packet[] = Arrays.copyOf(packet, packet.length);
        answer_packet[2] = session_id[0];
        answer_packet[3] = session_id[1];
        try{
            DatagramPacket dp = new DatagramPacket(packet, packet.length, remote_addr, SWITCHER_PORT);  //DatagramPacket 作成
            socket.send(dp);
        }catch(Exception e) {
            System.err.println("Exception : " + e);
        }
    }

//    protected static final byte CONNECT_HELLO[] = {0x10, 0x14, 0x53, (byte)0xAB, 0x00, 0x00, 0x00, 0x00, 0x00, 0x3A, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
    private void sendHello(){
        connect_start_time = System.currentTimeMillis();
        last_contact = System.currentTimeMillis();
        init_payload_sent = false;
        has_initialized = false;
        is_connected = false;
        session_id = new byte[]{0x53, (byte)0xab};
        last_remote_packet_id = new byte[]{0,0};
        Arrays.fill(missed_init_package,true);

        byte packet[] = initPacket(HEADER_BIT_HELLO_PACKET, 20);
        packet[9] = 0x3a;
        packet[12] = 0x01;
        sendPacket(packet);
//        sendPacket(CONNECT_HELLO);
    }

//    protected static final byte CONNECT_HELLO_ANSWER[] = {(byte)0x80, 0x0c, 0x53, (byte)0xab, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03, 0x00, 0x00 };
    private void sendHelloAnswer() {
        byte packet[] = initPacket(HEADER_BIT_ACK, 12);
        packet[9] = 0x03;
        sendPacket(packet);
    }

    private void sendAck(byte packet_id[]) {
        byte packet[] = initPacket(HEADER_BIT_ACK, 12);
        packet[4] = packet_id[0];
        packet[5] = packet_id[1];
        sendPacket(packet);

        /*
        byte buf[] = new byte[12];
        Arrays.fill(buf, (byte)0);

        buf[0] = (byte) 0b10000000;
        buf[1] = (byte) buf.length;
//        buf[2] = session_id[0]; // fill in sendPacket()
//        buf[3] = session_id[1];
        buf[4] = packet_id[0];
        buf[5] = packet_id[1];
//        buf[6] = 0;
//        buf[7] = 0;
//        buf[8] = 0;
//        buf[9] = 0; // 0x41?
//        buf[10] = 0;
//        buf[11] = 0;

        sendPacket(buf);
        */
//        System.out.println("Send Answer packet.");
    }

    private void sendRequestNextAfter(int id){
        byte packet[] = initPacket(HEADER_BIT_REQUEST_NEXT_AFTER, 12);
        packet[6] = (byte)(id / 256);
        packet[7] = (byte)(id % 256);
        packet[8] = 0x01;
        sendPacket(packet);

        /*
        byte buf[] = new byte[12];
        Arrays.fill(buf, (byte)0);

        buf[0] = (byte) 0b01000000;
        buf[1] = (byte) buf.length;
//        buf[2] = session_id[0]; // fill in sendAnwser()
//        buf[3] = session_id[1];
//        buf[4] = 0;
//        buf[5] = 0;
        buf[6] = (byte)(id / 256);
        buf[7] = (byte)(id % 256);
        buf[8] = 0x01;
//        buf[9] = 0;
//        buf[10] = 0;
//        buf[11] = 0;

        sendPacket(buf);
        */
        System.out.println("Send RequestNextAfter.");
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

    int word(byte hb, byte lb){
        return ((int)hb << 8) + (lb & 0xff);
    }

    boolean ver42()	{
        return (ver_m > 2) || (ver_m>=2 && ver_l>=12);
    }
}


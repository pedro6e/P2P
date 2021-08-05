package napster;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.*;

public class Mensagem {
	public String     message;
	public String     response;
	public int        destPort; //server ou outro peer
	public static int servPort = 10098;
	
	public Mensagem(String message) {this.message=message;}
	
	public Mensagem(String action, String params) {
		this.message  = action +", "+params;
	}
	
	public String sendReceiveUDP(DatagramSocket sender) throws Exception{
		InetAddress IPAddress = InetAddress.getByName("localhost");
		//send request
		byte[]       sendData = new byte[1024];
		sendData = message.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData,
        											   sendData.length,
        											   IPAddress,
        											   servPort);
        sender.send(sendPacket);
        
        //receive response
        byte[] receiveData = new byte[1024];
		DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
		sender.receive(receivePacket);
		response = new String(receivePacket.getData());						
		return response;
	}
	
	public String sendReceiveTCP(Socket sender) throws Exception{
		//send request
		OutputStream                os= sender.getOutputStream();
		DataOutputStream serverWriter = new DataOutputStream(os);
		serverWriter.writeBytes(message+"\n");
		
		//receive response
		InputStreamReader isrServer = new InputStreamReader(sender.getInputStream());
		BufferedReader serverReader = new BufferedReader(isrServer);
		response = serverReader.readLine();
		return response;
	}
}

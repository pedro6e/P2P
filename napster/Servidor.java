package napster;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.net.*;

public class Servidor {
	public static int servPort = 10098;
	public static ConcurrentHashMap <Integer,String>       peerFiles = new ConcurrentHashMap<>();  //1000: [teste.txt, teste3.txt]
	public static ConcurrentHashMap <String,List<Integer>> filesPeers= new ConcurrentHashMap<>(); //teste.txt: [9875, 9976, 1000]
	public static DatagramSocket                 serverSocket;
	public static DatagramSocket 		         aliveSocket;
	
	public static void main(String[] args) {
		try {			
			aliveSocket  = new DatagramSocket(); //inicia thread alive 
			ServerAlive aliveThread = new ServerAlive();
			aliveThread.start();
			//System.out.println("iniciando thread de escuta udp do server");
			serverSocket = new DatagramSocket(servPort);
			while(true) {
				try {
					//receive
					byte[] receiveData = new byte[1024];
					DatagramPacket receivedPacket = new DatagramPacket(receiveData, receiveData.length);
					serverSocket.receive(receivedPacket);		//block until packet is sent by client
					String peerRequest1 = new String(receivedPacket.getData(), 0, receivedPacket.getLength());
					String response="";
					InetAddress IPAddress = InetAddress.getByName("localhost");
					String aux[] = peerRequest1.split(", ");
					int port     = receivedPacket.getPort(); //serverSocket.getPort();  
					String peerRequest2  = aux[0];
					String param;
					int portPeer=-1;

					switch(peerRequest2) {
						case "JOIN":   //adiciona aos hashs
							param    = aux[1]; //param=peerFiles
							portPeer = Integer.parseInt(aux[2]);
							response = reqJoin(portPeer, param);
							System.out.println("Peer ["+IPAddress.toString()+"]:["+portPeer+"] adicionado com arquivos ["+param+"].");
							break;
						case "SEARCH": //consulta
							param = aux[1]; //param=file
							response = reqSearch(param);
							System.out.println("Peer ["+IPAddress.toString()+"]:["+port+"] solicitou arquivo ["+param+"].");
							break;
						case "DOWNLOAD": 
							response = "Server não aceita essa requisição";
							break;
						case "UPDATE": //atualiza os hashs
							param = aux[1]; //param=newPeerFiles
							portPeer = Integer.parseInt(aux[2]);
							response = reqUpdate(portPeer,param); 
							break;
						case "LEAVE": //retira dos hashs
							portPeer = Integer.parseInt(aux[1]);
							response = reqLeave(portPeer); 
							break;
					}
					byte[] sendData = new byte[response.length()]; //resposta ao peer
					sendData = response.getBytes();
					DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, port);
					serverSocket.send(sendPacket);
				}catch(Exception e) {e.printStackTrace();}
			}
		}catch(Exception e) {e.printStackTrace();}
	}
	
	public static class ServerAlive extends Thread{
		public void run() {			
			while(true) { //loop para executar uma rodada de alive  
				try {
					String sentence = "ALIVE";
					byte[] sendData = new byte[sentence.length()];
					sendData = sentence.getBytes();
					InetAddress IPAddress = InetAddress.getByName("localhost");
					
					//loop sobre os peers, faz alive request
					for(Map.Entry<Integer,String> entry : peerFiles.entrySet()) {
						int port = entry.getKey();
						//System.out.println("Sending ALIVE to port "+port);
						DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, port);
						aliveSocket.send(sendPacket);
						try {
							aliveSocket.setSoTimeout(3000);
					        byte[] buf        = new byte[1000];
					        DatagramPacket dp = new DatagramPacket(buf, buf.length);
					        try {
					        	aliveSocket.receive(dp);
								String response = new String(dp.getData(), 0, dp.getLength());
								//System.out.println("Received ALIVE_OK from "+port);
								if (response=="ALIVE_OK") {} //do nothing
							}catch(SocketTimeoutException e) {
								System.out.println("Peer ["+IPAddress.toString()+"]:["+port+"] morto. Eliminando seus arquivos ["+ peerFiles.get(port) +"]");
								String ignore = reqLeave(port); 
							}				
						}catch(Exception e) {e.printStackTrace();}	
					}
					Thread.sleep(30000);					
				}catch(Exception e) {e.printStackTrace();}
			}
		}
	}
	
	public static String reqJoin(int port, String files) {
		peerFiles.put(port, files);
		String filesSplit[] = files.split("; ");
		List<Integer> portList;
		for(int i=0;i<filesSplit.length;i++) {
			String arquivo = filesSplit[i]; //arquivo:[port]
			if(! filesPeers.containsKey(arquivo)) {
		        Integer vetorNormal[] = {port};
		        portList = Arrays.asList(vetorNormal);
				filesPeers.put(arquivo, portList);
			}else {
				List<Integer> portList2= new ArrayList<>();
				portList2.addAll(filesPeers.get(arquivo)); 		
				portList2.add(port);
				filesPeers.put(arquivo, portList2);
			}
		}return "JOIN_OK";
	}
	
	public static String reqSearch(String file) {
		if (filesPeers.containsKey(file)){
			return filesPeers.get(file).toString().trim();
		}else {
			return "[]"; //filesPeers.toString()+ 
		}
	}
	
	public static String reqUpdate(int port, String files) {
		String ignore = reqLeave(port);
		ignore        = reqJoin(port,files);
		return "UPDATE_OK";
	}
	
	public static String reqLeave(int port) {
		peerFiles.remove(port);
		for(Map.Entry<String, List<Integer>> entry : filesPeers.entrySet()) {
			if(entry.getValue().contains(port)) {
				List<Integer> auxil = new ArrayList<>();
				auxil.addAll( entry.getValue());
				auxil.remove(auxil.indexOf(port));
				filesPeers.replace(entry.getKey(), auxil);			
			}
			if(entry.getValue().isEmpty())
				filesPeers.remove(entry.getKey());
		}
		return "LEAVE_OK";
	}
}